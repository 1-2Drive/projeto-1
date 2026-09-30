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
      demoPhases: []
    });

    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('50');
    expect(text).toContain('35m');
  });
});
