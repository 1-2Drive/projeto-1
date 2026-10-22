import {
  CAMERA_BACK_METRES, CAMERA_MAX_EYE_METRES, CAMERA_MIN_EYE_METRES, createCamera, ground, groundMatrix, groundQuad,
  isInFront, LANE_CENTRE_METRES, pathOf, pixelsPerMetre, project, SHOULDER_HALF_METRES, spriteFrame, VIEW_AHEAD_METRES
} from './road-projection';

const VIEWPORTS = [
  { name: '1920x1080 page', width: 1296, height: 355 },
  { name: '1600x900 page', width: 1024, height: 257 },
  { name: '1440x900 page', width: 909, height: 257 },
  { name: '1366x768 page', width: 874, height: 492 },
  { name: '1280x720 page', width: 792, height: 446 }
];

describe('road projection', () => {
  const camera = createCamera({ width: 1000, height: 500 }, 0);

  it('puts the ego contact point at its anchor and the vanishing point at the horizontal centre', () => {
    const ego = project(camera, 0, 0, 0)!;
    expect(ego.x).toBeCloseTo(camera.cx, 6);
    expect(ego.y).toBeCloseTo(camera.egoBaseY, 6);
    expect(ego.z).toBeCloseTo(CAMERA_BACK_METRES, 6);

    const far = ground(camera, 4, 100000)!;
    expect(far.x).toBeCloseTo(camera.cx, 1);
    expect(far.y).toBeCloseTo(camera.horizonY, 1);
  });

  it('keeps LEFT left of CENTER left of RIGHT at every distance', () => {
    for (const ahead of [-8, 0, 5, 20, 60, 150, 220]) {
      const left = ground(camera, LANE_CENTRE_METRES.LEFT, ahead)!;
      const centre = ground(camera, LANE_CENTRE_METRES.CENTER, ahead)!;
      const right = ground(camera, LANE_CENTRE_METRES.RIGHT, ahead)!;
      expect(left.x).toBeLessThan(centre.x);
      expect(centre.x).toBeLessThan(right.x);
      // Same longitudinal position, same screen row: depth never depends on the lane.
      expect(left.y).toBeCloseTo(right.y, 9);
    }
  });

  it('follows the lateral coordinate linearly at a given distance (a lane change slides smoothly)', () => {
    const xs = [-3.5, -1.75, 0, 1.75, 3.5].map((lateral) => ground(camera, lateral, 30)!.x);
    const steps = xs.slice(1).map((x, i) => x - xs[i]);
    for (const step of steps) {
      expect(step).toBeCloseTo(steps[0], 6);
    }
  });

  it('makes the road narrower, higher on screen and smaller the farther away', () => {
    let previous = ground(camera, 0, 0)!;
    let previousScale = pixelsPerMetre(camera, 0);
    let previousWidth = Infinity;
    for (const ahead of [10, 25, 50, 100, 200]) {
      const point = ground(camera, 0, ahead)!;
      const scale = pixelsPerMetre(camera, ahead);
      const width = ground(camera, SHOULDER_HALF_METRES, ahead)!.x - ground(camera, -SHOULDER_HALF_METRES, ahead)!.x;
      expect(point.y).toBeLessThan(previous.y);
      expect(point.y).toBeGreaterThan(camera.horizonY);
      expect(scale).toBeLessThan(previousScale);
      expect(width).toBeLessThan(previousWidth);
      previous = point;
      previousScale = scale;
      previousWidth = width;
    }
  });

  it('scales with the inverse of the camera depth', () => {
    for (const ahead of [4, 20, 80]) {
      const ratio = pixelsPerMetre(camera, 0) / pixelsPerMetre(camera, ahead);
      expect(ratio).toBeCloseTo((ahead + CAMERA_BACK_METRES) / CAMERA_BACK_METRES, 9);
    }
  });

  it('converges lane lines on the vanishing point', () => {
    const edge = (lateral: number, ahead: number) => Math.abs(ground(camera, lateral, ahead)!.x - camera.cx);
    for (const lateral of [-5.25, -1.75, 1.75, 5.25]) {
      expect(edge(lateral, 200)).toBeLessThan(edge(lateral, 50));
      expect(edge(lateral, 50)).toBeLessThan(edge(lateral, 5));
    }
  });

  it('draws nothing closer than the near clip plane', () => {
    expect(project(camera, 0, 0, -CAMERA_BACK_METRES)).toBeNull();
    expect(isInFront(camera, camera.nearAhead - 1)).toBeFalse();
    expect(isInFront(camera, camera.nearAhead)).toBeTrue();
    expect(pixelsPerMetre(camera, -CAMERA_BACK_METRES)).toBe(0);
  });

  it('slides the whole scene a little with the ego lateral position', () => {
    const rightLane = createCamera({ width: 1000, height: 500 }, 3.5);
    expect(rightLane.x).toBeGreaterThan(0);
    // The ego itself is then drawn right of centre, but less than the full lane offset.
    const ego = project(rightLane, 3.5, 0, 0)!;
    expect(ego.x).toBeGreaterThan(rightLane.cx);
    expect(ego.x - rightLane.cx).toBeLessThan(3.5 * rightLane.focal / rightLane.back);
  });

  it('clips ground quads to the visible span instead of folding them behind the camera', () => {
    const quad = groundQuad(camera, -1, 1, -500, 1000)!;
    expect(quad.length).toBe(4);
    const nearest = Math.max(...quad.map((p) => p.y));
    const farthest = Math.min(...quad.map((p) => p.y));
    expect(Number.isFinite(nearest)).toBeTrue();
    expect(farthest).toBeGreaterThan(camera.horizonY);
    expect(groundQuad(camera, -1, 1, VIEW_AHEAD_METRES + 5, VIEW_AHEAD_METRES + 9)).toBeNull();
    expect(pathOf(quad)).toMatch(/^M[\d. -]+L[\d. -]+L[\d. -]+L[\d. -]+Z$/);
  });

  it('lays paint flat with a foreshortened vertical axis', () => {
    const near = groundMatrix(camera, 0, 10)!;
    const far = groundMatrix(camera, 0, 100)!;
    const values = (matrix: string) => matrix.slice(7, -1).split(' ').map(Number);
    const [, , , dNear] = values(near);
    const [, , , dFar] = values(far);
    expect(dNear).toBeGreaterThan(dFar * 10);
    // Painting across the road keeps the horizontal metre scale of the perspective.
    expect(values(near)[0]).toBeCloseTo(pixelsPerMetre(camera, 10), 2);
  });

  it('floors the size of billboards without moving them', () => {
    const real = spriteFrame(camera, 0, 150)!;
    const floored = spriteFrame(camera, 0, 150, 9)!;
    expect(real.scale).toBeLessThan(9);
    expect(floored.scale).toBe(9);
    expect(floored.x).toBe(real.x);
    expect(floored.y).toBe(real.y);
    expect(spriteFrame(camera, 0, 20)!.scale).toBeCloseTo(pixelsPerMetre(camera, 20), 3);
  });

  describe('responsive camera', () => {
    for (const view of VIEWPORTS) {
      it(`keeps the ego, the horizon and the road inside a ${view.width}x${view.height} canvas (${view.name})`, () => {
        const cam = createCamera(view, 0);
        const ego = project(cam, 0, 0, 0)!;
        expect(ego.y).toBeGreaterThan(cam.horizonY + view.height * 0.3);
        expect(ego.y).toBeLessThan(view.height * 0.8);

        // Asphalt plus shoulders fill a sensible share of the width at the ego, never overflowing it.
        const left = project(cam, -SHOULDER_HALF_METRES, 0, 0)!.x;
        const right = project(cam, SHOULDER_HALF_METRES, 0, 0)!.x;
        const share = (right - left) / view.width;
        expect(share).toBeGreaterThan(0.3);
        expect(share).toBeLessThan(0.95);

        // The car keeps the same proportions: 1.94 m wide at the pixel scale of its own depth.
        const carWidth = 1.94 * pixelsPerMetre(cam, 0);
        const carLength = project(cam, 0, 0, -2.3)!.y - project(cam, 0, 0, 2.3)!.y;
        expect(carWidth).toBeGreaterThan(view.height * 0.12);
        expect(carLength).toBeGreaterThan(0);

        expect(cam.eye).toBeGreaterThanOrEqual(CAMERA_MIN_EYE_METRES);
        expect(cam.eye).toBeLessThanOrEqual(CAMERA_MAX_EYE_METRES);
      });
    }

    it('survives a degenerate canvas', () => {
      const cam = createCamera({ width: 0, height: 0 }, Number.NaN);
      expect(Number.isFinite(cam.focal)).toBeTrue();
      expect(cam.x).toBe(0);
    });
  });
});
