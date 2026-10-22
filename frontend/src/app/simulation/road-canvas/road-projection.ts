/**
 * Visual projection layer of the driving view.
 *
 * The simulation (backend) stays the single source of truth: it reports, for every object, a
 * longitudinal position along the road and a lane (or a lateral offset). This module only maps
 * those existing numbers onto the screen with a pinhole chase camera; nothing here feeds back
 * into the simulation, and lanes are never derived from pixels.
 *
 *   world state                        camera space                     screen
 *   lateral X (m, 0 = centre line) --> X - camera.x                -->  x = cx + focal * (X - camX) / z
 *   ahead d (m, 0 = ego)           --> z = d + camera.back          -->  y = horizonY + focal * (eye - Y) / z
 *   height Y (m above the road)    --> (eye - Y)                    -->  scale = focal / z  (px per metre)
 *
 * The camera sits `back` metres behind the ego and `eye` metres above the road, looking straight
 * ahead. The horizon (vanishing point) is the line y = horizonY at x = cx. Because every point
 * goes through the same pinhole equation, straight lanes stay straight, converge on the
 * vanishing point and objects shrink with distance, whatever the viewport size.
 *
 * The camera is derived from the viewport so that the road keeps the same share of the width on
 * very wide (short) and on 16:9 canvases and the ego car keeps its proportions.
 */
import { LaneName } from '../../core/models/api.models';

/** Lane centre offsets in metres, mirroring the backend Lane.centreMeters() contract. */
export const LANE_CENTRE_METRES: Record<LaneName, number> = {
  LEFT: -3.5,
  CENTER: 0,
  RIGHT: 3.5
};

export const LANE_WIDTH_METRES = 3.5;
/** Half the width of the three asphalt lanes. */
export const ASPHALT_HALF_METRES = 5.25;
/** Half the width of asphalt plus hard shoulder. */
export const SHOULDER_HALF_METRES = 7.25;
/** Centre of the lane-dividing lines (between LEFT/CENTER and CENTER/RIGHT). */
export const DIVIDER_METRES = LANE_WIDTH_METRES / 2;

/** Distance behind the ego the chase camera sits at. */
export const CAMERA_BACK_METRES = 16;
/** The camera never needs to be lower / higher than this, whatever the canvas shape. */
export const CAMERA_MIN_EYE_METRES = 3;
export const CAMERA_MAX_EYE_METRES = 8.5;
/** Share of the lateral ego offset the camera follows, so a lane change slides the whole scene. */
export const CAMERA_FOLLOW = 0.5;
/** Share of the canvas width the road (asphalt plus shoulders) fills at the ego's depth. */
const ROAD_FILL_AT_EGO = 0.6;
/** Where the horizon and the ego's contact point sit, as a fraction of the canvas height. */
const HORIZON_FRACTION = 0.2;
const EGO_BASE_FRACTION = 0.72;
/** Nothing closer than this camera depth (metres) is drawn: it is below the canvas anyway. */
const NEAR_CLIP_METRES = 3;
/** Furthest distance ahead of the ego the view draws. */
export const VIEW_AHEAD_METRES = 220;

export interface Viewport {
  width: number;
  height: number;
}

export interface Camera {
  width: number;
  height: number;
  /** Screen x of the vanishing point (the horizontal centre). */
  cx: number;
  /** Screen y of the horizon. */
  horizonY: number;
  /** Screen y where the ego touches the road. */
  egoBaseY: number;
  /** Metres between the camera and the ego along the road. */
  back: number;
  /** Camera height above the road, in metres. */
  eye: number;
  /** Focal length in pixels: a point z metres away is drawn focal / z pixels per metre. */
  focal: number;
  /** Lateral position of the camera, in metres from the centre line. */
  x: number;
  /** Closest `ahead` value that is still in front of the near clip plane. */
  nearAhead: number;
}

export interface ScreenPoint {
  x: number;
  y: number;
  /** Camera-space depth in metres; larger is farther. */
  z: number;
}

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

/** Builds the chase camera for a canvas and the ego's lateral position. */
export function createCamera(view: Viewport, egoLateralMeters: number): Camera {
  const width = Math.max(1, view.width);
  const height = Math.max(1, view.height);
  const horizonY = height * HORIZON_FRACTION;
  const egoBaseY = height * EGO_BASE_FRACTION;
  const roadWidth = 2 * SHOULDER_HALF_METRES;
  // Road pixel width at the ego depth = (egoBaseY - horizonY) * roadWidth / eye.
  const eye = clamp(((egoBaseY - horizonY) * roadWidth) / (ROAD_FILL_AT_EGO * width),
    CAMERA_MIN_EYE_METRES, CAMERA_MAX_EYE_METRES);
  const focal = ((egoBaseY - horizonY) * CAMERA_BACK_METRES) / eye;
  return {
    width,
    height,
    cx: width / 2,
    horizonY,
    egoBaseY,
    back: CAMERA_BACK_METRES,
    eye,
    focal,
    x: (Number.isFinite(egoLateralMeters) ? egoLateralMeters : 0) * CAMERA_FOLLOW,
    nearAhead: NEAR_CLIP_METRES - CAMERA_BACK_METRES
  };
}

