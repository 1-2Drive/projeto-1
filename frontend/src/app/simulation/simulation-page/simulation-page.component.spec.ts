import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { SimulationPageComponent } from './simulation-page.component';
import { SimulationStateService } from '../../core/state/simulation-state.service';

class SimulationStateServiceStub {
  vm = signal({
    snapshot: null,
    scenarios: [],
    connection: {
      backendConnected: true,
      droolsActive: true,
      simulationStatus: 'STOPPED' as const,
      streamStatus: 'LIVE' as const
    },
    error: null,
    loading: false
  });

  snapshot = signal({
    simulationTimeMillis: 1500,
    status: 'STOPPED' as const,
    egoVehicle: { speedKmh: 0, positionMeters: 0, lane: 'CENTER' as const, accelerationMps2: 0 },
    environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
    speedAdvisor: { currentKmh: 0, legalLimitKmh: 120, recommendedKmh: 0, action: 'MAINTAIN_SPEED' },
    currentDecision: {
      action: 'MAINTAIN_SPEED',
      recommendedSpeedKmh: 0,
      riskLevel: 'LOW',
      reason: 'Stable conditions.',
      sourceRule: 'Default cruise',
      timestamp: 0,
      evidence: []
    },
    sensorReadings: {},
    worldObjects: [],
    workingMemory: [],
    recentTimeline: [],
    recentRules: [],
      recentFacts: [],
      activeStage: 'WORLD',
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

  scenarios = signal([]);

  initialize = jasmine.createSpy('initialize');
  destroy = jasmine.createSpy('destroy');
  start = jasmine.createSpy('start');
  pause = jasmine.createSpy('pause');
  resume = jasmine.createSpy('resume');
  stop = jasmine.createSpy('stop');
  reset = jasmine.createSpy('reset');
  loadScenario = jasmine.createSpy('loadScenario');
  addRoadElement = jasmine.createSpy('addRoadElement');
  addActor = jasmine.createSpy('addActor');
  updateEnvironment = jasmine.createSpy('updateEnvironment');
  clearError = jasmine.createSpy('clearError');
  runPresentationDemo = jasmine.createSpy('runPresentationDemo');
}

describe('SimulationPageComponent', () => {
  let fixture: ComponentFixture<SimulationPageComponent>;
  let state: SimulationStateServiceStub;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SimulationPageComponent],
      providers: [{ provide: SimulationStateService, useClass: SimulationStateServiceStub }]
    }).compileComponents();

    fixture = TestBed.createComponent(SimulationPageComponent);
    state = TestBed.inject(SimulationStateService) as unknown as SimulationStateServiceStub;
    fixture.detectChanges();
  });

  it('disables pause and enables start when stopped', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const start = compiled.querySelector('[data-testid="start-btn"]') as HTMLButtonElement;
    const pause = compiled.querySelector('[data-testid="pause-btn"]') as HTMLButtonElement;

    expect(start.disabled).toBeFalse();
    expect(pause.disabled).toBeTrue();
  });

  it('calls demo action from demo button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const demo = compiled.querySelector('[data-testid="run-demo-btn"]') as HTMLButtonElement;
    demo.click();

    expect(state.runPresentationDemo).toHaveBeenCalled();
  });
});
