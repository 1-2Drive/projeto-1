/**
 * Tiny solid-modelling layer for the driving view: convex solids (boxes, frusta, extruded
 * polygons) built in metres and drawn as flat-shaded SVG faces through the shared pinhole camera.
 *
 * Every vertex is projected individually, so cars, barriers and gantries have real perspective
 * (receding sides, visible roof and rear window) instead of being a skewed flat picture.
 * Faces are culled with a back-face test and drawn in a fixed solid order (painter's algorithm),
 * which is exact for the camera used here: always behind and above, looking down the road.
 *
 * Local frame of a solid: x to the right, y up, z along the direction of travel.
 */
import { Camera, project, ScreenPoint } from './road-projection';

export type Vec3 = readonly [number, number, number];
export type FaceRole = 'rear' | 'front' | 'left' | 'right' | 'top' | 'bottom';

export interface Decal {
  pts: Vec3[];
  /** CSS part name; becomes the class `f--<part>`. */
  part: string;
  /** Extra classes (e.g. `indicator-light`). */
  extra?: string;
  /** Group the decal is reported in (e.g. `indicator-left`); undefined draws it with the body. */
  group?: string;
}

export interface Face3 {
  pts: Vec3[];
  part: string;
  role: FaceRole;
  decals: Decal[];
}

export interface Solid {
  faces: Face3[];
  centre: Vec3;
  /** Shade each face by its own angle to the light (rounded bodies) instead of by its side. */
  smooth?: boolean;
}

export interface FaceView {
  /** SVG path data, empty when the face is hidden (so lamps keep a stable DOM). */
  d: string;
  cls: string;
  /** Mean camera depth, for ordering whole objects. */
  z: number;
}

/** Local-to-camera-world transform of a solid. */
export type Transform = (p: Vec3) => Vec3;

export const identity: Transform = (p) => p;

/** Rotates around the vertical axis through (cx, cz); positive yaw turns the nose to the right. */
export function yawAbout(cx: number, cz: number, yawRadians: number, then: Transform = identity): Transform {
  const sin = Math.sin(yawRadians);
  const cos = Math.cos(yawRadians);
  return (p) => {
    const dx = p[0] - cx;
    const dz = p[2] - cz;
    return then([cx + dx * cos + dz * sin, p[1], cz - dx * sin + dz * cos]);
  };
}

export function translate(dx: number, dy: number, dz: number, then: Transform = identity): Transform {
  return (p) => then([p[0] + dx, p[1] + dy, p[2] + dz]);
}

function centroid(points: readonly Vec3[]): Vec3 {
  let x = 0;
  let y = 0;
  let z = 0;
  for (const p of points) {
    x += p[0];
    y += p[1];
    z += p[2];
  }
  const n = points.length || 1;
  return [x / n, y / n, z / n];
}

function solidOf(faces: Face3[]): Solid {
  return { faces, centre: centroid(faces.flatMap((face) => face.pts)) };
}

export interface RectSpec {
  /** Half width (x), centred on `cx`. */
  hw: number;
  z0: number;
  z1: number;
  cx?: number;
}

/**
 * Convex frustum between a rectangle at height y0 and another at y1; a box when both match.
 * Parts may be overridden per face role (e.g. glass windows on a body-coloured cabin).
 */
export function frustum(part: string, y0: number, bottom: RectSpec, y1: number, top: RectSpec,
                        partByRole: Partial<Record<FaceRole, string>> = {}): Solid {
  const bx = bottom.cx ?? 0;
  const tx = top.cx ?? 0;
  const brl: Vec3 = [bx - bottom.hw, y0, bottom.z0];
  const brr: Vec3 = [bx + bottom.hw, y0, bottom.z0];
  const bfr: Vec3 = [bx + bottom.hw, y0, bottom.z1];
  const bfl: Vec3 = [bx - bottom.hw, y0, bottom.z1];
  const trl: Vec3 = [tx - top.hw, y1, top.z0];
  const trr: Vec3 = [tx + top.hw, y1, top.z0];
  const tfr: Vec3 = [tx + top.hw, y1, top.z1];
  const tfl: Vec3 = [tx - top.hw, y1, top.z1];
  const face = (role: FaceRole, pts: Vec3[]): Face3 => ({ pts, role, part: partByRole[role] ?? part, decals: [] });
  return solidOf([
    face('rear', [brl, brr, trr, trl]),
    face('front', [bfl, bfr, tfr, tfl]),
    face('left', [brl, bfl, tfl, trl]),
    face('right', [brr, bfr, tfr, trr]),
    face('top', [trl, trr, tfr, tfl]),
    face('bottom', [brl, brr, bfr, bfl])
  ]);
}

