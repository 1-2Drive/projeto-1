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
  setRandomObjectFrequency = jasmine.createSpy('setRandomObjectFrequency');
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

  it('lays the control bar out in three zones with the status row below, then the flow stages', () => {
    const el = fixture.nativeElement as HTMLElement;
    const bar = el.querySelector('nav.controls') as HTMLElement;
    const zones = Array.from(bar.children).map((c) => c.className);
    expect(zones).toEqual(['controls__primary', 'controls__engine', 'controls__demo', 'controls__status']);

    const text = (selector: string) => (bar.querySelector(selector)!.textContent ?? '').replace(/\s+/g, ' ').trim();
    expect(text('.controls__primary')).toContain('START');
    expect(text('.controls__primary')).toContain('PAUSE');
    expect(text('.controls__primary')).toContain('STOP');
    expect(text('.controls__primary')).toContain('RESET');
    expect(text('.controls__engine')).toContain('Rule engine');
    expect(text('.controls__engine')).toContain('Drools');
    expect(text('.controls__engine')).toContain('Prolog');
    expect(text('.controls__demo')).toContain('Automated demo');
    expect(text('.controls__demo')).toContain('RUN DEMO');
    expect(bar.querySelectorAll('.controls__demo button').length).toBe(3);
    expect(bar.querySelectorAll('.controls__status app-status-badge').length).toBe(3);
    expect(bar.querySelector('[data-testid="simulation-status"]')).toBeTruthy();
    expect(bar.querySelector('[data-testid="simulation-clock"]')).toBeTruthy();

    // The flow stages come right after the control bar.
    expect(bar.nextElementSibling!.tagName.toLowerCase()).toBe('app-flow-stage');
  });

  it('lets the speed advisor keep the height of its content instead of the height of the row', () => {
    const el = fixture.nativeElement as HTMLElement;
    const advisor = el.querySelector('.telemetry-pair > app-speed-advisor') as HTMLElement;
    expect(getComputedStyle(advisor).alignSelf).toBe('start');
    expect(getComputedStyle(advisor).height).not.toBe(getComputedStyle(el.querySelector('.telemetry-pair')!).height);
  });

  it('keeps the dashboard cells: road, evidence tabs, telemetry and the road map / toolbox tabs', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.workspace__center app-road-canvas')).toBeTruthy();
    const tabs = Array.from(el.querySelectorAll('.evidence .tabs > [role="tab"]')).map((t) => t.textContent!.trim());
    expect(tabs).toEqual(['Inference Timeline', 'Working Memory', 'Explanation', 'Decision']);
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

  it('shows the decision panel from its evidence tab', () => {
    const el = fixture.nativeElement as HTMLElement;
    const tab = (name: string) => Array.from(el.querySelectorAll('.evidence .tabs > [role="tab"]'))
      .find((t) => t.textContent!.trim() === name) as HTMLButtonElement;
    tab('Decision').click();
    fixture.detectChanges();
    expect(el.querySelector('app-decision-panel')).toBeTruthy();
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
