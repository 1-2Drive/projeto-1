import { createCamera, pixelsPerMetre, project } from './road-projection';
import { box, EGO_CAR, extrude, FaceView, renderCar, renderSolid, TRAFFIC_CAR, translate } from './road-solids';

/** Bounding box of every path in a list of faces. */
function bounds(faces: FaceView[]) {
  const xs: number[] = [];
  const ys: number[] = [];
  for (const face of faces) {
    for (const match of face.d.matchAll(/[ML]([\d.-]+) ([\d.-]+)/g)) {
      xs.push(Number(match[1]));
      ys.push(Number(match[2]));
    }
  }
  return { left: Math.min(...xs), right: Math.max(...xs), top: Math.min(...ys), bottom: Math.max(...ys) };
}

describe('road solids', () => {
  const camera = createCamera({ width: 1000, height: 500 }, 0);

  describe('back-face culling', () => {
    it('shows the rear and the top of a box ahead of the camera, never its front', () => {
      const crate = box('obstacle', -1, 1, 0, 1, -1, 1);
      const roles = renderSolid(camera, crate, translate(0, 0, 30)).faces.map((f) => f.cls);
      expect(roles.some((c) => c.includes('f--rear'))).toBeTrue();
      expect(roles.some((c) => c.includes('f--top'))).toBeTrue();
      expect(roles.some((c) => c.includes('f--front'))).toBeFalse();
      // Straight ahead of the camera neither side is visible.
      expect(roles.some((c) => c.includes('f--left') || c.includes('f--right'))).toBeFalse();
    });

    it('shows the inner side of a box that sits to the side of the camera', () => {
      const crate = box('obstacle', -1, 1, 0, 1, -1, 1);
      const onTheRight = renderSolid(camera, crate, translate(6, 0, 20)).faces.map((f) => f.cls).join(' ');
      const onTheLeft = renderSolid(camera, crate, translate(-6, 0, 20)).faces.map((f) => f.cls).join(' ');
      expect(onTheRight).toContain('f--left');
      expect(onTheRight).not.toContain('f--right');
      expect(onTheLeft).toContain('f--right');
      expect(onTheLeft).not.toContain('f--left');
    });

    it('orients extruded prisms outwards whatever their winding', () => {
      const bar = extrude('arm-red', [[0, 0], [2, 0.3], [2, 0.5], [0, 0.2]], -0.1, 0.1);
      const faces = renderSolid(camera, bar, translate(0, 1, 20)).faces;
      expect(faces.length).toBeGreaterThanOrEqual(2);
    });

    it('does not draw anything closer than the near clip plane', () => {
      const crate = box('obstacle', -1, 1, 0, 1, -1, 1);
      expect(renderSolid(camera, crate, translate(0, 0, camera.nearAhead - 2)).faces.length).toBe(0);
    });
  });

  describe('cars', () => {
    it('has two front and two rear wheels, a body, and three indicator lamps per side on the ego', () => {
      const ego = renderCar(camera, EGO_CAR, { lateral: 0, ahead: 0, yawDegrees: 0, steerDegrees: 0 });
      expect(ego.wheels.filter((w) => w.cls.includes('front-wheel')).length).toBe(2);
      expect(ego.wheels.filter((w) => w.cls.includes('rear-wheel')).length).toBe(2);
      expect(ego.faces.length).toBeGreaterThan(8);
      expect(ego.indicatorLeft.length).toBe(3);
      expect(ego.indicatorRight.length).toBe(3);
      expect(ego.lamps).not.toBeNull();
    });

    it('is seen from behind: rear face, roof and rear window, with the tail lamps on the rear face', () => {
      const ego = renderCar(camera, EGO_CAR, { lateral: 0, ahead: 0, yawDegrees: 0, steerDegrees: 0 });
      const classes = ego.faces.map((f) => f.cls).join(' ');
      expect(classes).toContain('f--tail');
      expect(classes).toContain('f--glass');
      expect(classes).toContain('f--roof');
      expect(classes).toContain('f--plate');
      expect(classes).not.toContain('f--front');
    });

    it('shrinks in proportion to depth and stays centred on its lane', () => {
      const near = renderCar(camera, TRAFFIC_CAR, { lateral: 0, ahead: 10, yawDegrees: 0, steerDegrees: 0 });
      const far = renderCar(camera, TRAFFIC_CAR, { lateral: 0, ahead: 90, yawDegrees: 0, steerDegrees: 0 });
      const nearBox = bounds(near.faces);
      const farBox = bounds(far.faces);
      const nearWidth = nearBox.right - nearBox.left;
      const farWidth = farBox.right - farBox.left;
      expect(farWidth).toBeLessThan(nearWidth);
      expect(nearWidth / farWidth).toBeGreaterThan(2.5);
      // Roughly the ratio of the pixel scales at the two distances.
      const expected = pixelsPerMetre(camera, 10) / pixelsPerMetre(camera, 90);
      expect(nearWidth / farWidth).toBeGreaterThan(expected * 0.6);
      expect(nearWidth / farWidth).toBeLessThan(expected * 1.4);
      expect((nearBox.left + nearBox.right) / 2).toBeCloseTo(camera.cx, 0);
      expect((farBox.left + farBox.right) / 2).toBeCloseTo(camera.cx, 0);
      expect(far.z).toBeGreaterThan(near.z);
    });

    it('puts each lane on its own side of the screen', () => {
      const centres = (['LEFT', 'CENTER', 'RIGHT'] as const).map((lane) => {
        const lateral = { LEFT: -3.5, CENTER: 0, RIGHT: 3.5 }[lane];
        const car = renderCar(camera, TRAFFIC_CAR, { lateral, ahead: 40, yawDegrees: 0, steerDegrees: 0 });
        const b = bounds(car.faces);
        return (b.left + b.right) / 2;
      });
      expect(centres[0]).toBeLessThan(centres[1]);
      expect(centres[1]).toBeLessThan(centres[2]);
    });

    it('yaws the nose towards the lane being entered and reveals the flank on that side', () => {
      // The flank repeater is the second lamp of its side: it is only drawn while its flank faces the camera.
      const flanks = (yaw: number) => {
        const car = renderCar(camera, TRAFFIC_CAR, { lateral: 0, ahead: 20, yawDegrees: yaw, steerDegrees: 0 });
        return { left: car.indicatorLeft[1].d !== '', right: car.indicatorRight[1].d !== '' };
      };
      expect(flanks(0)).toEqual({ left: false, right: false });
      // The flank on the side the nose turns to swings round towards the camera behind the car.
      expect(flanks(12)).toEqual({ left: false, right: true });
      expect(flanks(-12)).toEqual({ left: true, right: false });
    });

    it('keeps lamps in stable slots even when their face is hidden', () => {
      const away = renderCar(camera, EGO_CAR, { lateral: 0, ahead: 0, yawDegrees: 0, steerDegrees: 0 });
      // The flank repeaters face away from a camera straight behind: present but not drawn.
      expect(away.indicatorLeft.some((lamp) => lamp.d === '')).toBeTrue();
      expect(away.indicatorLeft.some((lamp) => lamp.d !== '')).toBeTrue();
    });

    it('draws the ego and other traffic from different models', () => {
      expect(EGO_CAR).not.toBe(TRAFFIC_CAR);
      expect(EGO_CAR.height).not.toBe(TRAFFIC_CAR.height);
    });

    it('stays on the road surface: the wheels touch the contact point of the car', () => {
      const car = renderCar(camera, TRAFFIC_CAR, { lateral: 0, ahead: 30, yawDegrees: 0, steerDegrees: 0 });
      const contact = project(camera, 0, 0, 30 - TRAFFIC_CAR.length / 2 + 0.85 - 0.33)!;
      const wheels = bounds(car.wheels.flatMap((w) => w.faces));
      expect(wheels.bottom).toBeGreaterThanOrEqual(contact.y - 2);
    });
  });
});