export function box(part: string, x0: number, x1: number, y0: number, y1: number, z0: number, z1: number): Solid {
  const spec: RectSpec = { hw: (x1 - x0) / 2, z0, z1, cx: (x0 + x1) / 2 };
  return frustum(part, y0, spec, y1, spec);
}

/** Prism: a polygon drawn in the x/y plane and extruded between z0 and z1. */
export function extrude(part: string, polygon: ReadonlyArray<readonly [number, number]>, z0: number, z1: number): Solid {
  const faces: Face3[] = [
    { pts: polygon.map(([x, y]) => [x, y, z0] as Vec3), role: 'rear', part, decals: [] },
    { pts: polygon.map(([x, y]) => [x, y, z1] as Vec3).reverse(), role: 'front', part, decals: [] }
  ];
  for (let i = 0; i < polygon.length; i++) {
    const a = polygon[i];
    const b = polygon[(i + 1) % polygon.length];
    const dx = b[0] - a[0];
    const dy = b[1] - a[1];
    const role: FaceRole = Math.abs(dy) >= Math.abs(dx) ? (dx >= 0 ? 'right' : 'left') : (dy >= 0 ? 'top' : 'bottom');
    faces.push({ pts: [[a[0], a[1], z0], [b[0], b[1], z0], [b[0], b[1], z1], [a[0], a[1], z1]], role, part, decals: [] });
  }
  return solidOf(faces);
}

/** Rectangle lying on the plane z = const (a rear/front face decal). */
export function rectZ(x0: number, x1: number, y0: number, y1: number, z: number): Vec3[] {
  return [[x0, y0, z], [x1, y0, z], [x1, y1, z], [x0, y1, z]];
}

/** Rectangle lying on the plane x = const (a side face decal). */
export function rectX(x: number, y0: number, y1: number, z0: number, z1: number): Vec3[] {
  return [[x, y0, z0], [x, y0, z1], [x, y1, z1], [x, y1, z0]];
}

/** Regular polygon on the plane z = const, e.g. a traffic-light lamp. */
export function discZ(cx: number, cy: number, radius: number, z: number, sides = 14): Vec3[] {
  const pts: Vec3[] = [];
  for (let i = 0; i < sides; i++) {
    const a = (i / sides) * Math.PI * 2;
    pts.push([cx + Math.cos(a) * radius, cy + Math.sin(a) * radius, z]);
  }
  return pts;
}

/** Shrinks a convex face towards its centre, producing a window / panel inset. */
export function inset(pts: readonly Vec3[], factor: number): Vec3[] {
  const c = centroid(pts);
  return pts.map((p) => [c[0] + (p[0] - c[0]) * factor, c[1] + (p[1] - c[1]) * factor, c[2] + (p[2] - c[2]) * factor] as Vec3);
}

export function faceOf(solid: Solid, role: FaceRole): Face3 {
  const found = solid.faces.find((face) => face.role === role);
  if (!found) {
    throw new Error(`solid has no ${role} face`);
  }
  return found;
}

/**
 * A convex solid skinned through a list of cross-sections (rings of the same number of vertices,
 * ordered the same way), rear to front. Segment i/edge j is face `i * ringSize + j`; the optional
 * end caps follow. Rounded bodies are made of a few of these, each convex on its own.
 */