/** Projects a world point; null when it is closer than the near clip plane. */
export function project(camera: Camera, lateral: number, heightMetres: number, ahead: number): ScreenPoint | null {
  const z = ahead + camera.back;
  if (z < NEAR_CLIP_METRES) {
    return null;
  }
  const scale = camera.focal / z;
  return {
    x: camera.cx + (lateral - camera.x) * scale,
    y: camera.horizonY + (camera.eye - heightMetres) * scale,
    z
  };
}

/** Pixels per metre at `ahead` metres from the ego (0 behind the near clip plane). */
export function pixelsPerMetre(camera: Camera, ahead: number): number {
  const z = ahead + camera.back;
  return z < NEAR_CLIP_METRES ? 0 : camera.focal / z;
}

/** True when something `ahead` metres from the ego can be in front of the camera. */
export function isInFront(camera: Camera, ahead: number): boolean {
  return ahead >= camera.nearAhead;
}

/** A point on the road surface, at lateral `x` metres and `ahead` metres from the ego. */
export function ground(camera: Camera, lateral: number, ahead: number): ScreenPoint | null {
  return project(camera, lateral, 0, ahead);
}

export interface GroundPoint {
  lateral: number;
  ahead: number;
}

/**
 * Projects a polygon lying on the road. Points closer than the near clip plane are pulled onto
 * it, which is exact for the lane-aligned quads and fans drawn here (the clipped edge stays on
 * the same projected line).
 */
export function groundPolygon(camera: Camera, points: GroundPoint[]): ScreenPoint[] | null {
  const projected: ScreenPoint[] = [];
  for (const p of points) {
    const point = ground(camera, p.lateral, Math.max(p.ahead, camera.nearAhead));
    if (!point) {
      return null;
    }
    projected.push(point);
  }
  return projected;
}

/** Lane-aligned rectangle on the road between two distances ahead, clipped to the visible span. */
export function groundQuad(camera: Camera, lateral0: number, lateral1: number, ahead0: number, ahead1: number): ScreenPoint[] | null {
  const near = Math.max(ahead0, camera.nearAhead);
  const far = Math.min(ahead1, VIEW_AHEAD_METRES);
  if (far <= near) {
    return null;
  }
  return groundPolygon(camera, [
    { lateral: lateral0, ahead: near },
    { lateral: lateral1, ahead: near },
    { lateral: lateral1, ahead: far },
    { lateral: lateral0, ahead: far }
  ]);
}

/** SVG path data ("M x y L ... Z") for a list of screen points. */
export function pathOf(points: ScreenPoint[], close = true): string {
  let d = '';
  for (let i = 0; i < points.length; i++) {
    d += `${i === 0 ? 'M' : 'L'}${points[i].x.toFixed(1)} ${points[i].y.toFixed(1)} `;
  }
  return close ? d + 'Z' : d.trimEnd();
}

/**
 * Affine approximation of the projection around a road point, as an SVG `matrix(...)` string.
 * Local x is lateral metres, local -y is metres ahead, so artwork drawn upright in a metre-based
 * coordinate system lies flat on the road with the right foreshortening (speed-limit paint).
 */
export function groundMatrix(camera: Camera, lateral: number, ahead: number): string | null {
  const origin = ground(camera, lateral, ahead);
  if (!origin) {
    return null;
  }
  const z = origin.z;
  const a = camera.focal / z;
  const c = ((lateral - camera.x) * camera.focal) / (z * z);
  const d = (camera.eye * camera.focal) / (z * z);
  return `matrix(${a.toFixed(3)} 0 ${c.toFixed(3)} ${d.toFixed(3)} ${origin.x.toFixed(1)} ${origin.y.toFixed(1)})`;
}

export interface SpriteFrame {
  /** SVG transform that draws metre-sized artwork standing on the road at the point. */
  transform: string;
  /** Screen position of the contact point with the road. */
  x: number;
  y: number;
  /** Pixels per metre actually used (never below `minPxPerMetre`). */
  scale: number;
  /** Camera depth, used to sort drawing order. */
  z: number;
}

/**
 * Frame for an upright billboard (sign, pedestrian, light head) standing on the road. The scale
 * follows the perspective but is floored at `minPxPerMetre` so far-away infrastructure stays
 * legible; the position itself is never altered.
 */
export function spriteFrame(camera: Camera, lateral: number, ahead: number, minPxPerMetre = 0): SpriteFrame | null {
  const point = ground(camera, lateral, ahead);
  if (!point) {
    return null;
  }
  const scale = Math.max(camera.focal / point.z, minPxPerMetre);
  return {
    transform: `translate(${point.x.toFixed(1)} ${point.y.toFixed(1)}) scale(${scale.toFixed(3)})`,
    x: point.x,
    y: point.y,
    scale,
    z: point.z
  };
}
