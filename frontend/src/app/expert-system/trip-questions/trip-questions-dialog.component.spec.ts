import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { BackendApiService } from '../../core/api/backend-api.service';
import { SimulationStateService } from '../../core/state/simulation-state.service';
import { POTHOLE_ANSWER } from '../decision-explanation/decision-explanation.fixture';
import { TripQuestionsDialogComponent } from './trip-questions-dialog.component';

function snapshot(simulationTimeMillis: number, speedAction: string, laneAction: string) {
  return {
    simulationTimeMillis,
    vehicle: { speedKmh: 42, positionMeters: 300, lane: 'RIGHT', accelerationMps2: 0, lateralPositionMeters: 3.5,
      targetLane: 'RIGHT', laneChangeState: 'IDLE', laneChangeProgress: 0 },
    environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
    sensors: { frontCamera: { rangeMeters: 120, detections: [], status: 'CLEAR' }, frontRadar: { targetId: 'none' } },
    worldObjects: [], workingMemory: [], timeline: [], roadMap: null,
    decision: { action: laneAction === 'KEEP_LANE' ? speedAction : laneAction, speedAction, laneAction, reason: '',
      sourceRule: '', speedSourceRule: '', laneSourceRule: '', recommendedSpeedKmh: 50, evidence: [],
      actuatorResult: '', proposals: [], laneChecks: [] }
  };
}

const session = {
  id: 'just-stopped', startedAt: new Date().toISOString(), stoppedAt: new Date().toISOString(),
  durationMillis: 30000, startedAtSimulationMillis: 0, scenarioId: 'S1', backend: 'DROOLS',
  eventCount: 12, decisionCount: 2, initialWorld: [], initialWorldObjects: [], events: [], vehicleSamples: [],
  decisionEventSequences: {},
  decisionSnapshots: [snapshot(12000, 'MAINTAIN_SPEED', 'KEEP_LANE'), snapshot(27700, 'MAINTAIN_SPEED', 'CHANGE_LANE_LEFT')]
} as any;

class ApiMock {
  getSession = jasmine.createSpy().and.returnValue(of(session));
}

class StateMock {
  explainSessionDecision = jasmine.createSpy().and.returnValue(of(POTHOLE_ANSWER));
}

describe('TripQuestionsDialogComponent', () => {
  let fixture: ComponentFixture<TripQuestionsDialogComponent>;
  let api: ApiMock;
  let state: StateMock;
  let element: HTMLElement;

  async function open(): Promise<void> {
    fixture = TestBed.createComponent(TripQuestionsDialogComponent);
    fixture.componentRef.setInput('sessionId', 'just-stopped');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    element = fixture.nativeElement;
  }

  function click(testId: string): void {
    element.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click();
    fixture.detectChanges();
  }

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
  });

  it('explains the latest decision of the session as soon as it opens, without a question to type', async () => {
    await open();
    expect(api.getSession).toHaveBeenCalledOnceWith('just-stopped');
    expect(state.explainSessionDecision).toHaveBeenCalledOnceWith('just-stopped', 27700);
    expect(element.querySelector('textarea')).toBeNull();
    const answer = element.querySelector('[data-testid="trip-answer"]')!;
    expect(answer.querySelectorAll('[data-testid="why-channel"]').length).toBe(2);
    expect(answer.textContent).toContain('Avoid pothole in right lane by changing left');
    expect(element.textContent).not.toContain('SHOW TECHNICAL EVIDENCE');
    expect(element.querySelector('[data-testid="toggle-technical-evidence"]')).toBeNull();
  });

  it('lists every recorded decision with both channels and explains the one picked', async () => {
    await open();
    const select = element.querySelector<HTMLSelectElement>('[data-testid="trip-decision-select"]')!;
    expect(Array.from(select.options).map(option => option.textContent!.trim())).toEqual([
      '00:12.000 — speed MAINTAIN_SPEED · lane KEEP_LANE',
      '00:27.700 — speed MAINTAIN_SPEED · lane CHANGE_LANE_LEFT'
    ]);
    expect(select.value).toBe('27700');
    select.value = '12000';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(state.explainSessionDecision).toHaveBeenCalledWith('just-stopped', 12000);
  });

  it('switches between WHY, WHY NOT and HOW without asking the backend again', async () => {
    await open();
    click('why-not-mode');
    expect(element.querySelector('[data-testid="why-not-select"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="trip-answer"]')!.textContent).toContain('WHY NOT');
    click('how-mode');
    const steps = element.querySelector('[data-testid="how-steps"]')!;
    expect(steps.querySelectorAll('[data-testid="how-flow"]').length).toBe(2);
    expect(steps.textContent).toContain('Lane · lateral');
    expect(state.explainSessionDecision).toHaveBeenCalledTimes(1);
  });

  it('closes on Escape', async () => {
    await open();
    const component = fixture.componentInstance;
    const closed = jasmine.createSpy('close');
    component.close.subscribe(closed);
    element.querySelector('[role="dialog"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(closed).toHaveBeenCalled();
  });

  it('shows an unavailable message when the explanation cannot be loaded', async () => {
    state.explainSessionDecision.and.returnValue(throwError(() => new Error('gone')));
    await open();
    expect(element.querySelector('[data-testid="trip-dialog-error"]')!.textContent).toContain('Unable to explain');
    expect(element.querySelector('[data-testid="trip-answer"]')).toBeNull();
  });

  it('says so when the trip recorded no decision', async () => {
    api.getSession.and.returnValue(of({ ...session, decisionSnapshots: [] }));
    await open();
    expect(state.explainSessionDecision).not.toHaveBeenCalled();
    expect(element.querySelector('[data-testid="trip-dialog-error"]')!.textContent).toContain('no decision');
    expect(element.querySelector<HTMLSelectElement>('[data-testid="trip-decision-select"]')!.disabled).toBeTrue();
  });
});
