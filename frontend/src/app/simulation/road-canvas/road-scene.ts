/**
 * View-model builders for the driving view. Pure functions: backend state in (through the
 * kinematics, which hold the numbers being drawn), screen geometry out. Lane, object type and
 * detection always come from the snapshot; the camera in road-projection.ts only decides where
 * on the screen they land.
 */
import { ApiRoadElement, ApiSimulationSnapshot, LaneName } from '../../core/models/api.models';
import { Kinematics } from './road-kinematics';
import {
  ASPHALT_HALF_METRES, Camera, DIVIDER_METRES, groundMatrix, groundPolygon, groundQuad,
  isInFront, LANE_CENTRE_METRES, LANE_WIDTH_METRES, pathOf, project,
  SHOULDER_HALF_METRES, spriteFrame, VIEW_AHEAD_METRES
} from './road-projection';
import {
  box, CarPose, extrude, FaceView, faceOf, rectZ, discZ, Decal, RenderedCar,
  renderCar, renderSolid, shadowPoints, Solid, TRAFFIC_CAR, translate
} from './road-solids';
import { EGO_CAR } from './road-ev-model';

/** Category drives which asset is rendered for a world object. */
export type RoadObjectKind =
  | 'speedLimit'
  | 'stopSign'
  | 'trafficLight'
  | 'barrier'
  | 'pothole'
  | 'obstacle'
  | 'vehicle'
  | 'pedestrian'
  | 'crosswalk'
  | 'radar';

/** What the template draws for an item (one logical object can need several). */
export type RoadShape =
  | 'ego' | 'vehicle' | 'pedestrian' | 'pothole' | 'obstacle' | 'barrier'
  | 'stopSign' | 'stopLine' | 'speedMark' | 'speedSign' | 'gantry' | 'crosswalk' | 'radar';

/** Distance and detection tag drawn above an object, in screen pixels. */
export interface TagView {
  x: number;
  /** Bottom of the tag, just above the object. */
  y: number;
  badge: 'DETECTED' | 'RADAR' | null;
  /** "35 m", "PASSED" or empty. */
  text: string;
  passed: boolean;
  /** Approximate pixel widths so the plates fit their text. */
  badgeWidth: number;
  textWidth: number;
}

export interface LampView {
  cls: string;
  d: string;
}

export interface GantryHead {
  lane: LaneName;
  /** The head in the lane the ego drives in carries the tag and the main test id. */
  main: boolean;
  faces: FaceView[];
  /** Glow around the lit lamp. */
  glow: { x: number; y: number; r: number; cls: string } | null;
  tag: TagView | null;
}

export interface RoadItemView {
  id: string;
  kind: RoadObjectKind;
  shape: RoadShape;
  /** Which copy of a road-wide element this is (`main` carries the tag). */
  role: 'main' | 'copy' | 'decor';
  lane: LaneName | 'ALL';
  state: string;
  /** Digits painted in a sign. */
  label: string;
  ariaLabel: string;
  relativeMeters: number;
  worldPositionMeters: number;
  /** Metres ahead of the ego at the drawn instant. */
  ahead: number;
  /** Lateral metres from the centre line at the drawn instant. */
  lateral: number;
  /** Camera depth of the ground contact point; larger is farther away. */
  depth: number;
  opacity: number;
  detectedByCamera: boolean;
  detectedByRadar: boolean;
  passed: boolean;
  tag: TagView | null;
  halo: string | null;

  // payloads, by shape
  car?: RenderedCar;
  shadow?: string;
  tone?: number;
  faces?: FaceView[];
  transform?: string;
  rings?: string[];
  d?: string;
  open?: boolean;
  swing?: number;
  heads?: GantryHead[];
  beacon?: { x: number; y: number; r: number } | null;
  /** Ego only. */
  lamps?: { x: number; y: number; r: number } | null;
}

export interface SensorEdge {
  d: string;
  x: number;
  y: number;
  text: string;
  anchor: 'start' | 'end';
  ahead: number;
}

export interface SensorView {
  cameraD: string;
  cameraLostD: string | null;
  radarD: string;
  blindLeft: { d: string; occupied: boolean } | null;
  blindRight: { d: string; occupied: boolean } | null;
  cameraEdge: SensorEdge | null;
  cameraNominalEdge: SensorEdge | null;
  radarEdge: SensorEdge | null;
}

export interface RoadSurfaceView {
  asphalt: string;
  shoulderLeft: string;
  shoulderRight: string;
  edgeLeft: string;
  edgeRight: string;
  hills: string;
  hillsFar: string;
}

export interface DistanceTick {
  d: string;
  x: number;
  y: number;
  text: string;
}

export interface TreeView {
  id: number;
  transform: string;
  opacity: number;
}

export interface MarkingsView {
  bands: string;
  dividerLeft: { d: string; continuous: boolean };
  dividerRight: { d: string; continuous: boolean };
  arrows: string;
  postsLeft: string;
  postsRight: string;
  ticks: DistanceTick[];
  trees: TreeView[];
  laneTags: { lane: LaneName; x: number; y: number }[];
}

// ===========================================================================================
// Road surface and static scenery
// ===========================================================================================

/** The road itself runs on to the horizon (the haze hides its end), unlike the paint on it. */
const ROAD_END_METRES = 4000;

