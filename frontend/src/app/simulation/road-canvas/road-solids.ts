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

function cross(a: Vec3, b: Vec3): Vec3 {
  return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
}

function sub(a: Vec3, b: Vec3): Vec3 {
  return [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
}

function dot(a: Vec3, b: Vec3): number {
  return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}

/** True when the face looks at the camera (outward normal against the camera position). */
function facesCamera(camera: Camera, pts: readonly Vec3[], solidCentre: Vec3): boolean {
  if (pts.length < 3) {
    return false;
  }
  const c = centroid(pts);
  let n = cross(sub(pts[1], pts[0]), sub(pts[2], pts[0]));
  if (dot(n, sub(c, solidCentre)) < 0) {
    n = [-n[0], -n[1], -n[2]];
  }
  return dot(n, sub([camera.x, camera.eye, -camera.back], c)) > 1e-6;
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
    if (screen) {
      const z = screen.reduce((sum, p) => sum + p.z, 0) / screen.length;
      zSum += z;
      zCount++;
      faces.push({ d: pathOfPoints(screen), cls: `${classPrefix}f f--${face.part} f--${face.role}`, z });
    }
    for (const decal of face.decals) {
      const projected = screen ? projectAll(camera, decal.pts.map(transform)) : null;
      const view: FaceView = {
        d: projected ? pathOfPoints(projected) : '',
        cls: `${classPrefix}f f--${decal.part} f--${face.role}${decal.extra ? ' ' + decal.extra : ''}`,
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

/** The ego: a low, wide coupe. */
export const EGO_CAR: CarModel = buildCar({
  length: 4.6, hw: 0.97, beltY: 0.94, roofY: 1.45,
  cabinBottom: [-1.45, 1.05], cabinTop: [-0.62, 0.45], mirrors: true
});

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
