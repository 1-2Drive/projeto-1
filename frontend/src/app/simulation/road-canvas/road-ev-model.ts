/**
 * The ego vehicle: a modern electric fastback sedan, drawn as one rounded body.
 *
 * An original design (inspired by the language of current EVs, copying no make): a long smooth
 * roofline falling into a short duck-tail, a black glass roof running into the rear window, a slim
 * light bar across the tail, wheel arches cut into the flanks and aero wheels.
 *
 * The body is a single skin through cross-sections along the car. Each section is built from a few
 * profile curves (width, floor, deck, roof) so the surface flows instead of being assembled from
 * boxes; the glasshouse grows out of the deck through the `glasshouse` blend.
 *
 * Local frame, like every solid: x to the right, y up, z along the direction of travel, origin at
 * the middle of the car on the road.
 */
import { CarModel, Decal, Vec3, box, faceOf, inset, rectZ, skin, wheelSolid } from './road-solids';

const LENGTH = 4.8;
const REAR = -LENGTH / 2;
const FRONT = LENGTH / 2;
const FRONT_AXLE = 1.42;
const REAR_AXLE = -1.4;
const WHEEL_RADIUS = 0.34;
const WHEEL_HALF_WIDTH = 0.11;
const ROOF_PEAK = 1.45;

type Keys = ReadonlyArray<readonly [number, number]>;