export function buildRoadSurface(camera: Camera): RoadSurfaceView {
  const quad = (a: number, b: number) => {
    const points = groundPolygon(camera, [
      { lateral: a, ahead: camera.nearAhead }, { lateral: b, ahead: camera.nearAhead },
      { lateral: b, ahead: ROAD_END_METRES }, { lateral: a, ahead: ROAD_END_METRES }
    ]);
    return points ? pathOf(points) : '';
  };
  const edge = ASPHALT_HALF_METRES - 0.3;
  return {
    asphalt: quad(-ASPHALT_HALF_METRES, ASPHALT_HALF_METRES),
    shoulderLeft: quad(-SHOULDER_HALF_METRES, -ASPHALT_HALF_METRES),
    shoulderRight: quad(ASPHALT_HALF_METRES, SHOULDER_HALF_METRES),
    edgeLeft: quad(-edge - 0.2, -edge),
    edgeRight: quad(edge, edge + 0.2),
    hills: hillPath(camera, 0.9, 11, 0.035, 3),
    hillsFar: hillPath(camera, 0.6, 6, 0.05, 1.1)
  };
}

/** Low, deterministic ridge line along the horizon; heights are fractions of the canvas height. */
function hillPath(camera: Camera, amplitude: number, waves: number, heightFraction: number, phase: number): string {
  const steps = 48;
  const base = camera.horizonY + 1;
  let d = `M0 ${base.toFixed(1)} `;
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const x = t * camera.width;
    const h = (Math.sin(t * waves + phase) * 0.5 + Math.sin(t * waves * 2.3 + phase * 1.7) * 0.3 + 0.8) * amplitude * heightFraction * camera.height;
    d += `L${x.toFixed(1)} ${(base - h).toFixed(1)} `;
  }
  return d + `L${camera.width.toFixed(1)} ${base.toFixed(1)} Z`;
}

const DASH_PERIOD_METRES = 16;
const DASH_LENGTH_METRES = 8;
const BAND_PERIOD_METRES = 12;
const POST_SPACING_METRES = 20;
const TREE_SPACING_METRES = 26;
const TICK_SPACING_METRES = 40;
const ARROW_SPACING_METRES = 48;
const TICK_LABEL_RANGE_METRES = 160;

/** Deterministic pseudo-random number in [0, 1) from an integer, for scenery placement. */
function hash01(n: number): number {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
}

/** World-anchored multiples of `spacing` between two distances ahead of the ego. */
function worldMultiples(egoPos: number, spacing: number, fromAhead: number, toAhead: number, offset = 0): number[] {
  const out: number[] = [];
  const first = Math.ceil((egoPos + fromAhead - offset) / spacing);
  const last = Math.floor((egoPos + toAhead - offset) / spacing);
  for (let i = first; i <= last; i++) {
    out.push(i);
  }
  return out;
}

