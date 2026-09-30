import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DecisionPanelComponent } from './decision-panel.component';

describe('DecisionPanelComponent', () => {
  let fixture: ComponentFixture<DecisionPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DecisionPanelComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(DecisionPanelComponent);
  });

  it('highlights emergency decision row', () => {
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 0,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 20, positionMeters: 0, lane: 'CENTER', accelerationMps2: -5 },
      environment: { weatherCondition: 'RAIN', visibilityFactor: 0.5, gripFactor: 0.5 },
      speedAdvisor: { currentKmh: 20, legalLimitKmh: 50, recommendedKmh: 0, action: 'EMERGENCY_BRAKE' },
      currentDecision: {
        action: 'EMERGENCY_BRAKE',
        recommendedSpeedKmh: 0,
        riskLevel: 'HIGH',
        reason: 'Persistent collision risk.',
        sourceRule: 'Emergency brake when persistent TTC is critical',
        timestamp: 0,
        evidence: []
      },
      sensorReadings: {},
      worldObjects: [],
      workingMemory: [],
      recentTimeline: [],
      recentRules: [],
      recentFacts: [],
      activeStage: 'DECISION',
      tickMillis: 100,
      detectedObjectIds: [],
      cameraDetectedObjectIds: [],
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

    const panel = fixture.nativeElement.querySelector('.decision') as HTMLElement;
    expect(panel.classList.contains('decision--stop')).toBeTrue();
  });
});
