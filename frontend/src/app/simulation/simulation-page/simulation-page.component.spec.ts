import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { SimulationPageComponent } from './simulation-page.component';
import { SimulationStateService } from '../../core/state/simulation-state.service';
import { BackendApiService } from '../../core/api/backend-api.service';
import { of } from 'rxjs';

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

  expertBackend = signal('DROOLS');
  expertBackendError = signal(null);
  stoppedSessionId = signal<string | null>(null);
  stopPersistenceError = signal<string | null>(null);
  selectedHistoricalTimestamp = signal<number | null>(null);
  selectedHistoricalSessionId = signal<string | null>(null);
  historyVersion = signal(0);

  scenarios = signal([]);

  initialize = jasmine.createSpy('initialize');
  destroy = jasmine.createSpy('destroy');
  start = jasmine.createSpy('start');
  pause = jasmine.createSpy('pause');
  resume = jasmine.createSpy('resume');
  stop = jasmine.createSpy('stop');
  clearStoppedSession = jasmine.createSpy('clearStoppedSession');
  openHistoricalSession = jasmine.createSpy('openHistoricalSession');
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
      providers: [
        { provide: SimulationStateService, useClass: SimulationStateServiceStub },
        { provide: BackendApiService, useValue: { getSessions: () => of([]), getSession: () => of({ decisionSnapshots: [] }) } }
      ]
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

  it('keeps the dashboard cells: road, evidence tabs, telemetry and the road map / toolbox tabs', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.workspace__center app-road-canvas')).toBeTruthy();
    const tabs = Array.from(el.querySelectorAll('.evidence .tabs > [role="tab"]')).map((t) => t.textContent!.trim());
    expect(tabs).toEqual(['Inference Timeline', 'Working Memory', 'Explanation', 'World State', 'Decision', 'Situation']);
    expect(el.querySelector('app-speed-advisor')).toBeTruthy();
    expect(el.querySelector('app-sensors-panel')).toBeTruthy();

    // The road map is the default bottom tab; the toolbox and scenario library sit in the other.
    expect(el.querySelector('app-road-map')).toBeTruthy();
    expect(el.querySelector('app-toolbox')).toBeNull();
    (el.querySelectorAll('.bottom-tabs__nav [role="tab"]')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('app-toolbox')).toBeTruthy();
    expect(el.querySelector('app-scenario-library')).toBeTruthy();
    expect(el.querySelector('app-road-map')).toBeNull();
  });

  it('shows the situation narration and the decision panel from their evidence tabs', () => {
    const el = fixture.nativeElement as HTMLElement;
    const tab = (name: string) => Array.from(el.querySelectorAll('.evidence .tabs > [role="tab"]'))
      .find((t) => t.textContent!.trim() === name) as HTMLButtonElement;
    tab('Situation').click();
    fixture.detectChanges();
    expect(el.querySelector('app-situation-panel')).toBeTruthy();
    tab('Decision').click();
    fixture.detectChanges();
    expect(el.querySelector('app-decision-panel')).toBeTruthy();
    expect(el.querySelector('app-situation-panel')).toBeNull();
  });

  it('calls demo action from demo button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const demo = compiled.querySelector('[data-testid="run-demo-btn"]') as HTMLButtonElement;
    demo.click();

    expect(state.runPresentationDemo).toHaveBeenCalled();
  });

  it('renders the STOP-time question dialog only for the persisted stopped session and closes it cleanly', () => {
    state.stoppedSessionId.set('saved-trip-1');
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[data-testid="trip-questions-dialog"]') as HTMLElement;
    expect(dialog).toBeTruthy();
    expect(dialog.getAttribute('data-session-id')).toBe('saved-trip-1');
    const close = fixture.nativeElement.querySelector('[data-testid="close-trip-dialog"]') as HTMLButtonElement;
    close.click();
    expect(state.clearStoppedSession).toHaveBeenCalled();
  });

  it('opens historical session UI without deleting it when RESET is requested', () => {
    state.stoppedSessionId.set('saved-trip-1');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="open-session-history"]') as HTMLButtonElement).click();
    expect(state.clearStoppedSession).toHaveBeenCalled();
    expect(state.openHistoricalSession).toHaveBeenCalledWith('saved-trip-1');
  });
});