export function buildMarkings(camera: Camera, egoPos: number, leftMarking: string, rightMarking: string): MarkingsView {
  const near = camera.nearAhead;
  const bandPaths: string[] = [];
  for (const i of worldMultiples(egoPos, BAND_PERIOD_METRES, near - BAND_PERIOD_METRES / 2, VIEW_AHEAD_METRES)) {
    const s = i * BAND_PERIOD_METRES - egoPos;
    const quad = groundQuad(camera, -ASPHALT_HALF_METRES, ASPHALT_HALF_METRES, s, s + BAND_PERIOD_METRES / 2);
    if (quad) {
      bandPaths.push(pathOf(quad));
    }
  }

  const divider = (lateral: number, marking: string) => {
    const continuous = marking === 'CONTINUOUS';
    if (continuous) {
      const quad = groundQuad(camera, lateral - 0.1, lateral + 0.1, near, VIEW_AHEAD_METRES);
      return { d: quad ? pathOf(quad) : '', continuous };
    }
    const paths: string[] = [];
    const firstAhead = near - DASH_LENGTH_METRES;
    for (const i of worldMultiples(egoPos, DASH_PERIOD_METRES, firstAhead, VIEW_AHEAD_METRES)) {
      const s = i * DASH_PERIOD_METRES - egoPos;
      const quad = groundQuad(camera, lateral - 0.08, lateral + 0.08, s, s + DASH_LENGTH_METRES);
      if (quad) {
        paths.push(pathOf(quad));
      }
    }
    return { d: paths.join(' '), continuous };
  };

  // Painted lane-direction arrows, one per lane every 48 m.
  const arrowPaths: string[] = [];
  for (const i of worldMultiples(egoPos, ARROW_SPACING_METRES, near, 150, ARROW_SPACING_METRES / 2)) {
    const s = i * ARROW_SPACING_METRES + ARROW_SPACING_METRES / 2 - egoPos;
    for (const lateral of Object.values(LANE_CENTRE_METRES)) {
      const arrow = groundPolygon(camera, [
        { lateral: lateral - 0.14, ahead: s }, { lateral: lateral + 0.14, ahead: s },
        { lateral: lateral + 0.14, ahead: s + 1.9 }, { lateral: lateral + 0.5, ahead: s + 1.9 },
        { lateral, ahead: s + 3.2 },
        { lateral: lateral - 0.5, ahead: s + 1.9 }, { lateral: lateral - 0.14, ahead: s + 1.9 }
      ]);
      if (arrow && s > near) {
        arrowPaths.push(pathOf(arrow));
      }
    }
  }

  const posts = (lateral: number) => {
    const paths: string[] = [];
    for (const i of worldMultiples(egoPos, POST_SPACING_METRES, near + 1, VIEW_AHEAD_METRES)) {
      const s = i * POST_SPACING_METRES - egoPos;
      const foot = project(camera, lateral, 0, s);
      const top = project(camera, lateral, 0.95, s);
      if (foot && top) {
        const half = Math.max(0.5, (camera.focal / foot.z) * 0.05);
        paths.push(`M${(foot.x - half).toFixed(1)} ${foot.y.toFixed(1)}L${(foot.x + half).toFixed(1)} ${foot.y.toFixed(1)}`
          + `L${(top.x + half).toFixed(1)} ${top.y.toFixed(1)}L${(top.x - half).toFixed(1)} ${top.y.toFixed(1)}Z`);
      }
    }
    return paths.join(' ');
  };

  const ticks: DistanceTick[] = [];
  for (const i of worldMultiples(egoPos, TICK_SPACING_METRES, near + 2, TICK_LABEL_RANGE_METRES)) {
    const s = i * TICK_SPACING_METRES - egoPos;
    const a = project(camera, -ASPHALT_HALF_METRES, 0, s);
    const b = project(camera, ASPHALT_HALF_METRES, 0, s);
    const label = project(camera, -SHOULDER_HALF_METRES - 0.2, 0, s);
    if (a && b && label) {
      ticks.push({
        d: `M${a.x.toFixed(1)} ${a.y.toFixed(1)}L${b.x.toFixed(1)} ${b.y.toFixed(1)}`,
        x: label.x,
        y: label.y - 2,
        text: `${i * TICK_SPACING_METRES} m`
      });
    }
  }

  const trees: TreeView[] = [];
  for (const i of worldMultiples(egoPos, TREE_SPACING_METRES, near + 4, VIEW_AHEAD_METRES)) {
    for (const side of [-1, 1]) {
      const n = i * 2 + (side > 0 ? 1 : 0);
      if (hash01(n) < 0.35) {
        continue;
      }
      const lateral = side * (SHOULDER_HALF_METRES + 3 + hash01(n + 17) * 10);
      const ahead = i * TREE_SPACING_METRES + hash01(n + 3) * 12 - egoPos;
      const frame = spriteFrame(camera, lateral, ahead);
      if (frame && ahead <= VIEW_AHEAD_METRES) {
        const scale = 0.7 + hash01(n + 9) * 0.7;
        trees.push({
          id: n,
          transform: `${frame.transform} scale(${scale.toFixed(2)})`,
          opacity: Math.max(0.25, 1 - ahead / (VIEW_AHEAD_METRES * 1.1))
        });
      }
    }
  }

  // Far trees first, so near ones are drawn over them.
  trees.reverse();

  const laneTags = (Object.keys(LANE_CENTRE_METRES) as LaneName[]).map((lane) => {
    const p = project(camera, LANE_CENTRE_METRES[lane], 0, 18);
    return { lane, x: p?.x ?? 0, y: p?.y ?? 0 };
  });

  return {
    bands: bandPaths.join(' '),
    dividerLeft: divider(-DIVIDER_METRES, leftMarking),
    dividerRight: divider(DIVIDER_METRES, rightMarking),
    arrows: arrowPaths.join(' '),
    postsLeft: posts(-SHOULDER_HALF_METRES + 0.3),
    postsRight: posts(SHOULDER_HALF_METRES - 0.3),
    ticks,
    trees,
    laneTags
  };
}

// ===========================================================================================
// Sensors
// ===========================================================================================

/** Distance from the ego centre to its nose. */
const EGO_NOSE_METRES = 2.3;
/** The camera cone has widened to all lanes by this distance. */
const CAMERA_SPREAD_METRES = 40;
const RADAR_HALF_WIDTH_METRES = 1.4;
const BLIND_PAD_HALF_WIDTH_METRES = 1.25;
const MIN_PAD_WIDTH_METRES = 0.3;
const LABEL_VERGE_PX = 44;

export interface SensorInput {
  camera: Camera;
  egoLateral: number;
  egoPos: number;
  lane: LaneName;
  ranges: { cameraRangeMeters: number; radarRangeMeters: number; blindSpotRangeMeters: number };
  effectiveCameraRange: number;
  cameraLabel: string;
  snapshot: ApiSimulationSnapshot | null;
}

