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

  it('lists legal limit, recommended speed and delta as three separate columns, label above value', () => {
    fixture.componentRef.setInput('snapshot', {
      simulationTimeMillis: 1000,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 50, positionMeters: 0, lane: 'CENTER', accelerationMps2: 0 },
      speedAdvisor: { currentKmh: 50, legalLimitKmh: 50, recommendedKmh: 50, action: 'MAINTAIN_SPEED' }
    });
    fixture.detectChanges();

    const legend = fixture.nativeElement.querySelector('.gauge__legend') as HTMLElement;
    const columns = Array.from(legend.children) as HTMLElement[];
    expect(columns.length).toBe(3);
    expect(columns.map((c) => (c.firstChild!.textContent ?? '').trim())).toEqual(['Legal limit', 'Recommended', 'Delta']);
    expect(columns.map((c) => c.querySelector('strong')!.textContent!.trim())).toEqual(['50 km/h', '50 km/h', '0 km/h']);

    // Each metric is its own column of the legend grid, and the columns never overlap.
    expect(getComputedStyle(legend).display).toBe('grid');
    const boxes = columns.map((c) => c.getBoundingClientRect());
    for (let i = 1; i < boxes.length; i++) {
      expect(boxes[i].left).toBeGreaterThanOrEqual(boxes[i - 1].right);
    }
    // The value sits below its label in the same column.
    for (const column of columns) {
      const label = document.createRange();
      label.selectNodeContents(column.firstChild!);
      expect(column.querySelector('strong')!.getBoundingClientRect().top).toBeGreaterThanOrEqual(label.getBoundingClientRect().bottom - 1);
    }
  });
});
