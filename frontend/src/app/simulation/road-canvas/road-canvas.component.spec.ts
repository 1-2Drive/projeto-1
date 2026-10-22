import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RoadCanvasComponent } from './road-canvas.component';

const RANGES = { cameraRangeMeters: 120, radarRangeMeters: 130, blindSpotRangeMeters: 30, cameraRateMillis: 200, radarRateMillis: 100 };

/** Reads x and y out of `translate(x y) scale(s)`. */
function translation(el: Element): { x: number; y: number; scale: number } {
  const match = /translate\(([\d.-]+) ([\d.-]+)\) scale\(([\d.]+)\)/.exec(el.getAttribute('transform') ?? '');
  if (!match) {
    throw new Error(`no translate() in ${el.getAttribute('transform')}`);
  }
  return { x: Number(match[1]), y: Number(match[2]), scale: Number(match[3]) };
}

/** x, y of every vertex of a path, in drawing order. */
function vertices(path: Element): { x: number; y: number }[] {
  return Array.from((path.getAttribute('d') ?? '').matchAll(/[ML]([\d.-]+) ([\d.-]+)/g))
    .map((m) => ({ x: Number(m[1]), y: Number(m[2]) }));
}

describe('RoadCanvasComponent', () => {
  let fixture: ComponentFixture<RoadCanvasComponent>;
  let root: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoadCanvasComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(RoadCanvasComponent);
    root = fixture.nativeElement as HTMLElement;
    // Snapshots are drawn as received, so assertions see the state they just set.
    fixture.componentRef.setInput('animate', false);
  });

  function snapshotWith(options: {
    ego?: Record<string, unknown>;
    objects?: unknown[];
    sensors?: Record<string, unknown>;
    environment?: Record<string, unknown>;
    cameraIds?: string[];
    radarIds?: string[];
    status?: string;
    time?: number;
  } = {}) {
    return {
      simulationTimeMillis: options.time ?? 1000,
      status: options.status ?? 'RUNNING',
      egoVehicle: { speedKmh: 50, positionMeters: 10, lane: 'RIGHT', lateralPositionMeters: 3.5, accelerationMps2: 0, ...options.ego },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1, ...options.environment },
      sensors: options.sensors ?? {},
      worldObjects: options.objects ?? [],
      detectedObjectIds: [...(options.cameraIds ?? []), ...(options.radarIds ?? [])],
      cameraDetectedObjectIds: options.cameraIds ?? [],
      radarDetectedObjectIds: options.radarIds ?? [],
      sensorRanges: RANGES
    } as never;
  }

  function show(options: Parameters<typeof snapshotWith>[0] = {}) {
    fixture.componentRef.setInput('snapshot', snapshotWith(options));
    fixture.detectChanges();
  }

  const q = (selector: string) => root.querySelector(selector) as HTMLElement | null;
  const qa = (selector: string) => Array.from(root.querySelectorAll(selector)) as HTMLElement[];

  /** Gives the host a real size and waits for the ResizeObserver to hand it to the camera. */
  async function resizeTo(width: number, height: number) {
    root.style.cssText = `display:block;width:${width}px;height:${height}px`;
    for (let i = 0; i < 20; i++) {
      await new Promise((resolve) => setTimeout(resolve, 30));
      fixture.detectChanges();
      const box = q('[data-testid="road-scene"]')!.getAttribute('viewBox')!.split(' ').map(Number);
      if (Math.abs(box[2] - width) < 4 && Math.abs(box[3] - height) < 4) {
        return;
      }
    }
    throw new Error(`the road view did not adopt ${width}x${height}`);
  }

  it('should render world items from snapshot coordinates', () => {
    show({
      ego: { positionMeters: 42, lane: 'CENTER', lateralPositionMeters: 0 },
      objects: [
        { id: 'a', type: 'SpeedLimitSign', positionMeters: 80, lane: 'CENTER', details: 'limitKmh=50' },
        { id: 'b', type: 'FrontVehicleActor', positionMeters: 35, lane: 'CENTER', details: 'speed=22' }
      ],
      cameraIds: ['a']
    });

    const text = root.textContent;
    expect(text).toContain('50');
    expect(text).toContain('35 m');
  });

  it('draws the 3D scene as one SVG in canvas pixels', () => {
    show();
    const scene = q('[data-testid="road-scene"]')!;
    expect(scene.tagName.toLowerCase()).toBe('svg');
    expect(scene.getAttribute('viewBox')).toMatch(/^0 0 [\d.]+ [\d.]+$/);
    expect(q('.surface__asphalt')!.getAttribute('d')).toMatch(/^M/);
    expect(q('.sky')).toBeTruthy();
    expect(q('.ground')).toBeTruthy();
  });

  describe('perspective', () => {
    it('converges the lane lines and road edges on the horizon', () => {
      show({ ego: { lane: 'CENTER', lateralPositionMeters: 0 } });
      const asphalt = vertices(q('.surface__asphalt')!);
      // Quad from near-left, near-right, far-right, far-left: the far edge is narrower and higher.
      const nearWidth = asphalt[1].x - asphalt[0].x;
      const farWidth = asphalt[2].x - asphalt[3].x;
      expect(farWidth).toBeLessThan(nearWidth / 4);
      expect(asphalt[3].y).toBeLessThan(asphalt[0].y);
      const horizon = Number(q('.ground')!.getAttribute('y'));
      expect(asphalt[3].y).toBeGreaterThan(horizon);
    });

    it('draws the road markings that the backend reports', () => {
      show();
      expect(q('.surface__lane--1')!.classList.contains('surface__lane--dashed')).toBeTrue();
      fixture.componentRef.setInput('snapshot', { ...(snapshotWith() as object), roadMarks: { leftMarking: 'CONTINUOUS', rightMarking: 'DASHED' } });
      fixture.detectChanges();
      expect(q('.surface__lane--1')!.classList.contains('surface__lane--continuous')).toBeTrue();
      expect(q('.surface__lane--2')!.classList.contains('surface__lane--dashed')).toBeTrue();
    });

    it('scrolls the world-anchored markings with the ego position', () => {
      show({ ego: { positionMeters: 10 } });
      const before = q('.surface__lane--1')!.getAttribute('d');
      show({ ego: { positionMeters: 13 } });
      expect(q('.surface__lane--1')!.getAttribute('d')).not.toBe(before);
      // A whole dash period later the dashes are back where they started.
      show({ ego: { positionMeters: 26 } });
      expect(q('.surface__lane--1')!.getAttribute('d')).toBe(before);
    });
  });

  describe('lanes', () => {
    function vehicleCentres(ahead: number) {
      return (['LEFT', 'CENTER', 'RIGHT'] as const).map((lane) => {
        show({
          ego: { positionMeters: 10, lane: 'CENTER', lateralPositionMeters: 0 },
          objects: [{ id: `v-${lane}`, type: 'OtherVehicle', positionMeters: ahead, lane, details: 'speedMps=10' }]
        });
        const car = q(`[data-object-id="v-${lane}"] .car--other`) as unknown as SVGGraphicsElement;
        const box = car.getBBox();
        return { lane, x: box.x + box.width / 2, attr: q(`[data-object-id="v-${lane}"]`)!.getAttribute('data-lane') };
      });
    }

    it('keeps LEFT, CENTER and RIGHT in that order from left to right, at any distance', () => {
      for (const ahead of [15, 45, 110]) {
        const [left, centre, right] = vehicleCentres(ahead);
        expect([left.attr, centre.attr, right.attr]).toEqual(['LEFT', 'CENTER', 'RIGHT']);
        expect(left.x).toBeLessThan(centre.x);
        expect(centre.x).toBeLessThan(right.x);
      }
    });

    it('draws the lane the backend reports, not a lane derived from the picture', () => {
      show({ objects: [{ id: 'p', type: 'Pothole', positionMeters: 40, lane: 'LEFT', details: 'severity=HIGH' }] });
      expect(q('[data-testid="road-pothole"]')!.getAttribute('data-lane')).toBe('LEFT');
      expect(q('[data-testid="road-pothole"]')!.getAttribute('data-lateral')).toBe('-3.50');
    });

    it('follows the backend lateral position through a lane change', () => {
      const xs = [3.5, 2.5, 1.5, 0.5, 0].map((lateral) => {
        show({ ego: { lane: 'RIGHT', lateralPositionMeters: lateral, laneChangeState: 'CHANGING' } });
        return q('[data-testid="road-ego"]')!.getAttribute('data-lateral');
      });
      expect(xs).toEqual(['3.50', '2.50', '1.50', '0.50', '0.00']);
    });

    it('slides the ego across the road with its lateral position, with no jump between the endpoints', () => {
      const egoX = (lateral: number) => {
        show({ ego: { lane: 'RIGHT', lateralPositionMeters: lateral } });
        const lamps = vertices(q('[data-testid="road-ego"] .f--plate')!);
        return lamps.reduce((sum, p) => sum + p.x, 0) / lamps.length;
      };
      const path = [3.5, 3, 2, 1, 0, -1.2].map(egoX);
      for (let i = 1; i < path.length; i++) {
        expect(path[i]).toBeLessThan(path[i - 1]);
      }
    });
  });

  describe('depth', () => {
    it('draws a vehicle smaller and higher on the screen the farther away it is', () => {
      const probe = (ahead: number) => {
        show({ ego: { lane: 'CENTER', lateralPositionMeters: 0 }, objects: [{ id: 'v', type: 'OtherVehicle', positionMeters: ahead, lane: 'CENTER', details: '' }] });
        const car = q('[data-object-id="v"] .car--other') as unknown as SVGGraphicsElement;
        const box = car.getBBox();
        return { width: box.width, bottom: box.y + box.height, depth: Number(q('[data-object-id="v"]')!.getAttribute('data-depth')) };
      };
      const near = probe(15);
      const mid = probe(50);
      const far = probe(120);
      expect(near.width).toBeGreaterThan(mid.width);
      expect(mid.width).toBeGreaterThan(far.width);
      expect(near.bottom).toBeGreaterThan(mid.bottom);
      expect(mid.bottom).toBeGreaterThan(far.bottom);
      expect(near.depth).toBeLessThan(mid.depth);
      expect(mid.depth).toBeLessThan(far.depth);
      // Pinhole scaling: width is inversely proportional to camera depth (the car has some length of its own).
      const nearProduct = near.width * near.depth;
      const farProduct = far.width * far.depth;
      expect(nearProduct / farProduct).toBeGreaterThan(0.85);
      expect(nearProduct / farProduct).toBeLessThan(1.15);
    });

    it('sorts things far to near, so a nearer vehicle is drawn over a farther one', () => {
      show({
        ego: { lane: 'CENTER', lateralPositionMeters: 0 },
        objects: [
          { id: 'near', type: 'OtherVehicle', positionMeters: 20, lane: 'CENTER', details: '' },
          { id: 'far', type: 'OtherVehicle', positionMeters: 80, lane: 'CENTER', details: '' }
        ]
      });
      const ids = qa('[data-object-id]').map((el) => el.getAttribute('data-object-id'));
      expect(ids.indexOf('far')).toBeLessThan(ids.indexOf('near'));
      const order = qa('[data-testid="road-ego"], [data-testid="road-vehicle"]').map((el) => el.getAttribute('data-testid'));
      expect(order[order.length - 1]).toBe('road-ego');
    });
  });

  describe('road objects by type', () => {
    it('draws each kind with its own 3D asset in the lane the backend gives it', () => {
      show({
        ego: { positionMeters: 0, lane: 'CENTER', lateralPositionMeters: 0 },
        objects: [
          { id: 'ph', type: 'Pothole', positionMeters: 30, lane: 'RIGHT', details: 'severity=HIGH' },
          { id: 'ob', type: 'StaticObstacle', positionMeters: 40, lane: 'LEFT', details: 'severity=HIGH' },
          { id: 'ba', type: 'Barrier', positionMeters: 50, lane: 'CENTER', details: 'Barrier{open=false}' },
          { id: 'pe', type: 'Pedestrian', positionMeters: 60, lane: 'LEFT', details: '' },
          { id: 'ss', type: 'StopSign', positionMeters: 70, lane: 'RIGHT', details: '' },
          { id: 'tl', type: 'TrafficLight', positionMeters: 80, lane: 'CENTER', details: 'TrafficLight state=GREEN' },
          { id: 'cw', type: 'Crosswalk', positionMeters: 90, lane: 'ALL', details: '', worldPositionMeters: 90 },
          { id: 've', type: 'OtherVehicle', positionMeters: 100, lane: 'RIGHT', details: 'speedMps=9' }
        ]
      });

      expect(q('[data-testid="road-pothole"] .pothole')).toBeTruthy();
      expect(q('[data-testid="road-obstacle"] .obstacle path')).toBeTruthy();
      expect(q('[data-testid="road-barrier"] .barrier path')).toBeTruthy();
      expect(q('[data-testid="road-barrier"] .barrier')!.classList.contains('barrier--open')).toBeFalse();
      expect(q('[data-testid="road-pedestrian"] .person')).toBeTruthy();
      expect(q('[data-testid="road-stop-sign"] .sign--stop')).toBeTruthy();
      expect(q('.gantry [data-testid="road-traffic-light"]')).toBeTruthy();
      expect(q('[data-testid="crosswalk"] .crosswalk__bars')).toBeTruthy();
      expect(q('[data-testid="road-vehicle"] .car--other')).toBeTruthy();

      const lanes: Record<string, string> = { ph: 'RIGHT', ob: 'LEFT', ba: 'CENTER', pe: 'LEFT', ss: 'RIGHT', ve: 'RIGHT' };
      for (const [id, lane] of Object.entries(lanes)) {
        expect(q(`[data-object-id="${id}"]`)!.getAttribute('data-lane')).toBe(lane, id);
      }
    });

    it('stands a barrier across its lane and raises the boom when the backend reports it open', () => {
      const probe = (open: boolean) => {
        show({ objects: [{ id: 'b', type: 'Barrier', positionMeters: 30, lane: 'CENTER', details: `Barrier{open=${open}}` }] });
        const barrier = q('[data-testid="road-barrier"]')!;
        const bounds = (barrier.querySelector('.barrier') as unknown as SVGGraphicsElement).getBBox();
        return { open: barrier.getAttribute('data-state'), width: bounds.width, height: bounds.height };
      };
      const closed = probe(false);
      const open = probe(true);
      expect(closed.open).toBe('CLOSED');
      expect(open.open).toBe('OPEN');
      expect(open.height).toBeGreaterThan(closed.height);
      // The posts at both ends keep the footprint of the gate the same, open or closed.
      expect(closed.width).toBeGreaterThanOrEqual(open.width);
    });

    it('draws an obstacle as a solid with volume (a front face and a top face)', () => {
      show({ objects: [{ id: 'o', type: 'StaticObstacle', positionMeters: 30, lane: 'CENTER', details: '' }] });
      const classes = qa('[data-testid="road-obstacle"] path').map((p) => p.getAttribute('class')).join(' ');
      expect(classes).toContain('f--top');
      expect(classes).toContain('f--rear');
    });

    it('draws a pothole on the road surface: flat, not standing', () => {
      show({ objects: [{ id: 'p', type: 'Pothole', positionMeters: 25, lane: 'CENTER', details: '' }] });
      const rings = qa('[data-testid="road-pothole"] path').filter((p) => p.getAttribute('class')?.startsWith('pothole'));
      expect(rings.length).toBe(3);
      const outer = vertices(rings[0]);
      const height = Math.max(...outer.map((p) => p.y)) - Math.min(...outer.map((p) => p.y));
      const width = Math.max(...outer.map((p) => p.x)) - Math.min(...outer.map((p) => p.x));
      expect(height).toBeLessThan(width / 2);
    });

    it('draws a pedestrian upright, with a crosswalk painted flat under it', () => {
      show({
        ego: { positionMeters: 0 },
        objects: [
          { id: 'cw', type: 'Crosswalk', positionMeters: 40, lane: 'ALL', details: '', worldPositionMeters: 40 },
          { id: 'pe', type: 'Pedestrian', positionMeters: 40, lane: 'LEFT', details: '', worldPositionMeters: 40, lateralPositionMeters: -1 }
        ]
      });
      const person = q('[data-testid="road-pedestrian"] .person') as unknown as SVGGraphicsElement;
      const box = person.getBBox();
      expect(box.height).toBeGreaterThan(box.width * 2);
      expect(q('[data-testid="crosswalk"]')).toBeTruthy();
    });

    it('lights the lamp that matches the backend traffic-light state', () => {
      for (const [state, lit] of [['RED', 'lamp-red'], ['YELLOW', 'lamp-yellow'], ['GREEN', 'lamp-green']]) {
        show({ objects: [{ id: 'tl', type: 'TrafficLight', positionMeters: 60, lane: 'CENTER', details: `TrafficLight state=${state}` }] });
        const main = q('[data-testid="road-traffic-light"]')!;
        const lamps = Array.from(main.querySelectorAll('.lamp')).map((p) => p.getAttribute('class')!);
        expect(lamps.filter((c) => c.includes(lit)).length).toBe(1, state);
        expect(lamps.filter((c) => c.includes('lamp-off')).length).toBe(2, state);
        expect(main.querySelector('.glow')!.getAttribute('class')).toContain(`glow--${state.toLowerCase()}`);
      }
    });

    it('keeps the speed radar as fixed roadside infrastructure, never as a vehicle', () => {
      show({
        ego: { lane: 'CENTER', lateralPositionMeters: 0 },
        objects: [
          { id: 'radar-1', type: 'SpeedRadar', positionMeters: 70, lane: 'CENTER', details: '' },
          { id: 'car-1', type: 'OtherVehicle', positionMeters: 70, lane: 'CENTER', details: 'speedMps=10' }
        ]
      });
      const radar = q('[data-testid="road-radar"]')!;
      expect(radar).toBeTruthy();
      expect(radar.getAttribute('data-object-kind')).toBe('radar');
      expect(radar.querySelector('.radar-unit')).toBeTruthy();
      expect(radar.querySelector('.car')).toBeNull();
      expect(q('[data-testid="road-vehicle"]')!.querySelector('.radar-unit')).toBeNull();
      // On the shoulder at the right of the road, whatever lane its map cell is in.
      const unit = translation(radar.querySelector('.radar-unit')!);
      const centre = vertices(q('.surface__asphalt')!);
      expect(unit.x).toBeGreaterThan((centre[0].x + centre[1].x) / 2);
    });
  });

  describe('road-wide elements', () => {
    it('paints one speed limit on the pavement of all three lanes at the same road position', () => {
      for (const limit of [30, 90]) {
        show({
          ego: { speedKmh: 60, lane: 'RIGHT' },
          objects: [{ id: 'r20-CENTER', type: 'SpeedLimitSign', positionMeters: 90, lane: 'CENTER', details: `limitKmh=${limit}` }]
        });

        expect(qa('[data-testid="road-speed-limit"]').length).toBe(1, 'one logical speed limit');
        const marks = qa('.limit-mark');
        expect(marks.length).toBe(3);
        expect(marks.map((mark) => mark.textContent?.trim())).toEqual([`${limit}`, `${limit}`, `${limit}`]);

        const holders = marks.map((mark) => mark.parentElement as HTMLElement);
        expect(new Set(holders.map((holder) => holder.getAttribute('data-depth'))).size).toBe(1, 'same longitudinal position');
        expect(holders.map((holder) => holder.getAttribute('data-lane')).sort()).toEqual(['CENTER', 'LEFT', 'RIGHT']);
        // Same distance, so LEFT is left of CENTER is left of RIGHT.
        const x = (lane: string) => {
          const m = /matrix\(\S+ \S+ \S+ \S+ ([\d.-]+) ([\d.-]+)\)/.exec(
            holders.find((h) => h.getAttribute('data-lane') === lane)!.querySelector('.limit-mark')!.getAttribute('transform')!)!;
          return Number(m[1]);
        };
        expect(x('LEFT')).toBeLessThan(x('CENTER'));
        expect(x('CENTER')).toBeLessThan(x('RIGHT'));
      }
    });

    it('adds an upright roadside sign so the limit stays readable at a distance', () => {
      show({ objects: [{ id: 'r20-CENTER', type: 'SpeedLimitSign', positionMeters: 90, lane: 'CENTER', details: 'limitKmh=50' }] });
      const sign = q('.sign--speed .sign__value')!;
      expect(sign.textContent?.trim()).toBe('50');
      expect(sign.closest('.limit-mark')).toBeNull();
    });

    it('draws one traffic light in all three lanes at the same road position, with its state', () => {
      for (const state of ['RED', 'GREEN']) {
        show({
          ego: { speedKmh: 50, lane: 'RIGHT' },
          objects: [{ id: 'r20-CENTER', type: 'TrafficLight', positionMeters: 90, lane: 'CENTER', details: `TrafficLight state=${state}` }]
        });

        expect(qa('[data-testid="road-traffic-light"]').length).toBe(1, 'one logical traffic light');
        const lights = qa('.obj--trafficLight');
        expect(lights.length).toBe(3);
        expect(lights.map((light) => light.getAttribute('data-state'))).toEqual([state, state, state]);
        expect(new Set(lights.map((light) => light.getAttribute('data-depth'))).size).toBe(1, 'same longitudinal position');
        expect(lights.map((light) => light.getAttribute('data-lane'))).toEqual(['LEFT', 'CENTER', 'RIGHT']);
        // The gantry carries the heads over the road, not at its side.
        expect(q('.gantry__structure path')).toBeTruthy();
      }
    });

    it('shows the badge and distance of a road-wide element in the lane the ego drives in', () => {
      const elements = [
        { testId: 'road-traffic-light', object: { id: 'r20-CENTER', type: 'TrafficLight', positionMeters: 90, lane: 'CENTER', details: 'TrafficLight state=RED' } },
        { testId: 'road-speed-limit', object: { id: 'r20-CENTER', type: 'SpeedLimitSign', positionMeters: 90, lane: 'CENTER', details: 'limitKmh=50' } }
      ];
      for (const element of elements) {
        for (const egoLane of ['LEFT', 'CENTER', 'RIGHT']) {
          show({
            ego: { speedKmh: 50, lane: egoLane },
            objects: [element.object],
            cameraIds: ['r20-CENTER']
          });

          const main = q(`[data-testid="${element.testId}"]`)!;
          expect(main.getAttribute('data-lane')).toBe(egoLane, `${element.testId} with ego in ${egoLane}`);
          expect(main.textContent).toContain('DETECTED');
          expect(main.textContent).toContain('90 m');
          expect(qa('.obj__badge').length).toBe(1, 'the badge is shown once');
        }
      }
    });

    it('draws a stop sign once, in the lane it stands in', () => {
      for (const lane of ['LEFT', 'CENTER', 'RIGHT']) {
        show({
          ego: { speedKmh: 50, lane: 'RIGHT' },
          objects: [{ id: `r20-${lane}`, type: 'StopSign', positionMeters: 90, lane, details: '' }]
        });

        const signs = qa('[data-testid="road-stop-sign"]');
        expect(signs.length).toBe(1, `a stop sign in ${lane} is not repeated in the other lanes`);
        expect(signs[0].getAttribute('data-lane')).toBe(lane);
      }
    });

    it('stands the stop sign on the verge, upright on a pole, never inside the lanes', () => {
      for (const lane of ['LEFT', 'CENTER', 'RIGHT']) {
        show({
          ego: { positionMeters: 0, lane: 'CENTER', lateralPositionMeters: 0 },
          objects: [{ id: `s-${lane}`, type: 'StopSign', positionMeters: 60, lane, details: '' }]
        });
        const sign = q('[data-testid="road-stop-sign"]')!;
        // Beyond the asphalt (5.25 m from the centre line), on the left verge for the left lane.
        const lateral = Number(sign.getAttribute('data-lateral'));
        expect(Math.abs(lateral)).toBeGreaterThan(5.25, lane);
        expect(lateral < 0).toBe(lane === 'LEFT', lane);
        expect(sign.querySelector('.sign__pole')).toBeTruthy();

        // On screen it is outside the asphalt edge at its own depth.
        const frame = translation(sign.querySelector('.sign--stop')!);
        const asphalt = vertices(q('.surface__asphalt')!);
        const at = (a: { x: number; y: number }, b: { x: number; y: number }) => a.x + (b.x - a.x) * ((a.y - frame.y) / (a.y - b.y));
        const left = at(asphalt[0], asphalt[3]);
        const right = at(asphalt[1], asphalt[2]);
        expect(frame.x < left || frame.x > right).toBeTrue();

        // The lane it governs is marked by a stop line painted across that lane, flat on the road.
        const line = vertices(q('.stop-line')!);
        expect(line.length).toBe(4);
        expect(Math.max(...line.map((p) => p.y)) - Math.min(...line.map((p) => p.y))).toBeLessThan(6);
      }
    });

    it('scales the roadside stop sign with distance without drifting into the lane', () => {
      const probe = (ahead: number) => {
        show({ ego: { positionMeters: 0, lane: 'CENTER', lateralPositionMeters: 0 }, objects: [{ id: 's', type: 'StopSign', positionMeters: ahead, lane: 'RIGHT', details: '' }] });
        const frame = translation(q('[data-testid="road-stop-sign"] .sign--stop')!);
        const asphalt = vertices(q('.surface__asphalt')!);
        const edge = asphalt[1].x + (asphalt[2].x - asphalt[1].x) * ((asphalt[1].y - frame.y) / (asphalt[1].y - asphalt[2].y));
        return { scale: frame.scale, y: frame.y, clearance: frame.x - edge };
      };
      const far = probe(150);
      const mid = probe(40);
      const near = probe(10);
      // Far signs keep a legibility floor, so only the nearer ones must grow strictly.
      expect(near.scale).toBeGreaterThan(mid.scale);
      expect(mid.scale).toBeGreaterThan(far.scale);
      expect(near.y).toBeGreaterThan(mid.y);
      for (const p of [far, mid, near]) {
        expect(p.clearance).toBeGreaterThan(0);
      }
    });
  });

  describe('ego vehicle', () => {
    it('should keep all four wheels rigidly inside the ego chassis group with zero independent world translation', () => {
      show({
        ego: {
          speedKmh: 50, positionMeters: 30, lane: 'RIGHT', lateralPositionMeters: 2.5, targetLane: 'CENTER',
          laneChangeState: 'IN_PROGRESS', laneChangeProgress: 0.5, indicatorState: 'LEFT', steeringState: 'STEER_LEFT', headingDegrees: -4.5
        }
      });

      const egoElement = q('.ego')!;
      expect(egoElement).toBeTruthy();

      const wheelGroup = egoElement.querySelector('.car__wheels')!;
      expect(wheelGroup).toBeTruthy('All wheels must be children of the ego vehicle SVG');
      expect(wheelGroup.querySelectorAll('.front-wheel').length).toBe(2, 'Must have 2 front wheels');
      expect(wheelGroup.querySelectorAll('.rear-wheel').length).toBe(2, 'Must have 2 rear wheels');
      expect(egoElement.contains(wheelGroup)).toBeTrue();
      expect(wheelGroup.parentElement!.classList.contains('car--ego')).toBeTrue();
    });

    it('is a 3D body: roof, glass and tail lamps seen from behind', () => {
      show({ ego: { lane: 'CENTER', lateralPositionMeters: 0 } });
      const classes = qa('[data-testid="road-ego"] .car--ego > path').map((p) => p.getAttribute('class')).join(' ');
      for (const part of ['f--body', 'f--glass', 'f--roof', 'f--tail', 'f--plate', 'f--top', 'f--rear']) {
        expect(classes).toContain(part);
      }
    });

    it('is visibly different from other vehicles', () => {
      show({
        ego: { lane: 'CENTER', lateralPositionMeters: 0 },
        objects: [{ id: 'v', type: 'OtherVehicle', positionMeters: 25, lane: 'CENTER', details: 'speedMps=9' }]
      });
      expect(q('[data-testid="road-ego"] .car--ego')).toBeTruthy();
      expect(q('[data-testid="road-ego"] .car--other')).toBeNull();
      expect(q('[data-testid="road-vehicle"] .car--other')).toBeTruthy();
      expect(q('[data-testid="road-vehicle"] .car--ego')).toBeNull();
      expect(q('[data-testid="road-vehicle"] .ego__tag')).toBeNull();
    });

    it('yaws with the backend heading during a lane change, and sits level again afterwards', () => {
      // The second lamp of each side sits on the flank, so it is only drawn when that flank turns
      // towards the camera: the flank on the side the nose turns to.
      const flankLamps = (heading: number) => {
        show({ ego: { lane: 'CENTER', lateralPositionMeters: 0, headingDegrees: heading } });
        const drawn = (side: string) => qa(`[data-testid="ego-indicator-${side}"] .indicator-light`)[1].getAttribute('d') !== '';
        return { left: drawn('left'), right: drawn('right') };
      };
      expect(flankLamps(0)).toEqual({ left: false, right: false });
      expect(flankLamps(-5)).toEqual({ left: true, right: false });
      expect(flankLamps(5)).toEqual({ left: false, right: true });
      expect(flankLamps(0)).toEqual({ left: false, right: false });
    });

    it('lights the brake lamps from the backend state only', () => {
      show({ ego: { drivingState: 'CRUISING', accelerationMps2: 0 } });
      expect(q('.ego')!.classList.contains('ego--braking')).toBeFalse();
      show({ ego: { drivingState: 'BRAKING', accelerationMps2: -2 } });
      expect(q('.ego')!.classList.contains('ego--braking')).toBeTrue();
      expect(qa('.ego .brake-lamp').length).toBe(2);
    });

    for (const indicator of ['LEFT', 'RIGHT', 'HAZARD', 'OFF']) {
      it(`should flash the ego blinker lamps for indicator ${indicator}, and show none while off`, () => {
        show({ ego: { positionMeters: 30, lane: 'CENTER', lateralPositionMeters: 0, laneChangeState: 'IDLE', indicatorState: indicator } });

        const ego = q('[data-testid="road-ego"]')!;
        const left = indicator === 'LEFT' || indicator === 'HAZARD';
        const right = indicator === 'RIGHT' || indicator === 'HAZARD';
        expect(ego.classList.contains('ego--indicator-left') || ego.classList.contains('ego--indicator-hazard')).toBe(left);
        expect(ego.classList.contains('ego--indicator-right') || ego.classList.contains('ego--indicator-hazard')).toBe(right);

        for (const side of ['left', 'right']) {
          const group = ego.querySelector(`[data-testid="ego-indicator-${side}"]`)!;
          expect(group).toBeTruthy(`the ${side} blinker must be drawn on the ego car`);
          expect(group.querySelectorAll('.indicator-light').length).toBe(3, `rear, flank and mirror lamps on the ${side}`);
          const lamp = group.querySelector('.indicator-light') as SVGElement;
          const flashing = side === 'left' ? left : right;
          expect(getComputedStyle(lamp).animationName).toBe(flashing ? 'indicator-blink' : 'none');
          if (!flashing) {
            expect(getComputedStyle(lamp).opacity).toBe('0', `the ${side} blinker is not shown while off`);
          }
        }
      });
    }

    it('should render clean EGO badge without concatenating state text like EGOCRUISING', () => {
      show({
        ego: { positionMeters: 10, lane: 'CENTER', lateralPositionMeters: 0, drivingState: 'CRUISING' },
        objects: [{ id: 'obs_1', type: 'StaticObstacle', positionMeters: 50, worldPositionMeters: 60, lane: 'CENTER', details: 'severity=HIGH' }]
      });

      const egoTag = q('.ego__tag')!;
      expect(egoTag).toBeTruthy();
      expect(egoTag.textContent!.trim()).toBe('EGO');
      expect(q('[data-testid="road-ego"]')!.textContent!.trim()).toBe('EGO');
      expect(root.textContent).not.toContain('EGOCRUISING');
      expect(q('[data-testid="road-obstacle"]')).toBeTruthy('Obstacle must have data-testid="road-obstacle"');
    });
  });

  describe('sensor fields of view follow the ego vehicle', () => {
    function sensorSnapshot(ego: Record<string, unknown>, sensors: Record<string, unknown> = {}) {
      return { ego: { positionMeters: 100, lane: 'RIGHT', lateralPositionMeters: 3.5, targetLane: 'RIGHT', laneChangeState: 'IDLE', ...ego }, sensors };
    }

    it('renders the camera cone, radar corridor and blind-spot pads as ground shapes with stable test ids', () => {
      show(sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: 0 }, {
        blindSpotLeft: { rangeMeters: 30, occupied: true, targetId: 'v1', distanceMeters: 4, status: 'OCCUPIED' },
        blindSpotRight: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'CLEAR' }
      }));

      const camera = q('[data-testid="canvas-sensor-camera"]')!;
      const radar = q('[data-testid="canvas-sensor-radar"]')!;
      expect(camera.tagName.toLowerCase()).toBe('path');
      expect(vertices(camera).length).toBe(6, 'the camera cone has six corners');
      expect(vertices(radar).length).toBe(4);
      expect(q('[data-testid="canvas-sensor-blind-left"]')!.classList.contains('sensor-blind--occupied')).toBeTrue();
      expect(q('[data-testid="canvas-sensor-blind-right"]')!.classList.contains('sensor-blind--occupied')).toBeFalse();
      expect(q('[data-testid="radar-fov"]')).toBeTruthy('radar range edge keeps its test id');
      expect(q('.sensor-zone')).toBeNull('old static zones must be gone');
    });

    it('starts the camera cone and the radar corridor at the nose of the ego and widens them into the road ahead', () => {
      show(sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: 0 }));
      const cone = vertices(q('[data-testid="canvas-sensor-camera"]')!);
      const egoPlate = vertices(q('[data-testid="road-ego"] .f--plate')!);
      const egoX = egoPlate.reduce((sum, p) => sum + p.x, 0) / egoPlate.length;

      // First and last corner are the nose: centred on the ego, close together.
      expect((cone[0].x + cone[5].x) / 2).toBeCloseTo(egoX, 0);
      expect(cone[5].x - cone[0].x).toBeLessThan(cone[4].x - cone[1].x);
      // The far edge is higher on the screen than the nose, and narrower than the near spread.
      expect(cone[2].y).toBeLessThan(cone[0].y);
      expect(cone[2].y).toBeLessThanOrEqual(cone[1].y);

      const radar = vertices(q('[data-testid="canvas-sensor-radar"]')!);
      expect((radar[0].x + radar[1].x) / 2).toBeCloseTo(egoX, 0);
      expect(radar[3].y).toBeLessThan(radar[0].y);
      expect(radar[3].x - radar[2].x).toBeLessThan(0, 'the corridor narrows with distance');
    });

    it('draws a blind-spot pad only on sides that have a lane', () => {
      show(sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 }));
      expect(q('[data-testid="canvas-sensor-blind-left"]')).not.toBeNull();
      expect(q('[data-testid="canvas-sensor-blind-right"]')).toBeNull();

      show(sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: 0 }));
      expect(q('[data-testid="canvas-sensor-blind-left"]')).not.toBeNull();
      expect(q('[data-testid="canvas-sensor-blind-right"]')).not.toBeNull();

      show(sensorSnapshot({ lane: 'LEFT', lateralPositionMeters: -3.5 }));
      expect(q('[data-testid="canvas-sensor-blind-left"]')).toBeNull();
      expect(q('[data-testid="canvas-sensor-blind-right"]')).not.toBeNull();
    });

    it('puts each blind-spot pad in the lane beside the ego and spans the sensor range behind and ahead', () => {
      show(sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: 0 }));
      const left = vertices(q('[data-testid="canvas-sensor-blind-left"]')!);
      const right = vertices(q('[data-testid="canvas-sensor-blind-right"]')!);
      const egoPlate = vertices(q('[data-testid="road-ego"] .f--plate')!);
      const egoX = egoPlate.reduce((sum, p) => sum + p.x, 0) / egoPlate.length;
      expect(Math.max(...left.map((p) => p.x))).toBeLessThan(egoX);
      expect(Math.min(...right.map((p) => p.x))).toBeGreaterThan(egoX);
      // Symmetric zone: it starts behind the ego's contact point and ends well ahead of it.
      const egoY = Math.max(...egoPlate.map((p) => p.y));
      expect(Math.max(...left.map((p) => p.y))).toBeGreaterThan(egoY - 20);
      expect(Math.min(...left.map((p) => p.y))).toBeLessThan(egoY - 20);
    });

    it('slides every shape with the lateral position during a lane change', () => {
      const radarCentre = (lateral: number) => {
        show(sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: lateral, targetLane: 'RIGHT', laneChangeState: 'CHANGING', laneChangeProgress: 0.5 }));
        const radar = vertices(q('[data-testid="canvas-sensor-radar"]')!);
        return (radar[0].x + radar[1].x) / 2;
      };
      const path = [0, 1, 2, 3, 3.5].map(radarCentre);
      for (let i = 1; i < path.length; i++) {
        expect(path[i]).toBeGreaterThan(path[i - 1]);
      }
    });

    it('uses the blind-spot telemetry to hide the road-edge side and to flag an occupied side', () => {
      show(sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 }, {
        blindSpotLeft: { rangeMeters: 30, occupied: true, targetId: 'v1', distanceMeters: 4, status: 'OCCUPIED' },
        blindSpotRight: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'NO LANE' }
      }));

      expect(q('[data-testid="canvas-sensor-blind-left"]')!.classList.contains('sensor-blind--occupied')).toBeTrue();
      expect(q('[data-testid="canvas-sensor-blind-right"]')).toBeNull();
    });

    it('keeps a blind-spot pad on the asphalt while the car drifts towards an outer lane', () => {
      const clear = {
        blindSpotLeft: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'CLEAR' },
        blindSpotRight: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'CLEAR' }
      };
      show(sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: 2.5, targetLane: 'RIGHT', laneChangeState: 'CHANGING' }, clear));

      const pad = vertices(q('[data-testid="canvas-sensor-blind-right"]')!);
      const asphalt = vertices(q('.surface__asphalt')!);
      // Nothing of the pad sticks out past the asphalt's right edge (its widest row is the nearest one).
      expect(Math.max(...pad.map((p) => p.x))).toBeLessThanOrEqual(asphalt[1].x + 0.5);
      expect(Math.max(...pad.map((p) => p.x)) - Math.min(...pad.map((p) => p.x))).toBeGreaterThan(0);
    });

    it('drops the road-edge pad even when the telemetry reports that side as CLEAR', () => {
      const clear = {
        blindSpotLeft: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'CLEAR' },
        blindSpotRight: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'CLEAR' }
      };
      show(sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 }, clear));

      expect(q('[data-testid="canvas-sensor-blind-right"]')).toBeNull();
      expect(q('[data-testid="canvas-sensor-blind-left"]')).not.toBeNull();
    });

    it('draws no sensor layer before the first snapshot arrives', () => {
      fixture.detectChanges();

      expect(q('[data-testid="canvas-sensor-layer"]')).toBeNull();
      expect(q('[data-testid="camera-fov"]')).toBeNull('no "CAMERA FOV (0 m)" edge at the car');
      expect(q('[data-testid="radar-fov"]')).toBeNull('no "FRONT RADAR FOV (0 m)" edge at the car');
    });

    it('shortens the camera cone to the effective range and marks the range lost to rain', () => {
      show(sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5, positionMeters: 100 },
        { environment: { condition: 'RAIN', effectiveRangeMeters: 84, gripPercent: 65, rain: true } }));

      const edge = q('[data-testid="camera-fov"]')!;
      const ghost = q('[data-testid="camera-fov-nominal"]')!;
      const lost = q('[data-testid="canvas-sensor-camera-lost"]')!;

      expect(parseFloat(edge.getAttribute('data-ahead')!)).toBeCloseTo(84, 0);
      expect(parseFloat(ghost.getAttribute('data-ahead')!)).toBeCloseTo(120, 0);
      expect(edge.textContent).toContain('CAMERA 84 m (120 × 70%)');
      expect(lost).toBeTruthy('the band between 84 m and 120 m must be drawn');
      // The band is a ground quad between the two edges: higher on screen than the cone's far edge.
      const cone = vertices(q('[data-testid="canvas-sensor-camera"]')!);
      const band = vertices(lost);
      expect(Math.max(...band.map((p) => p.y))).toBeLessThanOrEqual(Math.min(cone[2].y, cone[3].y) + 0.5);
      expect(Math.min(...band.map((p) => p.y))).toBeLessThan(Math.max(...band.map((p) => p.y)));
    });

    it('draws the full camera cone and no lost band in clear weather', () => {
      show(sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 },
        { environment: { condition: 'CLEAR', effectiveRangeMeters: 120, gripPercent: 100, rain: false } }));

      expect(q('[data-testid="canvas-sensor-camera-lost"]')).toBeNull();
      expect(q('[data-testid="camera-fov-nominal"]')).toBeNull();
      expect(q('[data-testid="camera-fov"]')!.textContent).toContain('CAMERA FOV (120 m)');
      expect(q('[data-testid="radar-fov"]')!.textContent).toContain('FRONT RADAR FOV (130 m)');
    });

    it('never lets the camera cone fold behind the nose when the effective range is tiny', () => {
      show(sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 },
        { environment: { condition: 'RAIN', effectiveRangeMeters: 2, gripPercent: 65, rain: true } }));

      const cone = vertices(q('[data-testid="canvas-sensor-camera"]')!);
      expect(cone.length).toBe(6);
      const noseY = cone[0].y;
      for (const point of cone) {
        expect(Number.isFinite(point.x) && Number.isFinite(point.y)).toBeTrue();
        // Nothing nearer to the camera (lower on screen) than the nose.
        expect(point.y).toBeLessThanOrEqual(noseY + 1e-6);
      }
    });
  });

  it('renders a passed object behind the car with the PASSED label and the passed class', () => {
    show({
      ego: { speedKmh: 60, positionMeters: 120, lane: 'RIGHT' },
      objects: [{ id: 'r18-RIGHT', type: 'Pothole', positionMeters: -4, worldPositionMeters: 116, lane: 'RIGHT', details: 'severity=HIGH' }],
      cameraIds: ['r18-RIGHT']
    });

    const pothole = q('[data-testid="road-pothole"]')!;
    expect(pothole).toBeTruthy('a pothole just behind the car is still rendered');
    expect(pothole.classList.contains('obj--passed')).toBeTrue();
    expect(pothole.textContent).toContain('PASSED');
    expect(pothole.querySelector('.obj__badge')).toBeNull('a passed object keeps no DETECTED badge');
  });

  it('stops drawing an object once it is far behind the camera', () => {
    show({
      ego: { positionMeters: 120 },
      objects: [{ id: 'gone', type: 'Pothole', positionMeters: -30, worldPositionMeters: 90, lane: 'RIGHT', details: '' }]
    });
    expect(q('[data-testid="road-pothole"]')).toBeNull();
  });

  it('tags objects with detection and distance only where it helps', () => {
    show({
      ego: { positionMeters: 0, lane: 'CENTER', lateralPositionMeters: 0 },
      objects: [
        { id: 'seen', type: 'StaticObstacle', positionMeters: 60, lane: 'LEFT', details: '' },
        { id: 'radared', type: 'OtherVehicle', positionMeters: 80, lane: 'CENTER', details: '' },
        { id: 'remote', type: 'Pothole', positionMeters: 200, lane: 'RIGHT', details: '' }
      ],
      cameraIds: ['seen'],
      radarIds: ['radared']
    });
    expect(q('[data-object-id="seen"]')!.textContent).toContain('DETECTED');
    expect(q('[data-object-id="radared"]')!.textContent).toContain('RADAR');
    expect(q('[data-object-id="radared"] .obj__badge--radar')).toBeTruthy();
    // Beyond sensor reach and undetected: drawn, but without a plate to clutter the scene.
    expect(q('[data-object-id="remote"]')).toBeTruthy();
    expect(q('[data-object-id="remote"] .tag')).toBeNull();
  });

  it('shows the rain chip with the visibility and grip the backend is using', () => {
    show({
      ego: { speedKmh: 52, positionMeters: 80 },
      environment: { weatherCondition: 'RAIN', visibilityFactor: 0.7, gripFactor: 0.65 },
      sensors: { environment: { condition: 'RAIN', effectiveRangeMeters: 84, gripPercent: 99, rain: true } }
    });

    expect(q('.road--rain')).toBeTruthy();
    expect(q('[data-testid="rain-overlay"]')).toBeTruthy();
    expect(qa('.rain-overlay__drops').length).toBe(2, 'near and far streaks give the shower depth');
    expect(q('.surface__wet')).toBeTruthy('the wet road reflects the sky');
    const chip = q('[data-testid="rain-chip"]')!;
    expect(chip.textContent).toContain('VIS 70%');
    expect(chip.textContent).toContain('GRIP 65%');

    show({ ego: { speedKmh: 52, positionMeters: 81 } });

    expect(q('[data-testid="rain-overlay"]')).toBeNull();
    expect(q('[data-testid="rain-chip"]')).toBeNull();
    expect(q('.road--rain')).toBeNull();
    expect(q('.surface__wet')).toBeNull();
  });

  it('keeps the telemetry HUD and the start invitation', () => {
    show({ ego: { positionMeters: 12.34, speedKmh: 63.4, lane: 'CENTER' }, time: 0, status: 'STOPPED' });
    expect(q('[data-testid="hud-position"]')!.textContent).toContain('12.3 m');
    expect(q('[data-testid="hud-speed"]')!.textContent).toContain('63 km/h');
    expect(q('[data-testid="hud-lane"]')!.textContent).toContain('CENTER');
    expect(q('[data-testid="road-await-start"]')).toBeTruthy();
    show({ time: 500 });
    expect(q('[data-testid="road-await-start"]')).toBeNull();
  });

  describe('crosswalk', () => {
    function pedestrianSnapshot(timeMillis: number, egoPositionMeters: number, worldObjects: unknown[]) {
      return snapshotWith({
        time: timeMillis,
        ego: { speedKmh: 30, positionMeters: egoPositionMeters, lane: 'RIGHT' },
        objects: worldObjects
      });
    }

    function crosswalk(egoPositionMeters: number) {
      return {
        id: 'r20-ALL', type: 'Crosswalk', positionMeters: 100 - egoPositionMeters, lane: 'ALL',
        details: "Crosswalk{id='r20-ALL', positionMeters=100.0, lane=null, active=true}", worldPositionMeters: 100
      };
    }

    function pedestrian(egoPositionMeters: number) {
      return {
        id: 'p1', type: 'Pedestrian', positionMeters: 100 - egoPositionMeters, lane: 'LEFT',
        details: "Pedestrian{id='p1', crossingState=CROSSING}", worldPositionMeters: 100
      };
    }

    function crosswalkDepth(): number | null {
      const el = q('[data-testid="crosswalk"]');
      return el ? parseFloat(el.getAttribute('data-depth')!) : null;
    }

    function setSnapshot(snapshot: unknown) {
      fixture.componentRef.setInput('snapshot', snapshot);
      fixture.detectChanges();
    }

    it('is painted across the road from the Crosswalk world object, not from the pedestrian', () => {
      setSnapshot(pedestrianSnapshot(1000, 90, [crosswalk(90), pedestrian(90)]));
      expect(qa('[data-testid="crosswalk"]').length).toBe(1);
      expect(q('[data-testid="road-pedestrian"]')).not.toBeNull();
      expect(q('.obj--crosswalk')).toBeNull();
      const atCrossing = crosswalkDepth();

      // The pedestrian has crossed and left the world; the crosswalk slides behind the car.
      setSnapshot(pedestrianSnapshot(2000, 105, [crosswalk(105)]));
      expect(q('[data-testid="road-pedestrian"]')).toBeNull();
      expect(crosswalkDepth()).not.toBeNull();
      expect(crosswalkDepth()!).toBeLessThan(atCrossing!);
    });

    it('spans all three lanes, and the zebra bars lie across the road', () => {
      setSnapshot(pedestrianSnapshot(1000, 90, [crosswalk(90)]));
      const bars = q('.crosswalk__bars')!.getAttribute('d')!.split('Z').filter((part) => part.trim());
      expect(bars.length).toBeGreaterThanOrEqual(8);
      const asphalt = vertices(q('.surface__asphalt')!);
      const all = vertices(q('.crosswalk__bars')!);
      expect(Math.min(...all.map((p) => p.x))).toBeGreaterThan(Math.min(asphalt[0].x, asphalt[3].x) - 1);
      expect(Math.max(...all.map((p) => p.x))).toBeLessThan(Math.max(asphalt[1].x, asphalt[2].x) + 1);
    });

    it('draws a pedestrian where it is across the road, not at its lane centre', () => {
      const xAt = (lateralPositionMeters: number) => {
        setSnapshot(pedestrianSnapshot(1000, 90, [crosswalk(90), { ...pedestrian(90), lateralPositionMeters }]));
        return translation(q('[data-testid="road-pedestrian"] .person')!).x;
      };
      const a = xAt(-3.5);
      const b = xAt(-1.0);
      const c = xAt(2.0);
      expect(a).toBeLessThan(b);
      expect(b).toBeLessThan(c);
      // Same depth, so the screen distance is proportional to the lateral distance (2.5 m, then 3 m).
      expect((b - a) / 2.5).toBeCloseTo((c - b) / 3.0, 1);
      expect(q('[data-testid="road-pedestrian"]')!.getAttribute('data-lateral')).toBe('2.00');
    });

    it('draws a pedestrian on the middle of its crosswalk, not where it stands in the map block', () => {
      // The crosswalk is at the start of its 5 m block, the pedestrian 3 m into it.
      setSnapshot(pedestrianSnapshot(1000, 90,
        [crosswalk(90), { ...pedestrian(90), positionMeters: 13, worldPositionMeters: 103 }]));
      const depth = parseFloat(q('[data-testid="road-pedestrian"]')!.getAttribute('data-depth')!);
      expect(depth).toBeCloseTo(crosswalkDepth()!, 1);
    });
  });

  describe('motion between snapshots', () => {
    const potholeAt = (world: number) => ({ id: 'p', type: 'Pothole', positionMeters: world - 10, worldPositionMeters: world, lane: 'RIGHT', details: '' });
    const depth = () => parseFloat(q('[data-testid="road-pothole"]')!.getAttribute('data-depth')!);

    beforeEach(() => fixture.componentRef.setInput('animate', true));

    it('slides to a new snapshot instead of jumping, and lands exactly on it', async () => {
      show({ ego: { positionMeters: 10 }, objects: [potholeAt(100)] });
      const first = depth();
      expect(first).toBeCloseTo(100 - 10 + 16, 1);

      show({ ego: { positionMeters: 12 }, objects: [potholeAt(100)] });
      // Right after the snapshot the drawn state has barely moved: no teleporting.
      expect(depth()).toBeGreaterThan(first - 1);

      await new Promise((resolve) => setTimeout(resolve, 500));
      expect(depth()).toBeCloseTo(100 - 12 + 16, 1);
    });

    it('draws a snapshot at once when the simulation is not running', () => {
      show({ ego: { positionMeters: 10 }, objects: [potholeAt(100)] });
      show({ ego: { positionMeters: 12 }, objects: [potholeAt(100)], status: 'PAUSED' });
      expect(depth()).toBeCloseTo(100 - 12 + 16, 1);
    });

    it('shows a reset or a scenario load at once, never as a long slide', () => {
      show({ ego: { positionMeters: 900 }, objects: [potholeAt(1000)] });
      show({ ego: { positionMeters: 0 }, objects: [potholeAt(100)] });
      expect(depth()).toBeCloseTo(100 + 16, 1);
    });
  });

  describe('responsive canvas', () => {
    afterEach(() => root.style.cssText = '');

    for (const [width, height] of [[1296, 355], [1024, 257], [909, 257], [874, 492], [792, 446]]) {
      it(`adapts the camera to a ${width}x${height} road area without clipping the ego or distorting it`, async () => {
        show({ ego: { lane: 'CENTER', lateralPositionMeters: 0 }, objects: [{ id: 'v', type: 'OtherVehicle', positionMeters: 40, lane: 'CENTER', details: '' }] });
        await resizeTo(width, height);

        const scene = q('[data-testid="road-scene"]')!;
        expect(scene.getAttribute('viewBox')).toBe(`0 0 ${width}.0 ${height}.0`);

        const ego = q('[data-testid="road-ego"] .car--ego') as unknown as SVGGraphicsElement;
        const box = ego.getBBox();
        expect(box.x).toBeGreaterThan(0);
        expect(box.x + box.width).toBeLessThan(width);
        expect(box.y).toBeGreaterThan(height * 0.2);
        expect(box.y + box.height).toBeLessThan(height);
        // Proportions of the car stay those of a car whatever the canvas shape.
        expect(box.width / box.height).toBeGreaterThan(0.7);
        expect(box.width / box.height).toBeLessThan(2.6);

        const asphalt = vertices(q('.surface__asphalt')!);
        const horizon = Number(q('.ground')!.getAttribute('y'));
        expect(horizon).toBeGreaterThan(height * 0.1);
        expect(horizon).toBeLessThan(height * 0.4);
        expect(asphalt[3].y).toBeGreaterThan(horizon);
        // The road is centred under the vanishing point.
        expect((asphalt[2].x + asphalt[3].x) / 2).toBeCloseTo(width / 2, 0);
      });
    }

    it('re-fits when the road area is resized', async () => {
      show();
      await resizeTo(900, 300);
      const wide = q('[data-testid="road-scene"]')!.getAttribute('viewBox');
      await resizeTo(700, 400);
      expect(q('[data-testid="road-scene"]')!.getAttribute('viewBox')).not.toBe(wide);
    });
  });
});