export function buildSensors(input: SensorInput): SensorView {
  const { camera, egoLateral, ranges } = input;
  const pathFor = (points: ReturnType<typeof groundPolygon>) => (points ? pathOf(points) : '');
  const nose = EGO_NOSE_METRES;
  const clampAhead = (a: number) => Math.min(VIEW_AHEAD_METRES, a);

  const cameraEdge = Math.max(nose, clampAhead(input.effectiveCameraRange));
  const spread = Math.min(cameraEdge, CAMERA_SPREAD_METRES);
  const cameraD = pathFor(groundPolygon(camera, [
    { lateral: egoLateral - 0.9, ahead: nose },
    { lateral: -ASPHALT_HALF_METRES, ahead: spread },
    { lateral: -ASPHALT_HALF_METRES, ahead: cameraEdge },
    { lateral: ASPHALT_HALF_METRES, ahead: cameraEdge },
    { lateral: ASPHALT_HALF_METRES, ahead: spread },
    { lateral: egoLateral + 0.9, ahead: nose }
  ]));

  const nominalEdge = clampAhead(ranges.cameraRangeMeters);
  const degraded = input.effectiveCameraRange < ranges.cameraRangeMeters - 0.5;
  const lost = degraded && nominalEdge > cameraEdge + 0.5
    ? groundQuad(camera, -ASPHALT_HALF_METRES, ASPHALT_HALF_METRES, cameraEdge, nominalEdge)
    : null;

  const radarEdge = Math.max(nose, clampAhead(ranges.radarRangeMeters));
  const radarD = pathFor(groundQuad(camera, egoLateral - RADAR_HALF_WIDTH_METRES, egoLateral + RADAR_HALF_WIDTH_METRES, nose, radarEdge));

  // The backend blind-spot zone is symmetric (|distance| <= blindSpotRangeMeters), so the pad is too.
  const snapshot = input.snapshot;
  const blind = (side: 'LEFT' | 'RIGHT') => {
    const telemetry = side === 'LEFT' ? snapshot?.sensors?.blindSpotLeft : snapshot?.sensors?.blindSpotRight;
    const noLane = telemetry ? telemetry.status === 'NO LANE' : (side === 'LEFT' ? input.lane === 'LEFT' : input.lane === 'RIGHT');
    if (noLane) {
      return null;
    }
    // The telemetry can report CLEAR on the road-edge side (before START, or mid lane change
    // while ego.lane is still the source lane), so the pad is also kept on the asphalt.
    const centre = side === 'LEFT' ? egoLateral - LANE_WIDTH_METRES : egoLateral + LANE_WIDTH_METRES;
    const x0 = Math.max(-ASPHALT_HALF_METRES, centre - BLIND_PAD_HALF_WIDTH_METRES);
    const x1 = Math.min(ASPHALT_HALF_METRES, centre + BLIND_PAD_HALF_WIDTH_METRES);
    if (x1 - x0 < MIN_PAD_WIDTH_METRES) {
      return null;
    }
    const quad = groundQuad(camera, x0, x1, -ranges.blindSpotRangeMeters, ranges.blindSpotRangeMeters);
    return quad ? { d: pathOf(quad), occupied: telemetry?.occupied ?? false } : null;
  };

  const edge = (ahead: number, text: string, anchor: 'start' | 'end'): SensorEdge | null => {
    if (ahead > VIEW_AHEAD_METRES || ahead <= 0) {
      return null;
    }
    const a = project(camera, -ASPHALT_HALF_METRES, 0, ahead);
    const b = project(camera, ASPHALT_HALF_METRES, 0, ahead);
    const label = project(camera, anchor === 'end' ? -SHOULDER_HALF_METRES - 0.6 : SHOULDER_HALF_METRES + 0.6, 0, ahead);
    if (!a || !b || !label) {
      return null;
    }
    return {
      d: `M${a.x.toFixed(1)} ${a.y.toFixed(1)}L${b.x.toFixed(1)} ${b.y.toFixed(1)}`,
      // Pushed out onto the verge in screen space, so the text never sits on the lanes.
      x: label.x + (anchor === 'end' ? -LABEL_VERGE_PX : LABEL_VERGE_PX),
      y: label.y - 2,
      text,
      anchor,
      ahead
    };
  };

  return {
    cameraD,
    cameraLostD: lost ? pathOf(lost) : null,
    radarD,
    blindLeft: blind('LEFT'),
    blindRight: blind('RIGHT'),
    cameraEdge: edge(input.effectiveCameraRange, input.cameraLabel, 'end'),
    cameraNominalEdge: degraded ? edge(ranges.cameraRangeMeters, '', 'end') : null,
    radarEdge: edge(ranges.radarRangeMeters,
      `FRONT RADAR FOV (${Math.round(ranges.radarRangeMeters)} m)`, 'start')
  };
}

// ===========================================================================================
// World objects
// ===========================================================================================

/** Where a crossing pedestrian starts and ends, in metres from the centre line (Pedestrian.java). */
const SIDEWALK_METRES = 5.5;
/**
 * A crosswalk is painted at the start of its 5 m map block (RoadMap, SimulationConfig), the
 * pedestrian anywhere in that block: within this distance it is drawn on the crosswalk.
 */
const CROSSWALK_SNAP_METRES = 5;
const CROSSWALK_DEPTH_METRES = 3;

/** Smallest billboard scale (px per metre), so signs and people stay legible far away. */
const MIN_SPRITE_PX_PER_METRE = 7;

export function kindOf(item: ApiRoadElement): RoadObjectKind {
  const type = item.type;
  if (type.includes('SpeedLimit')) return 'speedLimit';
  if (type.includes('StopSign')) return 'stopSign';
  if (type.includes('TrafficLight')) return 'trafficLight';
  if (type.includes('Barrier')) return 'barrier';
  if (type.includes('Pothole')) return 'pothole';
  if (type.includes('Obstacle')) return 'obstacle';
  if (type.includes('Crosswalk')) return 'crosswalk';
  if (type.includes('Pedestrian')) return 'pedestrian';
  // Roadside speed-enforcement radar: fixed infrastructure, never one of the vehicles.
  if (type.includes('Radar')) return 'radar';
  if (type.includes('Vehicle')) return 'vehicle';
  return 'obstacle';
}

