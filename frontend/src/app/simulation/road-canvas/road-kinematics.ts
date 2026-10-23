import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { LANE_CENTRE_METRES } from './road-projection';

/**
 * The few continuously changing numbers the driving view draws, lifted out of a snapshot.
 *
 * Snapshots arrive about every 100 ms. Drawing them as they come makes the road scroll in
 * visible steps, so the view blends from the last drawn values to the newest snapshot over one
 * tick (never beyond it, never extrapolating). The newest snapshot is always the target, so what
 * is drawn converges on backend state; nothing here is fed back to the simulation.
 */
export interface Kinematics {
  /** Ego longitudinal position along the road, metres. */
  egoPos: number;
  /** Ego lateral position, metres from the centre line. */
  egoLateral: number;
  /** Ego heading, degrees; negative turns left, positive right. */
  heading: number;
  /** World position and (for pedestrians) lateral position per object id. */
  objects: Record<string, { world: number; lateral: number | null }>;
}

export const EMPTY_KINEMATICS: Kinematics = { egoPos: 0, egoLateral: LANE_CENTRE_METRES.RIGHT, heading: 0, objects: {} };

/** A jump larger than this (a reset, a scenario load, a lap) is shown at once, not slid across. */
const MAX_BLEND_JUMP_METRES = 60;

export function kinematicsOf(snapshot: ApiSimulationSnapshot | null): Kinematics {
  if (!snapshot?.egoVehicle) {
    return EMPTY_KINEMATICS;
  }
  const ego = snapshot.egoVehicle;
  const egoPos = Number.isFinite(ego.positionMeters) ? ego.positionMeters : 0;
  const egoLateral = Number.isFinite(ego.lateralPositionMeters)
    ? ego.lateralPositionMeters
    : (LANE_CENTRE_METRES[ego.lane] ?? 0);
  const objects: Kinematics['objects'] = {};
  for (const item of snapshot.worldObjects ?? []) {
    objects[item.id] = {
      world: item.worldPositionMeters != null && Number.isFinite(item.worldPositionMeters)
        ? item.worldPositionMeters
        : egoPos + item.positionMeters,
      lateral: item.lateralPositionMeters != null && Number.isFinite(item.lateralPositionMeters)
        ? item.lateralPositionMeters
        : null
    };
  }
  return { egoPos, egoLateral, heading: Number.isFinite(ego.headingDegrees) ? (ego.headingDegrees as number) : 0, objects };
}

const lerp = (a: number, b: number, k: number) => a + (b - a) * k;

/** True when sliding from one kinematic state to the other is a continuation of the same motion. */
export function isContinuation(from: Kinematics, to: Kinematics): boolean {
  return Math.abs(to.egoPos - from.egoPos) <= MAX_BLEND_JUMP_METRES;
}

/** Point between two states; k = 0 is `from`, k = 1 is exactly `to`. */
export function blendKinematics(from: Kinematics, to: Kinematics, k: number): Kinematics {
  if (k >= 1) {
    return to;
  }
  const objects: Kinematics['objects'] = {};
  for (const id of Object.keys(to.objects)) {
    const target = to.objects[id];
    const source = from.objects[id];
    if (!source || Math.abs(target.world - source.world) > MAX_BLEND_JUMP_METRES) {
      objects[id] = target;
      continue;
    }
    objects[id] = {
      world: lerp(source.world, target.world, k),
      lateral: target.lateral !== null && source.lateral !== null ? lerp(source.lateral, target.lateral, k) : target.lateral
    };
  }
  return {
    egoPos: lerp(from.egoPos, to.egoPos, k),
    egoLateral: lerp(from.egoLateral, to.egoLateral, k),
    heading: lerp(from.heading, to.heading, k),
    objects
  };
}