/** Smooth curve through the keys (cubic Hermite with finite-difference slopes), flat beyond the ends. */
function curve(keys: Keys, z: number): number {
  if (z <= keys[0][0]) {
    return keys[0][1];
  }
  const last = keys.length - 1;
  if (z >= keys[last][0]) {
    return keys[last][1];
  }
  let i = 1;
  while (z > keys[i][0]) {
    i++;
  }
  const [z0, y0] = keys[i - 1];
  const [z1, y1] = keys[i];
  const slope = (k: number) => {
    const a = keys[Math.max(0, k - 1)];
    const b = keys[Math.min(last, k + 1)];
    return (b[1] - a[1]) / (b[0] - a[0]);
  };
  const h = z1 - z0;
  const t = (z - z0) / h;
  const t2 = t * t;
  const t3 = t2 * t;
  return (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * h * slope(i - 1)
    + (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * h * slope(i);
}

const ease = (t: number): number => {
  const c = Math.min(1, Math.max(0, t));
  return c * c * (3 - 2 * c);
};
const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;

/** Half width of the body: a wide stance, rounded at the tail and the nose. */
const WIDTH: Keys = [[-2.4, 0.7], [-2.3, 0.8], [-2.1, 0.89], [-1.8, 0.94], [-1.3, 0.94], [1.3, 0.94], [1.8, 0.92], [2.1, 0.86], [2.3, 0.76], [2.4, 0.64]];
/** Height of the floor: lifted under the tail diffuser and the nose. */
const FLOOR: Keys = [[-2.4, 0.5], [-2.25, 0.4], [-2.0, 0.33], [-1.7, 0.3], [1.6, 0.3], [2.0, 0.33], [2.3, 0.38], [2.4, 0.42]];
/** Height of the hood and the boot lid: a high duck-tail, a long low hood. */
const DECK: Keys = [[-2.4, 0.84], [-2.25, 0.93], [-2.0, 0.985], [-1.6, 1.0], [-0.6, 0.995], [0.6, 0.97], [1.3, 0.915], [1.9, 0.8], [2.2, 0.7], [2.4, 0.58]];

/** How far the glasshouse has grown out of the deck: 0 on the hood and boot, 1 under the roof. */
function glasshouse(z: number): number {
  return ease((z + 1.55) / 1.05) * (1 - ease((z - 0.45) / 0.8));
}

/** Height of the roof along the car (a shallow arch). */
function roofAt(z: number): number {
  return ROOF_PEAK - 0.2 * (z + 0.1) * (z + 0.1);
}

/** Wheel arches lift the floor of the flanks around each axle. */
function archLift(z: number): number {
  let lift = 0;
  for (const axle of [FRONT_AXLE, REAR_AXLE]) {
    const u = Math.abs(z - axle) / 0.5;
    if (u < 1) {
      lift = Math.max(lift, 0.37 * Math.pow(0.5 + 0.5 * Math.cos(Math.PI * u), 0.6));
    }
  }
  return lift;
}

// Vertex order of every section: the floor's middle, up the right side, over the roof, down the left.
//   0 floor   1-6 right: rocker, lower flank, flank, upper flank, shoulder, deck edge
//   7-11 right: glass base, window, roof rail, roof edge, crown (11 is the middle of the roof)
//   12-15 left: roof edge, roof rail, window, glass base      16-21 left: deck edge .. rocker
const SIZE = 22;
const E = { lowerFlank: 2, flank: 3, glassLow: 7, glassHigh: 8, roofRight: 9, roofLeft: 12, glassHighLeft: 13, glassLowLeft: 14 } as const;

function section(z: number): Vec3[] {
  const w = curve(WIDTH, z);
  const floor = curve(FLOOR, z) + archLift(z);
  const deck = curve(DECK, z);
  const g = glasshouse(z);
  const roof = lerp(deck, roofAt(z), g);

  const side: Array<[number, number]> = [
    [w * 0.8, floor],
    [w * 0.97, floor + 0.1],
    [w, floor + 0.4 * (deck - floor)],
    [w, floor + 0.72 * (deck - floor)],
    [w * 0.965, deck - 0.05],
    [w * 0.88, deck - 0.012],
    [lerp(w * 0.74, w * 0.84, g), lerp(deck + 0.004, deck + 0.035, g)],
    [lerp(w * 0.56, w * 0.82, g), lerp(deck + 0.01, deck + 0.3 * (roof - deck), g)],
    [lerp(w * 0.34, w * 0.7, g), lerp(deck + 0.016, roof - 0.05, g)],
    [lerp(w * 0.15, w * 0.38, g), lerp(deck + 0.022, roof - 0.008, g)]
  ];
  const ring: Vec3[] = [[0, floor, z]];
  for (const [x, y] of side) {
    ring.push([x, y, z]);
  }
  ring.push([0, lerp(deck + 0.026, roof + 0.012, g), z]);
  for (let i = side.length - 1; i >= 0; i--) {
    ring.push([-side[i][0], side[i][1], z]);
  }
  return ring;
}

/** Sections along the car, denser where the surface turns quickly: the tail, the nose, the arches. */
function stations(): number[] {
  const zs = [REAR, -2.36, -2.29, -2.2, -2.08];
  for (let z = -1.92; z < 1.93; z += 0.16) {
    zs.push(Math.round(z * 100) / 100);
    // Twice as many where the roofline falls into the rear window and the windscreen.
    const ramp = (z > -1.6 && z < -0.5) || (z > 0.4 && z < 1.3);
    if (ramp) {
      zs.push(Math.round((z + 0.08) * 100) / 100);
    }
  }
  zs.push(2.08, 2.2, 2.29, 2.36, FRONT);
  return zs;
}

/** A face of the glasshouse whose two rings are fully grown (the side windows), or partly (the pillars). */
const GROWN = 0.995;

export function buildElectricFastback(): CarModel {
  const zs = stations();
  const rings = zs.map(section);
  const { solid: body, face, tail } = skin('body', rings);
  const growth = zs.map(glasshouse);

  // ---- surfaces: the glass roof runs into the rear window and the windscreen; windows on the flanks ----
  for (let i = 0; i < zs.length - 1; i++) {
    const fullyGrown = growth[i] > GROWN && growth[i + 1] > GROWN;
    const glassy = Math.max(growth[i], growth[i + 1]) > 0.02;
    if (glassy) {
      // Under the roof the glass panel keeps a border of body paint; into the rear window it spans the width.
      for (const edge of fullyGrown ? [10, 11] : [E.roofRight, 10, 11, E.roofLeft]) {
        const f = face(i, edge);
        f.part = 'roof-glass';
        f.gloss = 0.95;
      }
    }
    if (fullyGrown) {
      for (const edge of [E.glassLow, E.glassHigh, E.glassHighLeft, E.glassLowLeft]) {
        const f = face(i, edge);
        f.part = 'glass';
        f.gloss = 0.9;
      }
    }
  }

  // ---- the tail: dark band, a slim full-width light bar, indicators, plate and diffuser ----
  const z0 = REAR;
  const strip = (x0: number, x1: number, y0: number, y1: number): Vec3[] =>
    [[x0, y1, z0], [x1, y1 - 0.004, z0], [x1, y0 + 0.004, z0], [x0, y0, z0]];
  const lamps: Decal[] = [
    { pts: rectZ(-0.66, 0.66, 0.665, 0.795, z0), part: 'bumper' },
    // Unlit amber lenses under the indicators, so the tail looks complete while they are off.
    { pts: strip(-0.64, -0.46, 0.7, 0.775), part: 'lens' },
    { pts: strip(0.46, 0.64, 0.7, 0.775), part: 'lens' },
    { pts: strip(-0.64, -0.46, 0.7, 0.775), part: 'indicator', extra: 'indicator-light', group: 'indicator-left', slot: 0 },
    { pts: strip(0.46, 0.64, 0.7, 0.775), part: 'indicator', extra: 'indicator-light', group: 'indicator-right', slot: 0 },
    { pts: strip(-0.46, -0.03, 0.718, 0.762), part: 'tail', extra: 'brake-lamp' },
    { pts: strip(0.03, 0.46, 0.718, 0.762), part: 'tail', extra: 'brake-lamp' },
    { pts: rectZ(-0.03, 0.03, 0.728, 0.752, z0), part: 'tail', extra: 'brake-lamp' },
    { pts: rectZ(-0.21, 0.21, 0.545, 0.625, z0), part: 'plate' },
    { pts: rectZ(-0.5, 0.5, 0.47, 0.515, z0), part: 'bumper' }
  ];
  tail.decals.push(...lamps);

  // ---- flank repeaters, just behind the front arch ----
  const repeater = zs.findIndex((z) => z >= 1.44);
  for (const [side, edge] of [['left', 18], ['right', E.flank]] as const) {
    face(repeater, edge).decals.push({
      pts: inset(face(repeater, edge).pts, 0.6), part: 'indicator', extra: 'indicator-light',
      group: `indicator-${side}`, slot: 1
    });
  }

  // ---- door mirrors, each with its own indicator ----
  const solids = [body];
  for (const side of [-1, 1]) {
    const inner = side * 0.86;
    const outer = side * 1.05;
    const mirror = box('body', Math.min(inner, outer), Math.max(inner, outer), 0.93, 0.99, 0.5, 0.76);
    mirror.smooth = true;
    faceOf(mirror, 'rear').decals.push({
      pts: rectZ(Math.min(inner, outer) + 0.02, Math.max(inner, outer) - 0.02, 0.94, 0.965, 0.5),
      part: 'indicator', extra: 'indicator-light', group: side < 0 ? 'indicator-left' : 'indicator-right', slot: 2
    });
    solids.push(mirror);
  }

  // ---- aero wheels: a dark tyre, a flat bright cover and a hub ----
  const wheels = [
    { cx: -0.82, cz: FRONT_AXLE, front: true },
    { cx: 0.82, cz: FRONT_AXLE, front: true },
    { cx: -0.82, cz: REAR_AXLE, front: false },
    { cx: 0.82, cz: REAR_AXLE, front: false }
  ].map((w) => {
    const solid = wheelSolid('wheel', WHEEL_RADIUS, -WHEEL_HALF_WIDTH, WHEEL_HALF_WIDTH, 16);
    for (const role of ['left', 'right'] as const) {
      const f = faceOf(solid, role);
      f.decals.push({ pts: inset(f.pts, 0.74), part: 'rim', gloss: 0.7 }, { pts: inset(f.pts, 0.2), part: 'hub', gloss: 0.6 });
    }
    return { ...w, solid };
  });

  return { wheels, body: solids, length: LENGTH, width: 1.9, height: ROOF_PEAK };
}

export const EGO_CAR: CarModel = buildElectricFastback();