/** Reads the backend-reported state token (RED / GREEN / open=true / ...). */
export function stateOf(item: ApiRoadElement): string {
  const details = item.details ?? '';
  if (item.type.includes('TrafficLight')) {
    return details.match(/state=(\w+)/)?.[1] ?? '';
  }
  if (item.type.includes('Barrier')) {
    const open = details.match(/open=(\w+)/)?.[1];
    return open ? (open === 'true' ? 'OPEN' : 'CLOSED') : '';
  }
  return '';
}

export function labelOf(item: ApiRoadElement): string {
  const details = item.details ?? '';
  if (item.type.includes('SpeedLimit')) {
    return details.match(/limitKmh=(\d+)/)?.[1] ?? '';
  }
  if (item.type.includes('Vehicle')) {
    return details.match(/speedMps=([\d.]+)/)?.[1] ?? '';
  }
  return '';
}

export function objectIconLabel(kind: RoadObjectKind): string {
  switch (kind) {
    case 'speedLimit': return 'Speed limit sign';
    case 'stopSign': return 'Stop sign';
    case 'trafficLight': return 'Traffic light';
    case 'barrier': return 'Barrier';
    case 'pothole': return 'Pothole';
    case 'obstacle': return 'Obstacle';
    case 'vehicle': return 'Vehicle';
    case 'pedestrian': return 'Pedestrian';
    case 'crosswalk': return 'Crosswalk';
    case 'radar': return 'Speed radar';
  }
}

const ALL_LANES: LaneName[] = ['LEFT', 'CENTER', 'RIGHT'];

// ----- static models --------------------------------------------------------------------

/** Stripes (black on the warning colour) painted on the rear face of a crate. */
function hazardStripes(x0: number, x1: number, y0: number, y1: number, z: number): Decal[] {
  const count = 4;
  const width = (x1 - x0) / (count * 2);
  const lean = 0.32;
  const stripes: Decal[] = [];
  for (let i = 0; i < count; i++) {
    const a = x0 + i * 2 * width;
    stripes.push({
      pts: [[a, y0, z], [a + width, y0, z], [a + width + lean, y1, z], [a + lean, y1, z]],
      part: 'hazard-stripe'
    });
  }
  return stripes;
}

const OBSTACLE_SOLIDS: Solid[] = (() => {
  const crate = box('obstacle', -0.75, 0.75, 0, 1.1, -0.6, 0.6);
  faceOf(crate, 'rear').decals.push(...hazardStripes(-0.6, 0.6, 0.18, 0.92, -0.604));
  const beacon = box('beacon', -0.12, 0.12, 1.1, 1.3, -0.12, 0.12);
  return [crate, beacon];
})();

const BARRIER_ARM_SEGMENTS = 6;
const BARRIER_ARM_LENGTH = 3.0;
const BARRIER_PIVOT: [number, number] = [-1.55, 1.1];

/** Boom gate across a lane; the arm is raised (steeply) when the backend reports it open. */
function barrierSolids(open: boolean): Solid[] {
  const post = box('post', -1.85, -1.5, 0, 1.3, -0.2, 0.2);
  faceOf(post, 'rear').decals.push({ pts: rectZ(-1.78, -1.57, 0.4, 0.5, -0.204), part: 'hazard-stripe' });
  const beacon = box('beacon', -1.75, -1.6, 1.3, 1.48, -0.08, 0.08);
  const rest = box('post', 1.4, 1.65, 0, 0.9, -0.12, 0.12);
  const angle = open ? (68 * Math.PI) / 180 : 0;
  const dir: [number, number] = [Math.cos(angle), Math.sin(angle)];
  const normal: [number, number] = [-dir[1], dir[0]];
  const solids: Solid[] = [rest, post, beacon];
  for (let k = 0; k < BARRIER_ARM_SEGMENTS; k++) {
    const t0 = (k * BARRIER_ARM_LENGTH) / BARRIER_ARM_SEGMENTS;
    const t1 = ((k + 1) * BARRIER_ARM_LENGTH) / BARRIER_ARM_SEGMENTS;
    const p0: [number, number] = [BARRIER_PIVOT[0] + dir[0] * t0, BARRIER_PIVOT[1] + dir[1] * t0];
    const p1: [number, number] = [BARRIER_PIVOT[0] + dir[0] * t1, BARRIER_PIVOT[1] + dir[1] * t1];
    const h = 0.09;
    solids.push(extrude(k % 2 === 0 ? 'arm-red' : 'arm-white', [
      [p0[0] - normal[0] * h, p0[1] - normal[1] * h], [p1[0] - normal[0] * h, p1[1] - normal[1] * h],
      [p1[0] + normal[0] * h, p1[1] + normal[1] * h], [p0[0] + normal[0] * h, p0[1] + normal[1] * h]
    ], -0.09, 0.09));
  }
  return solids;
}
const BARRIER_CLOSED = barrierSolids(false);
const BARRIER_OPEN = barrierSolids(true);

const GANTRY_HEIGHT = 6.1;
const HEAD_BOTTOM = 3.9;
const HEAD_TOP = 5.5;
const GANTRY_POSTS_X = SHOULDER_HALF_METRES + 0.2;

/** Posts and beam spanning the road. */
const GANTRY_STRUCTURE: Solid[] = [
  box('gantry', -GANTRY_POSTS_X - 0.18, -GANTRY_POSTS_X + 0.18, 0, GANTRY_HEIGHT, -0.18, 0.18),
  box('gantry', GANTRY_POSTS_X - 0.18, GANTRY_POSTS_X + 0.18, 0, GANTRY_HEIGHT, -0.18, 0.18),
  box('gantry', -GANTRY_POSTS_X, GANTRY_POSTS_X, GANTRY_HEIGHT - 0.5, GANTRY_HEIGHT, -0.15, 0.15)
];