export function loft(part: string, rings: ReadonlyArray<ReadonlyArray<Vec3>>, caps = true, smooth = true): Solid {
  const size = rings[0].length;
  const all = rings.flat();
  const centre = centroid(all);
  const faces: Face3[] = [];
  const add = (pts: Vec3[], fixedRole?: FaceRole) => {
    faces.push({ pts, part, role: fixedRole ?? roleOfNormal(outwardNormal(pts, centre)), decals: [] });
  };
  for (let i = 0; i < rings.length - 1; i++) {
    for (let j = 0; j < size; j++) {
      const k = (j + 1) % size;
      add([rings[i][j], rings[i][k], rings[i + 1][k], rings[i + 1][j]]);
    }
  }
  if (caps) {
    add([...rings[0]], 'rear');
    add([...rings[rings.length - 1]].reverse(), 'front');
  }
  return { faces, centre, smooth };
}

function roleOfNormal(n: Vec3): FaceRole {
  const [x, y, z] = n;
  if (Math.abs(y) >= Math.abs(x) && Math.abs(y) >= Math.abs(z)) {
    return y >= 0 ? 'top' : 'bottom';
  }
  if (Math.abs(z) >= Math.abs(x)) {
    return z >= 0 ? 'front' : 'rear';
  }
  return x >= 0 ? 'right' : 'left';
}

/** Cylinder with its axis along x (a wheel): a regular polygon in the z/y plane, extruded between x0 and x1. */
export function wheelSolid(part: string, radius: number, x0: number, x1: number, sides = 12): Solid {
  const ring = (x: number): Vec3[] => Array.from({ length: sides }, (_, i) => {
    const a = (i / sides) * Math.PI * 2;
    return [x, radius + Math.sin(a) * radius, Math.cos(a) * radius] as Vec3;
  });
  const solid = loft(part, [ring(x0), ring(x1)], false, true);
  const centre = centroid(solid.faces.flatMap((face) => face.pts));
  const cap = (x: number, role: FaceRole, reverse: boolean): Face3 => {
    const pts = ring(x);
    return { pts: reverse ? pts.reverse() : pts, part, role, decals: [] };
  };
  solid.faces.push(cap(x0, 'left', false), cap(x1, 'right', true));
  return { faces: solid.faces, centre, smooth: true };
}

function cross(a: Vec3, b: Vec3): Vec3 {
  return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
}

