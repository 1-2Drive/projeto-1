import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RoadCanvasComponent, SensorGeometry } from './road-canvas.component';

describe('RoadCanvasComponent', () => {
  let component: RoadCanvasComponent;
  let fixture: ComponentFixture<RoadCanvasComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoadCanvasComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(RoadCanvasComponent);
    component = fixture.componentInstance;
  });

  it('should render world items from snapshot coordinates', () => {
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 1200,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 90, positionMeters: 42, lane: 'CENTER', accelerationMps2: 0 },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
      speedAdvisor: { currentKmh: 90, legalLimitKmh: 120, recommendedKmh: 90, action: 'MAINTAIN_SPEED' },
      currentDecision: {
        action: 'MAINTAIN_SPEED',
        recommendedSpeedKmh: 90,
        riskLevel: 'LOW',
        reason: '',
        sourceRule: '',
        timestamp: 1200,
        evidence: []
      },
      sensorReadings: {},
      worldObjects: [
        { id: 'a', type: 'SpeedLimitSign', positionMeters: 80, lane: 'CENTER', details: 'limitKmh=50' },
        { id: 'b', type: 'FrontVehicleActor', positionMeters: 35, lane: 'CENTER', details: 'speed=22' }
      ],
      workingMemory: [],
      recentTimeline: [],
      recentRules: [],
      recentFacts: [],
      activeStage: 'SENSORS',
      tickMillis: 100,
      detectedObjectIds: ['a'],
      cameraDetectedObjectIds: ['a'],
      radarDetectedObjectIds: [],
      sensorRanges: {
        cameraRangeMeters: 120,
        radarRangeMeters: 80,
        blindSpotRangeMeters: 25,
        cameraRateMillis: 200,
        radarRateMillis: 100
      },
      demoPhases: []
    });

    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('50');
    expect(text).toContain('35 m');
  });

  it('renders an all-lane speed enforcement station above the road with its configured limit', () => {
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 1000,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 80, positionMeters: 0, lane: 'RIGHT', accelerationMps2: -1.5 },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
      speedAdvisor: { currentKmh: 80, legalLimitKmh: 50, recommendedKmh: 50, action: 'REDUCE_SPEED' },
      currentDecision: {
        action: 'REDUCE_SPEED',
        recommendedSpeedKmh: 50,
        riskLevel: 'MODERATE',
        reason: 'Approaching speed radar',
        sourceRule: 'Decelerate approaching speed radar',
        timestamp: 1000,
        evidence: []
      },
      sensorReadings: {},
      sensors: {
        frontRadar: {
          rangeMeters: 130,
          targetId: 'none',
          distanceMeters: Infinity,
          relativeSpeedKmh: 0,
          ttcSeconds: Infinity,
          status: 'CLEAR',
          speedRadarDistanceMeters: 130,
          speedRadarLimitKmh: 50
        }
      },
      worldObjects: [
        { id: 'radar_1', type: 'SpeedRadar', positionMeters: 130, lane: 'ALL', details: 'SpeedRadar{speedLimitKmh=50, scope=ALL_LANES}' }
      ],
      workingMemory: [],
      recentTimeline: [],
      recentRules: [],
      recentFacts: [],
      activeStage: 'SENSORS',
      tickMillis: 50,
      detectedObjectIds: [],
      cameraDetectedObjectIds: [],
      radarDetectedObjectIds: [],
      sensorRanges: {
        cameraRangeMeters: 100,
        radarRangeMeters: 130,
        blindSpotRangeMeters: 25,
        cameraRateMillis: 200,
        radarRateMillis: 50
      },
      demoPhases: []
    });

    fixture.detectChanges();

    const radarStation = fixture.nativeElement.querySelector('.radar-station');
    expect(radarStation).toBeTruthy('Physical speed radar SVG must be rendered on the road');
    expect(radarStation.textContent).toContain('RADAR');
    expect(radarStation.getAttribute('aria-label')).toContain('All-lane speed radar');
    const radarObject = fixture.nativeElement.querySelector('.obj--speedRadar') as HTMLElement;
    expect(radarObject.style.top).toBe('4%');
    expect(radarObject.querySelector('.car--other')).toBeNull('Radar infrastructure must never use the vehicle renderer');

    const hudRadar = fixture.nativeElement.querySelector('[data-testid="hud-radar"]');
    expect(hudRadar).toBeTruthy('Speed radar HUD telemetry must display');
    expect(hudRadar.textContent).toContain('130 m (50 km/h)');
  });

  it('should keep all four wheels rigidly inside the ego chassis group with zero independent world translation', () => {
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 2000,
      status: 'RUNNING',
      egoVehicle: {
        speedKmh: 50,
        positionMeters: 30,
        lane: 'RIGHT',
        lateralPositionMeters: 2.5,
        targetLane: 'CENTER',
        laneChangeState: 'IN_PROGRESS',
        laneChangeProgress: 0.5,
        indicatorState: 'LEFT',
        steeringState: 'STEER_LEFT',
        headingDegrees: -4.5
      },
      sensors: {},
      worldObjects: [],
      sensorRanges: {
        cameraRangeMeters: 100,
        radarRangeMeters: 130,
        blindSpotRangeMeters: 25,
        cameraRateMillis: 200,
        radarRateMillis: 50
      }
    });

    fixture.detectChanges();

    const egoElement = fixture.nativeElement.querySelector('.ego');
    expect(egoElement).toBeTruthy();

    const wheelGroup = egoElement.querySelector('.car__wheels');
    expect(wheelGroup).toBeTruthy('All wheels must be children of the ego vehicle SVG');

    const frontWheels = wheelGroup.querySelectorAll('.front-wheel');
    const rearWheels = wheelGroup.querySelectorAll('.rear-wheel');
    expect(frontWheels.length).toBe(2, 'Must have 2 front wheels');
    expect(rearWheels.length).toBe(2, 'Must have 2 rear wheels');

    // Wheels must be inside the SVG container of .ego, sharing the same parent world transform
    expect(egoElement.contains(wheelGroup)).toBeTrue();
  });

  it('should visibly advance ego screen X toward static speed radar across time and separate radar from FOV', () => {
    // Initial state at 200m
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 0,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 80, positionMeters: 200, lane: 'RIGHT', accelerationMps2: -1.5 },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
      speedAdvisor: { currentKmh: 80, legalLimitKmh: 50, recommendedKmh: 50, action: 'REDUCE_SPEED' },
      currentDecision: { action: 'REDUCE_SPEED', recommendedSpeedKmh: 50, riskLevel: 'MODERATE', reason: '', sourceRule: '', timestamp: 0, evidence: [] },
      sensorReadings: {},
      sensors: {
        frontRadar: {
          rangeMeters: 130, targetId: 'none', distanceMeters: Infinity, relativeSpeedKmh: 0,
          ttcSeconds: Infinity, status: 'CLEAR', speedRadarDistanceMeters: 130, speedRadarLimitKmh: 50
        }
      },
      worldObjects: [
        { id: 'radar_1', type: 'SpeedRadar', positionMeters: 130, worldPositionMeters: 330, lane: 'ALL', details: 'SpeedRadar{speedLimitKmh=50, scope=ALL_LANES}' }
      ],
      sensorRanges: { cameraRangeMeters: 100, radarRangeMeters: 130, blindSpotRangeMeters: 25, cameraRateMillis: 200, radarRateMillis: 50 }
    });
    fixture.detectChanges();

    const speedRadar = fixture.nativeElement.querySelector('[data-testid="speed-radar"]');
    const radarFov = fixture.nativeElement.querySelector('[data-testid="radar-fov"]');
    expect(speedRadar).toBeTruthy('Physical speed radar object must exist and be visible');
    expect(radarFov).toBeTruthy('Radar FOV sensor edge must exist');
    expect(speedRadar).not.toBe(radarFov, 'Physical radar and radar FOV must be distinct elements');

    const egoT1 = fixture.nativeElement.querySelector('[data-testid="road-ego"]') as HTMLElement;
    const egoT1Left = parseFloat(egoT1.style.left);
    const radarT1 = speedRadar as HTMLElement;
    const radarT1Left = parseFloat(radarT1.style.left);
    const distT1 = Math.abs(radarT1Left - egoT1Left);

    // Advanced state at 260m
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 3000,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 60, positionMeters: 260, lane: 'RIGHT', accelerationMps2: -1.0 },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
      speedAdvisor: { currentKmh: 60, legalLimitKmh: 50, recommendedKmh: 50, action: 'REDUCE_SPEED' },
      currentDecision: { action: 'REDUCE_SPEED', recommendedSpeedKmh: 50, riskLevel: 'MODERATE', reason: '', sourceRule: '', timestamp: 3000, evidence: [] },
      sensorReadings: {},
      sensors: {
        frontRadar: {
          rangeMeters: 130, targetId: 'none', distanceMeters: Infinity, relativeSpeedKmh: 0,
          ttcSeconds: Infinity, status: 'CLEAR', speedRadarDistanceMeters: 70, speedRadarLimitKmh: 50
        }
      },
      worldObjects: [
        { id: 'radar_1', type: 'SpeedRadar', positionMeters: 70, worldPositionMeters: 330, lane: 'ALL', details: 'SpeedRadar{speedLimitKmh=50, scope=ALL_LANES}' }
      ],
      sensorRanges: { cameraRangeMeters: 100, radarRangeMeters: 130, blindSpotRangeMeters: 25, cameraRateMillis: 200, radarRateMillis: 50 }
    });
    fixture.detectChanges();

    const egoT2 = fixture.nativeElement.querySelector('[data-testid="road-ego"]') as HTMLElement;
    const egoT2Left = parseFloat(egoT2.style.left);
    const radarT2 = fixture.nativeElement.querySelector('[data-testid="speed-radar"]') as HTMLElement;
    const radarT2Left = parseFloat(radarT2.style.left);
    const distT2 = Math.abs(radarT2Left - egoT2Left);

    // Assert visual distance between car and radar visibly decreased
    expect(distT2).toBeLessThan(distT1, 'Visual distance between ego and speed radar must decrease');
    // Assert radar approached ego on screen
    expect(radarT2Left).toBeLessThan(radarT1Left, 'Radar screen position must move toward ego as car approaches');
    // Assert ego stays in stable tracking region
    expect(egoT1Left).toBeCloseTo(20, 1);
    expect(egoT2Left).toBeCloseTo(20, 1);
  });

  it('should render clean EGO badge without concatenating state text like EGOCRUISING', () => {
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 500,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 50, positionMeters: 10, lane: 'CENTER', accelerationMps2: 0, drivingState: 'CRUISING' },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
      speedAdvisor: { currentKmh: 50, legalLimitKmh: 50, recommendedKmh: 50, action: 'MAINTAIN_SPEED' },
      currentDecision: { action: 'MAINTAIN_SPEED', recommendedSpeedKmh: 50, riskLevel: 'LOW', reason: '', sourceRule: '', timestamp: 500, evidence: [] },
      sensorReadings: {},
      sensors: {},
      worldObjects: [
        { id: 'obs_1', type: 'StaticObstacle', positionMeters: 50, worldPositionMeters: 60, lane: 'CENTER', details: 'severity=HIGH' }
      ],
      sensorRanges: { cameraRangeMeters: 100, radarRangeMeters: 130, blindSpotRangeMeters: 25, cameraRateMillis: 200, radarRateMillis: 50 }
    });
    fixture.detectChanges();

    const egoTag = fixture.nativeElement.querySelector('.ego__tag');
    expect(egoTag).toBeTruthy();
    expect(egoTag.textContent.trim()).toBe('EGO');
    expect(fixture.nativeElement.textContent).not.toContain('EGOCRUISING');

    const obstacle = fixture.nativeElement.querySelector('[data-testid="road-obstacle"]');
    expect(obstacle).toBeTruthy('Obstacle must have data-testid="road-obstacle"');
  });

  describe('sensor geometry follows the ego vehicle', () => {
    function sensorSnapshot(ego: Record<string, unknown>, sensors: Record<string, unknown> = {}) {
      return {
        simulationTimeMillis: 1000,
        status: 'RUNNING',
        egoVehicle: {
          speedKmh: 60, positionMeters: 100, lane: 'RIGHT', lateralPositionMeters: 3.5,
          accelerationMps2: 0, targetLane: 'RIGHT', laneChangeState: 'IDLE', laneChangeProgress: 1,
          ...ego
        },
        environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
        speedAdvisor: { currentKmh: 60, legalLimitKmh: 120, recommendedKmh: 60, action: 'MAINTAIN_SPEED' },
        currentDecision: { action: 'MAINTAIN_SPEED', recommendedSpeedKmh: 60, riskLevel: 'LOW', reason: '', sourceRule: '', timestamp: 1000, evidence: [] },
        sensorReadings: {},
        sensors,
        worldObjects: [],
        workingMemory: [], recentTimeline: [], recentRules: [], recentFacts: [],
        activeStage: 'SENSORS', tickMillis: 100,
        detectedObjectIds: [], cameraDetectedObjectIds: [], radarDetectedObjectIds: [],
        sensorRanges: { cameraRangeMeters: 120, radarRangeMeters: 130, blindSpotRangeMeters: 30, cameraRateMillis: 200, radarRateMillis: 100 },
        demoPhases: []
      };
    }

    function geometry() {
      return (component as unknown as { sensorGeometry: () => SensorGeometry }).sensorGeometry();
    }

    it('starts the camera cone and the radar corridor at the ego lane when driving on the right', () => {
      fixture.componentRef.setInput('snapshot', sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 }));
      fixture.detectChanges();

      const g = geometry();
      const firstPoint = g.cameraPoints.split(' ')[0].split(',').map(Number);
      expect(firstPoint[1]).toBeCloseTo(71, 1);          // egoY 75 − 4
      expect(g.radar.y).toBeCloseTo(65, 1);              // egoY 75 − 10
      expect(g.radar.height).toBe(20);
      expect(g.radar.x).toBeCloseTo(23, 1);              // car's nose: egoX 20 + 3
      expect(g.radar.width).toBeCloseTo(69.2, 0);        // radar edge 20 + 130/180·100 = 92.2, minus nose 23
    });

    it('draws a blind-spot pad only on sides that have a lane', () => {
      fixture.componentRef.setInput('snapshot', sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 }));
      fixture.detectChanges();
      expect(geometry().blindLeft).not.toBeNull();
      expect(geometry().blindRight).toBeNull();

      fixture.componentRef.setInput('snapshot', sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: 0 }));
      fixture.detectChanges();
      expect(geometry().blindLeft).not.toBeNull();
      expect(geometry().blindRight).not.toBeNull();
      expect(geometry().blindLeft!.y).toBeCloseTo(16, 1);   // (50 − 25) − 9
      expect(geometry().blindRight!.y).toBeCloseTo(66, 1);  // (50 + 25) − 9
    });

    it('blind-spot pads span the full sensor range behind and ahead of the car', () => {
      fixture.componentRef.setInput('snapshot', sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: 0, positionMeters: 100 }));
      fixture.detectChanges();

      const g = geometry();
      expect(g.blindLeft!.x).toBeCloseTo(3.3, 0);        // egoX 20 − 30/180·100
      expect(g.blindLeft!.width).toBeCloseTo(33.3, 0);   // 2 · 30/180·100
    });

    it('slides every shape with the lateral position during a lane change', () => {
      fixture.componentRef.setInput('snapshot', sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: 1.75, targetLane: 'RIGHT', laneChangeState: 'CHANGING', laneChangeProgress: 0.5 }));
      fixture.detectChanges();

      const g = geometry();
      expect(g.radar.y).toBeCloseTo(52.5, 1);            // egoY 62.5 − 10
      const firstPoint = g.cameraPoints.split(' ')[0].split(',').map(Number);
      expect(firstPoint[1]).toBeCloseTo(58.5, 1);        // egoY 62.5 − 4
    });

    it('uses the blind-spot telemetry to hide the road-edge side and to flag an occupied side', () => {
      const telemetry = {
        blindSpotLeft: { rangeMeters: 30, occupied: true, targetId: 'v1', distanceMeters: 4, status: 'OCCUPIED' },
        blindSpotRight: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'NO LANE' }
      };
      fixture.componentRef.setInput('snapshot', sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 }, telemetry));
      fixture.detectChanges();

      expect(geometry().blindLeft!.occupied).toBeTrue();
      expect(geometry().blindRight).toBeNull();
    });

    it('renders the sensor fields as SVG shapes with stable test ids', () => {
      const telemetry = {
        blindSpotLeft: { rangeMeters: 30, occupied: true, targetId: 'v1', distanceMeters: 4, status: 'OCCUPIED' },
        blindSpotRight: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'NO LANE' }
      };
      fixture.componentRef.setInput('snapshot', sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 }, telemetry));
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      const camera = host.querySelector('[data-testid="canvas-sensor-camera"]') as SVGPolygonElement;
      const radar = host.querySelector('[data-testid="canvas-sensor-radar"]') as SVGRectElement;
      const blindLeft = host.querySelector('[data-testid="canvas-sensor-blind-left"]') as SVGRectElement;

      expect(camera).toBeTruthy('camera cone polygon must exist');
      expect(camera.getAttribute('points')!.split(' ').length).toBe(6);
      expect(parseFloat(radar.getAttribute('y')!)).toBeCloseTo(65, 1);
      expect(blindLeft.classList.contains('sensor-blind--occupied')).toBeTrue();
      expect(host.querySelector('[data-testid="canvas-sensor-blind-right"]')).toBeNull();
      expect(host.querySelector('[data-testid="radar-fov"]')).toBeTruthy('radar range edge keeps its test id');
      expect(host.querySelector('.sensor-zone')).toBeNull('old static zones must be gone');
    });

    it('keeps a blind-spot pad on the asphalt while the car drifts towards an outer lane', () => {
      const clear = {
        blindSpotLeft: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'CLEAR' },
        blindSpotRight: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'CLEAR' }
      };
      fixture.componentRef.setInput('snapshot', sensorSnapshot({ lane: 'CENTER', lateralPositionMeters: 2.5, targetLane: 'RIGHT', laneChangeState: 'CHANGING' }, clear));
      fixture.detectChanges();

      const right = geometry().blindRight;
      expect(right).not.toBeNull();
      expect(right!.y + right!.height).toBeLessThanOrEqual(88.5);
    });

    it('drops the road-edge pad even when the telemetry reports that side as CLEAR', () => {
      const clear = {
        blindSpotLeft: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'CLEAR' },
        blindSpotRight: { rangeMeters: 30, occupied: false, targetId: 'none', distanceMeters: Infinity, status: 'CLEAR' }
      };
      fixture.componentRef.setInput('snapshot', sensorSnapshot({ lane: 'RIGHT', lateralPositionMeters: 3.5 }, clear));
      fixture.detectChanges();

      expect(geometry().blindRight).toBeNull();
      expect(geometry().blindLeft).not.toBeNull();
    });

    it('draws no sensor layer before the first snapshot arrives', () => {
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      expect(host.querySelector('[data-testid="canvas-sensor-layer"]')).toBeNull();
      expect(host.querySelector('[data-testid="camera-fov"]')).toBeNull('no "CAMERA FOV (0 m)" edge at the car');
      expect(host.querySelector('[data-testid="radar-fov"]')).toBeNull('no "FRONT RADAR FOV (0 m)" edge at the car');
    });

    it('shortens the camera cone to the effective range and marks the range lost to rain', () => {
      fixture.componentRef.setInput('snapshot', sensorSnapshot(
        { lane: 'RIGHT', lateralPositionMeters: 3.5, positionMeters: 100 },
        { environment: { condition: 'RAIN', effectiveRangeMeters: 84, gripPercent: 65, rain: true } }
      ));
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      const edge = host.querySelector('[data-testid="camera-fov"]') as HTMLElement;
      const ghost = host.querySelector('[data-testid="camera-fov-nominal"]') as HTMLElement;
      const lost = host.querySelector('[data-testid="canvas-sensor-camera-lost"]') as SVGRectElement;

      // ego at 20% of a 180 m viewport: 84 m ahead → 20 + 84/180·100 = 66.7%, 120 m → 86.7%
      expect(parseFloat(edge.style.left)).toBeCloseTo(66.7, 0);
      expect(parseFloat(ghost.style.left)).toBeCloseTo(86.7, 0);
      expect(edge.textContent).toContain('CAMERA 84 m (120 × 70%)');
      expect(lost).toBeTruthy('the band between 84 m and 120 m must be drawn');
      expect(parseFloat(lost.getAttribute('x')!)).toBeCloseTo(66.7, 0);
      expect(parseFloat(lost.getAttribute('width')!)).toBeCloseTo(20, 0);
    });

    it('draws the full camera cone and no lost band in clear weather', () => {
      fixture.componentRef.setInput('snapshot', sensorSnapshot(
        { lane: 'RIGHT', lateralPositionMeters: 3.5 },
        { environment: { condition: 'CLEAR', effectiveRangeMeters: 120, gripPercent: 100, rain: false } }
      ));
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      expect(host.querySelector('[data-testid="canvas-sensor-camera-lost"]')).toBeNull();
      expect(host.querySelector('[data-testid="camera-fov-nominal"]')).toBeNull();
      expect((host.querySelector('[data-testid="camera-fov"]') as HTMLElement).textContent).toContain('CAMERA FOV (120 m)');
    });

    it('never lets the camera cone fold behind the nose when the effective range is tiny', () => {
      fixture.componentRef.setInput('snapshot', sensorSnapshot(
        { lane: 'RIGHT', lateralPositionMeters: 3.5 },
        { environment: { condition: 'RAIN', effectiveRangeMeters: 2, gripPercent: 65, rain: true } }
      ));
      fixture.detectChanges();

      const xs = geometry().cameraPoints.split(' ').map((p) => Number(p.split(',')[0]));
      for (const x of xs) {
        expect(x).toBeGreaterThanOrEqual(23 - 1e-9);   // nose = egoX 20 + 3
      }
    });
  });

  it('renders a passed object behind the car with the PASSED label and the passed class', () => {
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 3000,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 60, positionMeters: 120, lane: 'RIGHT', lateralPositionMeters: 3.5, accelerationMps2: 0 },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
      speedAdvisor: { currentKmh: 60, legalLimitKmh: 120, recommendedKmh: 60, action: 'MAINTAIN_SPEED' },
      currentDecision: { action: 'MAINTAIN_SPEED', recommendedSpeedKmh: 60, riskLevel: 'LOW', reason: '', sourceRule: '', timestamp: 3000, evidence: [] },
      sensorReadings: {},
      sensors: {},
      worldObjects: [
        { id: 'r18-RIGHT', type: 'Pothole', positionMeters: -12, worldPositionMeters: 108, lane: 'RIGHT', details: 'severity=HIGH' }
      ],
      detectedObjectIds: ['r18-RIGHT'], cameraDetectedObjectIds: ['r18-RIGHT'], radarDetectedObjectIds: [],
      sensorRanges: { cameraRangeMeters: 120, radarRangeMeters: 130, blindSpotRangeMeters: 30, cameraRateMillis: 200, radarRateMillis: 100 }
    });
    fixture.detectChanges();

    const pothole = fixture.nativeElement.querySelector('[data-testid="road-pothole"]') as HTMLElement;
    expect(pothole).toBeTruthy('a pothole 12 m behind the car is still rendered');
    expect(pothole.classList.contains('obj--passed')).toBeTrue();
    expect(pothole.textContent).toContain('PASSED');
    expect(pothole.querySelector('.obj__badge')).toBeNull('a passed object keeps no DETECTED badge');
  });

  it('shows the rain chip with the visibility and grip the backend is using', () => {
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 2000,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 52, positionMeters: 80, lane: 'RIGHT', lateralPositionMeters: 3.5, accelerationMps2: 0 },
      environment: { weatherCondition: 'RAIN', visibilityFactor: 0.7, gripFactor: 0.65 },
      speedAdvisor: { currentKmh: 52, legalLimitKmh: 120, recommendedKmh: 52, action: 'REDUCE_SPEED' },
      currentDecision: { action: 'REDUCE_SPEED', recommendedSpeedKmh: 52, riskLevel: 'HIGH', reason: '', sourceRule: '', timestamp: 2000, evidence: [] },
      sensorReadings: {},
      sensors: { environment: { condition: 'RAIN', effectiveRangeMeters: 84, gripPercent: 99, rain: true } },
      worldObjects: [],
      detectedObjectIds: [], cameraDetectedObjectIds: [], radarDetectedObjectIds: [],
      sensorRanges: { cameraRangeMeters: 120, radarRangeMeters: 130, blindSpotRangeMeters: 30, cameraRateMillis: 200, radarRateMillis: 100 }
    });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.road--rain')).toBeTruthy();
    expect(host.querySelector('[data-testid="rain-overlay"]')).toBeTruthy();
    const chip = host.querySelector('[data-testid="rain-chip"]') as HTMLElement;
    expect(chip.textContent).toContain('VIS 70%');
    expect(chip.textContent).toContain('GRIP 65%');

    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 2100,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 52, positionMeters: 81, lane: 'RIGHT', lateralPositionMeters: 3.5, accelerationMps2: 0 },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
      speedAdvisor: { currentKmh: 52, legalLimitKmh: 120, recommendedKmh: 52, action: 'MAINTAIN_SPEED' },
      currentDecision: { action: 'MAINTAIN_SPEED', recommendedSpeedKmh: 52, riskLevel: 'LOW', reason: '', sourceRule: '', timestamp: 2100, evidence: [] },
      sensorReadings: {},
      sensors: {},
      worldObjects: [],
      detectedObjectIds: [], cameraDetectedObjectIds: [], radarDetectedObjectIds: [],
      sensorRanges: { cameraRangeMeters: 120, radarRangeMeters: 130, blindSpotRangeMeters: 30, cameraRateMillis: 200, radarRateMillis: 100 }
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="rain-overlay"]')).toBeNull();
    expect(host.querySelector('[data-testid="rain-chip"]')).toBeNull();
    expect(host.querySelector('.road--rain')).toBeNull();
  });
});