const LAMP_Y = { RED: HEAD_TOP - 0.38, YELLOW: (HEAD_TOP + HEAD_BOTTOM) / 2, GREEN: HEAD_BOTTOM + 0.38 } as const;
const HEAD_BACK_Z = -0.22;
/** A lit lamp glows, but never as a floodlight over the scene when the gantry is close. */
const GLOW_MAX_PX = 26;
const headCache = new Map<string, Solid>();

/** A signal head hanging over a lane; the lit lamp follows the backend state. */
function signalHead(state: string): Solid {
  const cached = headCache.get(state);
  if (cached) {
    return cached;
  }
  const housing = box('head', -0.3, 0.3, HEAD_BOTTOM, HEAD_TOP, HEAD_BACK_Z, 0.22);
  const rear = faceOf(housing, 'rear');
  rear.decals.push(
    { pts: rectZ(-0.36, 0.36, HEAD_BOTTOM - 0.06, HEAD_TOP + 0.06, HEAD_BACK_Z - 0.003), part: 'head-plate' },
    { pts: discZ(0, LAMP_Y.RED, 0.19, HEAD_BACK_Z - 0.006), part: state === 'RED' ? 'lamp-red' : 'lamp-off', extra: 'lamp lamp--red' },
    { pts: discZ(0, LAMP_Y.YELLOW, 0.19, HEAD_BACK_Z - 0.006), part: state === 'YELLOW' ? 'lamp-yellow' : 'lamp-off', extra: 'lamp lamp--amber' },
    { pts: discZ(0, LAMP_Y.GREEN, 0.19, HEAD_BACK_Z - 0.006), part: state === 'GREEN' ? 'lamp-green' : 'lamp-off', extra: 'lamp lamp--green' }
  );
  headCache.set(state, housing);
  return housing;
}

// ----- item assembly --------------------------------------------------------------------

export interface ItemsInput {
  camera: Camera;
  kin: Kinematics;
  snapshot: ApiSimulationSnapshot;
  egoLane: LaneName;
  /** Objects farther away than this get no distance tag (sensor reach). */
  tagRangeMeters: number;
}

export interface ItemsView {
  /** Painted on the road: crosswalks, potholes, stop lines, speed-limit paint. */
  flat: RoadItemView[];
  /** Standing objects, still to be depth-sorted with the ego. */
  standing: RoadItemView[];
}

function tagFor(x: number, y: number, ahead: number, relative: number, detectedByCamera: boolean, detectedByRadar: boolean, tagRange: number): TagView | null {
  const passed = relative <= 0;
  const badge = !passed && (detectedByCamera || detectedByRadar) ? (detectedByCamera ? 'DETECTED' : 'RADAR') : null;
  const text = passed ? 'PASSED' : (ahead <= tagRange || badge ? `${Math.round(relative)} m` : '');
  if (!badge && !text) {
    return null;
  }
  return {
    x, y, badge, text, passed,
    badgeWidth: badge ? badge.length * 5.6 + 8 : 0,
    textWidth: text.length * 5.8 + 6
  };
}

function haloPath(camera: Camera, lateral: number, ahead: number, halfWidth: number, halfLength: number): string | null {
  const pts = shadowPoints(camera, lateral, ahead, halfWidth, halfLength);
  return pts ? pathOf(pts) : null;
}

function fogOf(ahead: number): number {
  return ahead <= 70 ? 1 : Math.max(0.4, 1 - (ahead - 70) / 260);
}

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

