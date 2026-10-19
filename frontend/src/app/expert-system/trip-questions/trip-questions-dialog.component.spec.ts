import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { BackendApiService } from '../../core/api/backend-api.service';
import { SimulationStateService } from '../../core/state/simulation-state.service';
import { TripQuestionsDialogComponent } from './trip-questions-dialog.component';

const session = {
  id: 'just-stopped', startedAt: new Date().toISOString(), stoppedAt: new Date().toISOString(),
  durationMillis: 30000, startedAtSimulationMillis: 0, scenarioId: 'S1', backend: 'DROOLS',
  eventCount: 12, decisionCount: 1, initialWorld: [], initialWorldObjects: [], events: [], vehicleSamples: [],
  decisionEventSequences: {},
  decisionSnapshots: [{
    simulationTimeMillis: 27700,
    vehicle: { speedKmh: 60, positionMeters: 300, lane: 'RIGHT', accelerationMps2: 0, lateralPositionMeters: 3.5,
      targetLane: 'RIGHT', laneChangeState: 'IDLE', laneChangeProgress: 0 },
    environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
    sensors: { frontCamera: { rangeMeters: 120, detections: [], status: 'CLEAR' }, frontRadar: { targetId: 'none' } },
    worldObjects: [], workingMemory: [], timeline: [], roadMap: null,
    decision: { action: 'CHANGE_LANE_LEFT', speedAction: 'MAINTAIN_SPEED', laneAction: 'CHANGE_LANE_LEFT',
      reason: 'Pothole', sourceRule: 'Avoid pothole', speedSourceRule: '', laneSourceRule: '', recommendedSpeedKmh: 60,
      evidence: ['pothole 18 m ahead'], actuatorResult: 'steer left', proposals: [], laneChecks: [] }
  }]
} as any;

class ApiMock {
  getSession = jasmine.createSpy().and.returnValue(of(session));
  askWhy = jasmine.createSpy().and.returnValue(of({
    sessionId: 'just-stopped', timestampMillis: 27700, question: 'why', selected: 'CHANGE_LANE_LEFT',
    why: ['pothole 18 m ahead'], whyNot: ['BRAKE was not proposed'], observations: [], facts: [], rules: [], actuator: [], proposals: [], laneSafety: [], engine: 'DROOLS', candidates: [],
    how: [{ stage: 'WORLD', evidence: ['pothole in right lane'] }, { stage: 'SENSORS', evidence: ['camera detected pothole 18 m ahead'] }]
  }));
}

class StateMock {
  askAboutSession = jasmine.createSpy().and.returnValue(of({
    sessionId: 'just-stopped', timestampMillis: 27700, question: 'why', selected: 'CHANGE_LANE_LEFT',
    why: ['pothole 18 m ahead'], whyNot: ['BRAKE was not proposed'], observations: [], facts: [], rules: [], actuator: [], proposals: [], laneSafety: [], engine: 'DROOLS', candidates: [],
    how: [{ stage: 'WORLD', evidence: ['pothole in right lane'] }, { stage: 'SENSORS', evidence: ['camera detected pothole 18 m ahead'] }]
  }));
}

describe('TripQuestionsDialogComponent', () => {
  let fixture: ComponentFixture<TripQuestionsDialogComponent>;
  let api: ApiMock;
  let state: StateMock;

  beforeEach(async () => {
    api = new ApiMock();
    state = new StateMock();
    await TestBed.configureTestingModule({
      imports: [TripQuestionsDialogComponent],
      providers: [
        { provide: BackendApiService, useValue: api },
        { provide: SimulationStateService, useValue: state }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(TripQuestionsDialogComponent);
    fixture.componentRef.setInput('sessionId', 'just-stopped');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('loads the exact session delivered after STOP and submits WHY against it', () => {
    expect(api.getSession).toHaveBeenCalledOnceWith('just-stopped');
    fixture.nativeElement.querySelector('[data-testid="ask-trip-question"]').click();
    fixture.detectChanges();
    expect(state.askAboutSession).toHaveBeenCalledWith('just-stopped', 'Why did the vehicle change lane?', 27700, 'WHY');
    expect(fixture.nativeElement.querySelector('[data-testid="trip-answer"]').textContent).toContain('pothole 18 m ahead');
  });

  it('submits WHY-NOT mode and renders the recorded answer', () => {
    fixture.nativeElement.querySelector('[data-testid="why-not-mode"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="ask-trip-question"]').click();
    fixture.detectChanges();
    expect(state.askAboutSession).toHaveBeenCalledWith('just-stopped', 'Why did the vehicle change lane?', 27700, 'WHY_NOT');
    expect(fixture.nativeElement.querySelector('[data-testid="trip-answer"]').textContent).toContain('BRAKE was not proposed');
  });

  it('submits HOW mode and renders the ordered process steps', () => {
    fixture.nativeElement.querySelector('[data-testid="how-mode"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="ask-trip-question"]').click();
    fixture.detectChanges();
    expect(state.askAboutSession).toHaveBeenCalledWith('just-stopped', 'Why did the vehicle change lane?', 27700, 'HOW');
    const steps = fixture.nativeElement.querySelector('[data-testid="how-steps"]').textContent;
    expect(steps).toContain('WORLD');
    expect(steps).toContain('SENSORS');
    expect(steps).toContain('camera detected pothole 18 m ahead');
  });

  it('opens history and closes on Escape', () => {
    const component = fixture.componentInstance;
    const opened = jasmine.createSpy('openHistory');
    const closed = jasmine.createSpy('close');
    component.openHistory.subscribe(opened);
    component.close.subscribe(closed);
    fixture.nativeElement.querySelector('[data-testid="open-session-history"]').click();
    fixture.detectChanges();
    expect(opened).toHaveBeenCalledOnceWith('just-stopped');
    fixture.nativeElement.querySelector('[role="dialog"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(closed).toHaveBeenCalled();
  });

  it('shows an unavailable message when the saved session query fails', () => {
    state.askAboutSession.and.returnValue(throwError(() => new Error('gone')));
    fixture.nativeElement.querySelector('[data-testid="ask-trip-question"]').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="trip-dialog-error"]').textContent).toContain('Unable to query');
  });
});
