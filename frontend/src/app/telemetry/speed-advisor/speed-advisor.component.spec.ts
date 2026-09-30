import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SpeedAdvisorComponent } from './speed-advisor.component';

describe('SpeedAdvisorComponent', () => {
  let fixture: ComponentFixture<SpeedAdvisorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SpeedAdvisorComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(SpeedAdvisorComponent);
  });

  it('shows above class when current speed is above recommendation', () => {
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 0,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 80, positionMeters: 0, lane: 'CENTER', accelerationMps2: 0 },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
      speedAdvisor: { currentKmh: 80, legalLimitKmh: 50, recommendedKmh: 50, action: 'REDUCE_SPEED' },
      currentDecision: {
        action: 'REDUCE_SPEED',
        recommendedSpeedKmh: 50,
        riskLevel: 'MODERATE',
        reason: '',
        sourceRule: '',
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

    const legend = fixture.nativeElement.querySelector('.legend--delta') as HTMLElement;
    expect(legend.classList.contains('legend--above')).toBeTrue();
  });
});