export function buildItems(input: ItemsInput): ItemsView {
  const { camera, kin, snapshot, egoLane } = input;
  const flat: RoadItemView[] = [];
  const standing: RoadItemView[] = [];
  const objects = snapshot.worldObjects ?? [];

  const worldOf = (item: ApiRoadElement) => kin.objects[item.id]?.world ?? ((item.worldPositionMeters ?? kin.egoPos + item.positionMeters));
  const crosswalkWorlds = objects.filter((o) => kindOf(o) === 'crosswalk').map(worldOf);
  const snapToCrosswalk = (world: number) => {
    let best = world;
    let bestDistance = CROSSWALK_SNAP_METRES;
    for (const crosswalk of crosswalkWorlds) {
      const distance = Math.abs(crosswalk - world);
      if (distance <= bestDistance) {
        best = crosswalk;
        bestDistance = distance;
      }
    }
    return best;
  };

  for (const item of objects) {
    const kind = kindOf(item);
    const state = stateOf(item);
    const world = worldOf(item);
    // A pedestrian is drawn walking along the middle of its crosswalk's stripes.
    const drawnWorld = kind === 'pedestrian' ? snapToCrosswalk(world) : world;
    const ahead = drawnWorld - kin.egoPos;
    if (!isInFront(camera, ahead) || ahead > VIEW_AHEAD_METRES || item.positionMeters < -36) {
      continue;
    }
    const laneName = (item.lane as LaneName) in LANE_CENTRE_METRES ? (item.lane as LaneName) : 'CENTER';
    const pedLateral = kin.objects[item.id]?.lateral;
    const lateral = kind === 'pedestrian' && pedLateral != null
      ? clamp(pedLateral, -SIDEWALK_METRES, SIDEWALK_METRES)
      : LANE_CENTRE_METRES[laneName];
    const detectedByCamera = snapshot.cameraDetectedObjectIds?.includes(item.id) ?? false;
    const detectedByRadar = snapshot.radarDetectedObjectIds?.includes(item.id) ?? false;
    const base = {
      id: item.id,
      kind,
      state,
      label: labelOf(item),
      ariaLabel: `${objectIconLabel(kind)} ${state ? state.toLowerCase() + ' ' : ''}at ${world.toFixed(0)} metres`,
      relativeMeters: item.positionMeters,
      worldPositionMeters: world,
      ahead,
      opacity: fogOf(ahead),
      detectedByCamera,
      detectedByRadar,
      passed: item.positionMeters <= 0
    };
    const groundPoint = project(camera, lateral, 0, ahead);
    const depth = groundPoint?.z ?? 0;
    const tagAt = (x: number, y: number) => tagFor(x, y, ahead, item.positionMeters, detectedByCamera, detectedByRadar, input.tagRangeMeters);
    const detected = detectedByCamera || detectedByRadar;
    const view = (shape: RoadShape, patch: Partial<RoadItemView> = {}): RoadItemView => ({
      ...base, shape, role: 'main', lane: laneName, lateral, depth, tag: null, halo: null, ...patch
    });

    switch (kind) {
      case 'crosswalk': {
        const paths: string[] = [];
        for (let x = -ASPHALT_HALF_METRES + 0.25; x < ASPHALT_HALF_METRES - 0.4; x += 1.0) {
          const bar = groundQuad(camera, x, x + 0.5, ahead - CROSSWALK_DEPTH_METRES / 2, ahead + CROSSWALK_DEPTH_METRES / 2);
          if (bar) {
            paths.push(pathOf(bar));
          }
        }
        flat.push(view('crosswalk', { lane: 'ALL', lateral: 0, d: paths.join(' ') }));
        break;
      }
      case 'pothole': {
        const rings = [[0.8, 1.05], [0.62, 0.82], [0.38, 0.5]]
          .map(([hw, hl]) => shadowPoints(camera, lateral, ahead, hw, hl))
          .map((pts) => (pts ? pathOf(pts) : ''));
        const top = project(camera, lateral, 0, ahead);
        flat.push(view('pothole', {
          rings,
          halo: detected ? haloPath(camera, lateral, ahead, 1.3, 1.6) : null,
          tag: top ? tagAt(top.x, top.y - 12) : null
        }));
        break;
      }
      case 'speedLimit': {
        // One limit holds for the whole road: painted in every lane at the same distance.
        for (const lane of ALL_LANES) {
          const x = LANE_CENTRE_METRES[lane];
          const main = lane === egoLane;
          const transform = groundMatrix(camera, x, ahead);
          const top = project(camera, x, 0, ahead);
          if (!transform) {
            continue;
          }
          flat.push(view('speedMark', {
            lane, lateral: x, role: main ? 'main' : 'copy', transform,
            depth: top?.z ?? depth,
            tag: main && top ? tagAt(top.x, top.y - 14) : null
          }));
        }
        // Roadside sign on the right shoulder, upright, so the limit is readable at a distance.
        const frame = spriteFrame(camera, SHOULDER_HALF_METRES - 0.9, ahead, MIN_SPRITE_PX_PER_METRE * 1.3);
        if (frame) {
          standing.push(view('speedSign', { role: 'decor', lane: 'RIGHT', lateral: SHOULDER_HALF_METRES - 0.9, transform: frame.transform, depth: frame.z }));
        }
        break;
      }
      case 'stopSign': {
        // Like the speed-limit sign, a real roadside sign: upright on a pole on the right verge,
        // outside the lanes. A stop sign governs the whole road, so it stands once, whatever cell
        // the backend stores it in, and its stop line is painted across all three lanes.
        const verge = SHOULDER_HALF_METRES - 0.9;
        const frame = spriteFrame(camera, verge, ahead, MIN_SPRITE_PX_PER_METRE * 1.3);
        if (!frame) {
          break;
        }
        const line = groundQuad(camera, -ASPHALT_HALF_METRES, ASPHALT_HALF_METRES, ahead - 0.25, ahead + 0.25);
        if (line) {
          flat.push(view('stopLine', { role: 'decor', d: pathOf(line) }));
        }
        standing.push(view('stopSign', {
          lateral: verge, transform: frame.transform, depth: frame.z,
          tag: tagAt(frame.x, frame.y - 3.5 * frame.scale - 4)
        }));
        break;
      }
      case 'trafficLight': {
        const structure = GANTRY_STRUCTURE.flatMap((solid) => renderSolid(camera, solid, translate(0, 0, ahead)).faces);
        const heads: GantryHead[] = ALL_LANES.map((lane) => {
          const x = LANE_CENTRE_METRES[lane];
          const rendered = renderSolid(camera, signalHead(state), translate(x, 0, ahead));
          const lit = state === 'RED' ? LAMP_Y.RED : state === 'YELLOW' ? LAMP_Y.YELLOW : state === 'GREEN' ? LAMP_Y.GREEN : null;
          const lamp = lit === null ? null : project(camera, x, lit, ahead + HEAD_BACK_Z);
          const top = project(camera, x, HEAD_TOP + 0.5, ahead);
          const main = lane === egoLane;
          return {
            lane,
            main,
            faces: rendered.faces,
            glow: lamp ? {
              x: lamp.x, y: lamp.y,
              r: Math.min(GLOW_MAX_PX, Math.max(5, (camera.focal / lamp.z) * 0.8)),
              cls: `glow glow--${state.toLowerCase()}`
            } : null,
            tag: main && top ? tagAt(top.x, top.y - 4) : null
          };
        });
        standing.push(view('gantry', { faces: structure, heads, depth, role: 'main' }));
        break;
      }
      case 'barrier': {
        const solids = state === 'OPEN' ? BARRIER_OPEN : BARRIER_CLOSED;
        const place = translate(lateral, 0, ahead);
        const faces = solids.flatMap((solid) => renderSolid(camera, solid, place).faces);
        const top = project(camera, lateral, 1.9, ahead);
        standing.push(view('barrier', {
          faces, open: state === 'OPEN',
          halo: detected ? haloPath(camera, lateral, ahead, 2.1, 0.8) : null,
          tag: top ? tagAt(top.x, top.y - 4) : null
        }));
        break;
      }
      case 'obstacle': {
        const place = translate(lateral, 0, ahead);
        const faces = OBSTACLE_SOLIDS.flatMap((solid) => renderSolid(camera, solid, place).faces);
        const top = project(camera, lateral, 1.5, ahead);
        standing.push(view('obstacle', {
          faces,
          halo: detected ? haloPath(camera, lateral, ahead, 1.2, 1.1) : null,
          tag: top ? tagAt(top.x, top.y - 4) : null
        }));
        break;
      }
      case 'radar': {
        // Standing on the right shoulder whatever lane the map cell says: it watches the road.
        const verge = SHOULDER_HALF_METRES - 0.9;
        const frame = spriteFrame(camera, verge, ahead, MIN_SPRITE_PX_PER_METRE * 1.2);
        if (!frame) {
          break;
        }
        standing.push(view('radar', {
          lateral: verge, transform: frame.transform, depth: frame.z,
          tag: tagAt(frame.x, frame.y - 4.9 * frame.scale - 4)
        }));
        break;
      }
      case 'pedestrian': {
        const frame = spriteFrame(camera, lateral, ahead, MIN_SPRITE_PX_PER_METRE * 1.2);
        if (!frame) {
          break;
        }
        standing.push(view('pedestrian', {
          transform: frame.transform, depth: frame.z,
          // Legs alternate as the person moves across the road, which is the only thing that moves.
          swing: Math.sin(lateral * 4.2),
          halo: detected ? haloPath(camera, lateral, ahead, 0.9, 0.9) : null,
          tag: tagAt(frame.x, frame.y - 1.95 * frame.scale - 4)
        }));
        break;
      }
      case 'vehicle': {
        const pose: CarPose = { lateral, ahead, yawDegrees: 0, steerDegrees: 0 };
        const car = renderCar(camera, TRAFFIC_CAR, pose);
        const shadow = shadowPoints(camera, lateral, ahead, 1.15, 2.5);
        const tone = Math.abs(hashString(item.id)) % 3;
        standing.push(view('vehicle', {
          car, tone, shadow: shadow ? pathOf(shadow) : '', depth: car.z || depth,
          halo: detected ? haloPath(camera, lateral, ahead, 1.6, 3.1) : null,
          tag: car.roofY && groundPoint ? tagAt(groundPoint.x, car.roofY - 6) : null
        }));
        break;
      }
    }
  }
  return { flat, standing };
}