function sub(a: Vec3, b: Vec3): Vec3 {
  return [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
}

function dot(a: Vec3, b: Vec3): number {
  return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}

/** Unit-free outward normal of a face of a convex solid. */
function outwardNormal(pts: readonly Vec3[], solidCentre: Vec3): Vec3 {
  const c = centroid(pts);
  const n = cross(sub(pts[1], pts[0]), sub(pts[2], pts[0]));
  return dot(n, sub(c, solidCentre)) < 0 ? [-n[0], -n[1], -n[2]] : n;
}

/** True when the face looks at the camera (outward normal against the camera position). */
function facesCamera(camera: Camera, pts: readonly Vec3[], solidCentre: Vec3): boolean {
  if (pts.length < 3) {
    return false;
  }
  const n = outwardNormal(pts, solidCentre);
  return dot(n, sub([camera.x, camera.eye, -camera.back], centroid(pts))) > 1e-6;
}

/** Light from above, slightly to the left and behind the car, as seen from the chase camera. */
const LIGHT: Vec3 = (() => {
  const v: Vec3 = [-0.42, 0.82, -0.38];
  const l = Math.hypot(...v);
  return [v[0] / l, v[1] / l, v[2] / l];
})();
const SHADE_STEPS = 7;

/** Brightness bucket 0..SHADE_STEPS of a face, from the angle between its normal and the light. */
function shadeStep(normal: Vec3): number {
  const length = Math.hypot(...normal) || 1;
  const lambert = dot(normal, LIGHT) / length;
  const t = Math.min(1, Math.max(0, (lambert + 0.35) / 1.3));
  return Math.round(t * SHADE_STEPS);
}

function projectAll(camera: Camera, pts: readonly Vec3[]): ScreenPoint[] | null {
  const out: ScreenPoint[] = [];
  for (const p of pts) {
    const s = project(camera, p[0], p[1], p[2]);
    if (!s) {
      return null;
    }
    out.push(s);
  }
  return out;
}

function pathOfPoints(points: readonly ScreenPoint[]): string {
  let d = '';
  for (let i = 0; i < points.length; i++) {
    d += `${i === 0 ? 'M' : 'L'}${points[i].x.toFixed(1)} ${points[i].y.toFixed(1)} `;
  }
  return d + 'Z';
}

export interface RenderedSolid {
  faces: FaceView[];
  /** Decals that belong to a named group (indicator lamps), in a stable order with empty d when hidden. */
  groups: Record<string, FaceView[]>;
  /** Mean camera depth of the visible faces. */
  z: number;
}

/** Projects the visible faces (and their decals) of a solid. `transform` places it in the world. */
export function renderSolid(camera: Camera, solid: Solid, transform: Transform, classPrefix = ''): RenderedSolid {
  const centre = transform(solid.centre);
  const faces: FaceView[] = [];
  const groups: Record<string, FaceView[]> = {};
  let zSum = 0;
  let zCount = 0;
  const addGroup = (group: string, view: FaceView) => (groups[group] ??= []).push(view);

  for (const face of solid.faces) {
    const world = face.pts.map(transform);
    const visible = facesCamera(camera, world, centre);
    const screen = visible ? projectAll(camera, world) : null;
    const shade = solid.smooth && screen ? ` f--smooth f--s${shadeStep(outwardNormal(world, centre))}` : '';
    if (screen) {
      const z = screen.reduce((sum, p) => sum + p.z, 0) / screen.length;
      zSum += z;
      zCount++;
      faces.push({ d: pathOfPoints(screen), cls: `${classPrefix}f f--${face.part} f--${face.role}${shade}`, z });
    }
    for (const decal of face.decals) {
      const projected = screen ? projectAll(camera, decal.pts.map(transform)) : null;
      const view: FaceView = {
        d: projected ? pathOfPoints(projected) : '',
        cls: `${classPrefix}f f--${decal.part} f--${face.role}${shade}${decal.extra ? ' ' + decal.extra : ''}`,
        z: projected ? projected.reduce((sum, p) => sum + p.z, 0) / projected.length : 0
      };
      if (decal.group) {
        addGroup(decal.group, view);
      } else if (projected) {
        faces.push(view);
      }
    }
  }
  return { faces, groups, z: zCount ? zSum / zCount : 0 };
}

// ===========================================================================================
// Models
// ===========================================================================================

export interface CarModel {
  /** Solids drawn under everything else: [front-left, front-right, rear-left, rear-right]. */
  wheels: { solid: Solid; cx: number; cz: number; front: boolean }[];
  /** Body parts drawn in order. */
  body: Solid[];
  length: number;
  width: number;
  /** Height of the roof, in metres. */
  height: number;
}

interface CarSpec {
  length: number;
  hw: number;
  beltY: number;
  roofY: number;
  /** Cabin extent along z at the belt line and at the roof. */
  cabinBottom: [number, number];
  cabinTop: [number, number];
  mirrors: boolean;
}

/** A hatch/saloon: lower body, tapered glass cabin, tail lamps, plate, bumper and wheels. */
function buildCar(spec: CarSpec): CarModel {
  const { length, hw } = spec;
  const z0 = -length / 2;
  const z1 = length / 2;

  const tub = frustum('body', 0.3, { hw, z0, z1 }, spec.beltY, { hw: hw - 0.05, z0: z0 + 0.1, z1: z1 - 0.3 });
  const rear = faceOf(tub, 'rear');
  // The rear face slants a little (the top is inset), so decals follow its real plane.
  const rz = (y: number) => z0 + ((y - 0.3) / (spec.beltY - 0.3)) * 0.1 - 0.012;
  const lampY0 = 0.62;
  const lampY1 = 0.82;
  const lampHW = hw - 0.07;
  rear.decals.push(
    { pts: rectZ(-hw + 0.02, hw - 0.02, 0.3, 0.46, rz(0.38)), part: 'bumper' },
    // Unlit amber lenses under the indicator lamps, so the car looks complete while they are off.
    { pts: rectZ(-lampHW, -lampHW + 0.22, lampY0, lampY1, rz(0.72)), part: 'lens' },
    { pts: rectZ(lampHW - 0.22, lampHW, lampY0, lampY1, rz(0.72)), part: 'lens' },
    { pts: rectZ(-0.25, 0.25, 0.5, 0.6, rz(0.55)), part: 'plate' },
    { pts: rectZ(-lampHW, -lampHW + 0.22, lampY0, lampY1, rz(0.72)), part: 'indicator', extra: 'indicator-light', group: 'indicator-left' },
    { pts: rectZ(lampHW - 0.22, lampHW, lampY0, lampY1, rz(0.72)), part: 'indicator', extra: 'indicator-light', group: 'indicator-right' },
    { pts: rectZ(-lampHW + 0.24, -0.34, lampY0, lampY1, rz(0.72)), part: 'tail', extra: 'brake-lamp' },
    { pts: rectZ(0.34, lampHW - 0.24, lampY0, lampY1, rz(0.72)), part: 'tail', extra: 'brake-lamp' }
  );
  // Side repeaters sit on the flank, towards the front wing.
  for (const side of ['left', 'right'] as const) {
    // The flank leans inwards towards the belt line; keep the lamp on its real plane.
    const flank = hw - 0.05 * ((0.7 - 0.3) / (spec.beltY - 0.3)) + 0.004;
    const x = side === 'left' ? -flank : flank;
    faceOf(tub, side).decals.push({
      pts: rectX(x, 0.66, 0.74, z1 - 1.1, z1 - 0.8), part: 'indicator',
      extra: 'indicator-light', group: `indicator-${side}`
    });
  }

  const cabin = frustum('body', spec.beltY, { hw: hw - 0.1, z0: spec.cabinBottom[0], z1: spec.cabinBottom[1] },
    spec.roofY, { hw: hw - 0.24, z0: spec.cabinTop[0], z1: spec.cabinTop[1] },
    { top: 'roof' });
  for (const role of ['rear', 'front', 'left', 'right'] as const) {
    const f = faceOf(cabin, role);
    f.decals.push({ pts: inset(f.pts, role === 'left' || role === 'right' ? 0.8 : 0.84), part: 'glass' });
  }
  const roof = faceOf(cabin, 'top');
  roof.decals.push({ pts: inset(roof.pts, 0.55), part: 'roof-shine' });

  const body: Solid[] = [tub, cabin];
  if (spec.mirrors) {
    for (const side of [-1, 1]) {
      const m = box('body', side * (hw - 0.02) + (side < 0 ? -0.16 : 0), side * (hw - 0.02) + (side < 0 ? 0 : 0.16),
        spec.beltY + 0.06, spec.beltY + 0.2, spec.cabinBottom[1] - 0.5, spec.cabinBottom[1] - 0.25);
      faceOf(m, 'rear').decals.push({
        pts: rectZ(side < 0 ? -hw - 0.12 : hw + 0.0, side < 0 ? -hw - 0.0 : hw + 0.12,
          spec.beltY + 0.1, spec.beltY + 0.16, spec.cabinBottom[1] - 0.502),
        part: 'indicator', extra: 'indicator-light', group: side < 0 ? 'indicator-left' : 'indicator-right'
      });
      body.push(m);
    }
  }

  const wheelHalf = 0.13;
  const wheels = [
    { cx: -hw + 0.02, cz: z1 - 0.85, front: true },
    { cx: hw - 0.02, cz: z1 - 0.85, front: true },
    { cx: -hw + 0.02, cz: z0 + 0.85, front: false },
    { cx: hw - 0.02, cz: z0 + 0.85, front: false }
  ].map((w) => ({
    ...w,
    // Centred on the wheel so that steering rotates it in place.
    solid: box('wheel', -wheelHalf, wheelHalf, 0, 0.66, -0.33, 0.33)
  }));

  return { wheels, body, length, width: hw * 2, height: spec.roofY };
}

// -------------------------------------------------------------------------------------------
// The ego: a modern electric fastback sedan (an original design, not a copy of any make).
// Smooth shoulders, a tapering tail with a full-width light bar, a sloping glass roof.
// -------------------------------------------------------------------------------------------

/** One cross-section of the lower body: an octagon with rounded-looking chamfers, bottom to top. */
function bodyRing(z: number, hw: number, y0: number, y1: number): Vec3[] {
  const bw = hw * 0.84;
  const tw = hw * 0.87;
  const low = 0.12;
  const high = 0.17;
  return [
    [-bw, y0, z], [bw, y0, z], [hw, y0 + low, z], [hw, y1 - high, z],
    [tw, y1, z], [-tw, y1, z], [-hw, y1 - high, z], [-hw, y0 + low, z]
  ];
}

/** One cross-section of the glasshouse: belt line, shoulder and roof, with the sides leaning in. */
function cabinRing(z: number, a: number, b: number, c: number, yBelt: number, yShoulder: number, yRoof: number): Vec3[] {
  return [
    [-a, yBelt, z], [a, yBelt, z], [b, yShoulder, z], [c, yRoof, z], [-c, yRoof, z], [-b, yShoulder, z]
  ];
}

const EV_LENGTH = 4.7;
const EV_HW = 0.94;
const EV_WHEEL_RADIUS = 0.34;

function buildElectricSedan(): CarModel {
  const z0 = -EV_LENGTH / 2;
  const hw = EV_HW;

  // ---- lower body: tapering tail, long deck, low nose ----
  const tub = loft('body', [
    bodyRing(z0, 0.8, 0.34, 0.9),
    bodyRing(z0 + 0.16, 0.88, 0.3, 0.97),
    bodyRing(-1.85, 0.93, 0.28, 1.0),
    bodyRing(-0.2, hw, 0.27, 0.98),
    bodyRing(1.9, hw, 0.27, 0.84),
    bodyRing(2.2, 0.9, 0.3, 0.76),
    bodyRing(EV_LENGTH / 2, 0.8, 0.36, 0.66)
  ]);

  // The rear: a dark band under a slim lamp bar that runs across the whole tail, plate, diffuser.
  const tail = tub.faces[tub.faces.length - 2];
  const bar = (x0: number, x1: number, y0: number, y1: number): Vec3[] =>
    [[x0, y1, z0], [x1, y1 - 0.01, z0], [x1, y0 + 0.01, z0], [x0, y0, z0]];
  tail.decals.push(
    { pts: rectZ(-0.72, 0.72, 0.72, 0.89, z0), part: 'bumper' },
    { pts: bar(-0.7, -0.5, 0.74, 0.86), part: 'lens' },
    { pts: bar(0.5, 0.7, 0.74, 0.86), part: 'lens' },
    { pts: bar(-0.7, -0.5, 0.74, 0.86), part: 'indicator', extra: 'indicator-light', group: 'indicator-left' },
    { pts: bar(0.5, 0.7, 0.74, 0.86), part: 'indicator', extra: 'indicator-light', group: 'indicator-right' },
    { pts: bar(-0.5, -0.04, 0.775, 0.855), part: 'tail', extra: 'brake-lamp' },
    { pts: bar(0.04, 0.5, 0.775, 0.855), part: 'tail', extra: 'brake-lamp' },
    { pts: rectZ(-0.04, 0.04, 0.8, 0.83, z0), part: 'tail', extra: 'brake-lamp' },
    { pts: rectZ(-0.24, 0.24, 0.55, 0.65, z0), part: 'plate' },
    { pts: rectZ(-0.6, 0.6, 0.37, 0.44, z0), part: 'bumper' }
  );

  // Flank repeaters towards the front wing, and the wheel arches, on the vertical flanks.
  // Segments 2 (z = -1.85 .. -0.2) and 3 (z = -0.2 .. 1.9) have vertical flanks at x = +-hw: the
  // rear and the front wheel arches, and the front repeater, are drawn on them.
  for (const side of ['left', 'right'] as const) {
    const edge = side === 'left' ? 6 : 2;
    const x = side === 'left' ? -hw : hw;
    const flank = tub.faces[3 * 8 + edge];
    flank.decals.push({ pts: archPolygon(x, 1.42), part: 'arch' });
    flank.decals.push({
      pts: rectX(x, 0.55, 0.62, 1.05, 1.32), part: 'indicator',
      extra: 'indicator-light', group: `indicator-${side}`
    });
  }
  for (const side of ['left', 'right'] as const) {
    const edge = side === 'left' ? 6 : 2;
    tub.faces[2 * 8 + edge].decals.push({ pts: archPolygon(side === 'left' ? -hw : hw, -1.4), part: 'arch' });
  }

  // Lamps are reported in face order (rear lamp, flank repeater, mirror lamp): the tail goes first.
  tub.faces.unshift(...tub.faces.splice(tub.faces.length - 2, 1));

  // ---- glasshouse: fastback with a glass roof ----
  const cabin = loft('body', [
    cabinRing(-1.5, 0.8, 0.79, 0.7, 0.99, 1.0, 1.02),
    cabinRing(-0.72, 0.85, 0.83, 0.72, 0.99, 1.23, 1.4),
    cabinRing(0.25, 0.87, 0.85, 0.73, 0.98, 1.3, 1.45),
    cabinRing(0.6, 0.86, 0.84, 0.72, 0.97, 1.28, 1.42),
    cabinRing(1.3, 0.82, 0.8, 0.66, 0.9, 0.92, 0.94)
  ], false);
  // Faces: segment i, edge j -> i * 6 + j.  Edges: 1 = lower right, 2 = upper right, 3 = roof, 4 = upper left, 5 = lower left.
  const cabinFace = (segment: number, edge: number) => cabin.faces[segment * 6 + edge];
  const rearWindow = cabinFace(0, 3);
  rearWindow.decals.push({ pts: inset(rearWindow.pts, 0.86), part: 'glass' });
  for (const segment of [1, 2]) {
    const roofPanel = cabinFace(segment, 3);
    roofPanel.part = 'roof';
    roofPanel.decals.push({ pts: inset(roofPanel.pts, 0.82), part: 'roof-glass' });
  }
  for (const segment of [1, 2, 3]) {
    for (const edge of [1, 2, 4, 5]) {
      const side = cabinFace(segment, edge);
      side.decals.push({ pts: inset(side.pts, 0.78), part: 'glass' });
    }
  }
  const windscreen = cabinFace(3, 3);
  windscreen.decals.push({ pts: inset(windscreen.pts, 0.86), part: 'glass' });

  // ---- door mirrors, each with its own indicator ----
  const body: Solid[] = [tub, cabin];
  for (const side of [-1, 1]) {
    const inner = side * (hw - 0.1);
    const outer = side * (hw + 0.09);
    const mirror = box('body', Math.min(inner, outer), Math.max(inner, outer), 0.92, 0.99, 0.55, 0.76);
    mirror.smooth = true;
    faceOf(mirror, 'rear').decals.push({
      pts: rectZ(Math.min(inner, outer) + 0.02, Math.max(inner, outer) - 0.02, 0.945, 0.97, 0.55),
      part: 'indicator', extra: 'indicator-light', group: side < 0 ? 'indicator-left' : 'indicator-right'
    });
    body.push(mirror);
  }

  const wheels = [
    { cx: -hw + 0.1, cz: 1.42, front: true },
    { cx: hw - 0.1, cz: 1.42, front: true },
    { cx: -hw + 0.1, cz: -1.4, front: false },
    { cx: hw - 0.1, cz: -1.4, front: false }
  ].map((w) => {
    const solid = wheelSolid('wheel', EV_WHEEL_RADIUS, -0.11, 0.11);
    // A bright rim disc on each outer face, so the wheels read as wheels.
    for (const role of ['left', 'right'] as const) {
      const f = faceOf(solid, role);
      f.decals.push({ pts: inset(f.pts, 0.62), part: 'rim' });
    }
    return { ...w, solid };
  });

  return { wheels, body, length: EV_LENGTH, width: hw * 2, height: 1.45 };
}

/** A wheel arch on a vertical flank at x: the arc of the tyre cut off at the lower edge of the flank. */
function archPolygon(x: number, cz: number): Vec3[] {
  const centreY = EV_WHEEL_RADIUS;
  const lowest = 0.4;
  const radius = EV_WHEEL_RADIUS + 0.02;
  const half = Math.sqrt(radius * radius - (lowest - centreY) ** 2);
  const start = Math.atan2(lowest - centreY, half);
  const pts: Vec3[] = [];
  const steps = 8;
  for (let i = 0; i <= steps; i++) {
    const a = start + ((Math.PI - 2 * start) * i) / steps;
    pts.push([x, centreY + Math.sin(a) * radius, cz + Math.cos(a) * radius]);
  }
  return pts;
}

export const EGO_CAR: CarModel = buildElectricSedan();

/** Other traffic: a taller saloon with a longer cabin, so it reads differently from the ego. */
export const TRAFFIC_CAR: CarModel = buildCar({
  length: 4.5, hw: 0.93, beltY: 0.98, roofY: 1.56,
  cabinBottom: [-1.6, 1.2], cabinTop: [-0.9, 0.55], mirrors: false
});

export interface RenderedCar {
  wheels: { cls: string; faces: FaceView[] }[];
  faces: FaceView[];
  indicatorLeft: FaceView[];
  indicatorRight: FaceView[];
  /** Screen-space top of the roof (px), for labels. */
  roofY: number;
  /** Camera depth of the car's centre. */
  z: number;
  /** Rear-lamp centre and a pixel radius, for glow / wet-road reflections. */
  lamps: { x: number; y: number; r: number } | null;
}

export interface CarPose {
  /** Lateral metre offset of the car centre. */
  lateral: number;
  /** Metres ahead of the ego of the car centre. */
  ahead: number;
  yawDegrees: number;
  /** Front-wheel steer, in degrees. */
  steerDegrees: number;
}

/** Projects a car model at a pose in front of the camera. */
export function renderCar(camera: Camera, model: CarModel, pose: CarPose): RenderedCar {
  const yaw = (pose.yawDegrees * Math.PI) / 180;
  const place = yawAbout(0, 0, yaw, (p) => [p[0] + pose.lateral, p[1], p[2] + pose.ahead]);

  const wheels = model.wheels.map((w) => {
    const steer = w.front ? (pose.steerDegrees * Math.PI) / 180 : 0;
    // The wheel solid is centred on its own axis; steer around it, then move to the axle.
    const local = yawAbout(0, 0, steer, translate(w.cx, 0, w.cz, place));
    return {
      cls: `wheel ${w.front ? 'front-wheel' : 'rear-wheel'}`,
      faces: renderSolid(camera, w.solid, local).faces
    };
  });

  const faces: FaceView[] = [];
  const indicatorLeft: FaceView[] = [];
  const indicatorRight: FaceView[] = [];
  let zSum = 0;
  let zCount = 0;
  for (const solid of model.body) {
    const rendered = renderSolid(camera, solid, place);
    faces.push(...rendered.faces);
    indicatorLeft.push(...(rendered.groups['indicator-left'] ?? []));
    indicatorRight.push(...(rendered.groups['indicator-right'] ?? []));
    if (rendered.z) {
      zSum += rendered.z;
      zCount++;
    }
  }

  const roof = project(camera, pose.lateral, model.height, pose.ahead - model.length / 2 + 1.6);
  const rear = project(camera, pose.lateral, 0.7, pose.ahead - model.length / 2);
  const rearScale = rear ? camera.focal / rear.z : 0;
  return {
    wheels,
    faces,
    indicatorLeft,
    indicatorRight,
    roofY: roof ? roof.y : 0,
    z: zCount ? zSum / zCount : (rear ? rear.z : 0),
    lamps: rear ? { x: rear.x, y: rear.y, r: Math.max(3, rearScale * 0.9) } : null
  };
}

/** Flat shadow under a car, as a ground ellipse (screen points). */
export function shadowPoints(camera: Camera, lateral: number, ahead: number, halfWidth: number, halfLength: number): ScreenPoint[] | null {
  const pts: ScreenPoint[] = [];
  const sides = 14;
  for (let i = 0; i < sides; i++) {
    const a = (i / sides) * Math.PI * 2;
    const p = project(camera, lateral + Math.cos(a) * halfWidth, 0, ahead + Math.sin(a) * halfLength);
    if (!p) {
      return null;
    }
    pts.push(p);
  }
  return pts;
}
