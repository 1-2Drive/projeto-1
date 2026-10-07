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

  it('should render physical speed radar station with dish, camera, and speed limit badge', () => {
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
        { id: 'radar_1', type: 'SpeedRadar', positionMeters: 130, lane: 'RIGHT', details: 'speedLimitKmh=50' }
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
});