function hashString(value: string): number {
  let h = 0;
  for (let i = 0; i < value.length; i++) {
    h = (h * 31 + value.charCodeAt(i)) | 0;
  }
  return h;
}

// ===========================================================================================
// Ego
// ===========================================================================================

/** Visual emphasis on the (small) backend heading so the yaw of a lane change reads on screen. */
const YAW_EMPHASIS = 1.5;
const WHEEL_STEER_DEGREES = 12;

export interface EgoInput {
  camera: Camera;
  lateral: number;
  headingDegrees: number;
  steering: string;
}

export function buildEgo(input: EgoInput): RoadItemView {
  const { camera } = input;
  const steer = input.steering === 'LEFT' || input.steering === 'STEER_LEFT' ? -WHEEL_STEER_DEGREES
    : input.steering === 'RIGHT' || input.steering === 'STEER_RIGHT' ? WHEEL_STEER_DEGREES : 0;
  const yaw = clamp(input.headingDegrees * YAW_EMPHASIS, -25, 25);
  const car = renderCar(camera, EGO_CAR, { lateral: input.lateral, ahead: 0, yawDegrees: yaw, steerDegrees: steer + yaw * 0.3 });
  const shadow = shadowPoints(camera, input.lateral, 0, 1.2, 2.6);
  return {
    id: 'ego', kind: 'vehicle', shape: 'ego', role: 'main', lane: 'CENTER', state: '', label: '', ariaLabel: 'Ego vehicle',
    relativeMeters: 0, worldPositionMeters: 0, ahead: 0, lateral: input.lateral, depth: camera.back, opacity: 1,
    detectedByCamera: false, detectedByRadar: false, passed: false, tag: null, halo: null,
    car, shadow: shadow ? pathOf(shadow) : '',
    lamps: car.lamps
  };
}
