import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RoadCanvasComponent } from './road-canvas.component';

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
});
