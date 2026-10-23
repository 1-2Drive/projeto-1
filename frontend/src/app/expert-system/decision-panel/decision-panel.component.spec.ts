import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DecisionPanelComponent } from './decision-panel.component';
import { ApiDecisionView, ApiProposalView } from '../../core/models/api.models';

describe('DecisionPanelComponent', () => {
  let fixture: ComponentFixture<DecisionPanelComponent>;

  const headlineDecision: ApiDecisionView = {
    action: 'EMERGENCY_BRAKE',
    recommendedSpeedKmh: 0,
    riskLevel: 'HIGH',
    reason: 'Persistent collision risk.',
    sourceRule: 'Emergency brake when persistent TTC is critical',
    timestamp: 0,
    evidence: []
  };

  function snapshotWith(decision: ApiDecisionView): unknown {
    return {
      simulationTimeMillis: 0,
      status: 'RUNNING',
      egoVehicle: { speedKmh: 20, positionMeters: 0, lane: 'CENTER', accelerationMps2: -5 },
      environment: { weatherCondition: 'RAIN', visibilityFactor: 0.5, gripFactor: 0.5 },
      speedAdvisor: { currentKmh: 20, legalLimitKmh: 50, recommendedKmh: 0, action: 'EMERGENCY_BRAKE' },
      currentDecision: decision,
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
    };
  }

  function render(decision: ApiDecisionView): HTMLElement {
    fixture.componentRef.setInput('snapshot', snapshotWith(decision));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(root: HTMLElement, testId: string): string {
    const element = root.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
    expect(element).withContext(`element [data-testid="${testId}"]`).not.toBeNull();
    return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DecisionPanelComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(DecisionPanelComponent);
  });

  it('answers why not change lane for each side, left first', () => {
    const root = render({
      ...headlineDecision,
      laneChecks: [
        { side: 'RIGHT', allowed: false, explanation: 'no lane to the right', subGoals: [
          { goal: 'maneuverIdle', holds: true, detail: 'no lane change in progress' },
          { goal: 'noContinuousLine', holds: false, detail: 'no lane to the right' },
          { goal: 'sideClear', holds: false, detail: 'blind-spot sensor reports the right side occupied' }] },
        { side: 'LEFT', allowed: true, explanation: 'safe to change left', subGoals: [
          { goal: 'maneuverIdle', holds: true, detail: 'no lane change in progress' },
          { goal: 'noContinuousLine', holds: true, detail: 'no continuous line between RIGHT and CENTER' },
          { goal: 'sideClear', holds: true, detail: 'blind-spot sensor reports the left side clear' }] }
      ]
    });

    const rows = Array.from(root.querySelectorAll('[data-testid=\'decision-lane-check\']')) as HTMLElement[];
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('LEFT');
    expect(rows[0].classList.contains('lane-check--blocked')).toBeFalse();
    expect(rows[1].classList.contains('lane-check--blocked')).toBeTrue();
    expect(rows[1].querySelector('[data-testid=\'decision-lane-check-why\']')?.textContent?.trim())
      .toBe('no lane to the right');
    expect(rows[1].querySelectorAll('.lane-check__goal--fails').length).toBe(2);
  });

  it('highlights emergency decision row', () => {
    const root = render(headlineDecision);

    const panel = root.querySelector('.decision') as HTMLElement;
    expect(panel.classList.contains('decision--stop')).toBeTrue();
  });

  it('renders the speed and lane channel lines from the channel fields', () => {
    const root = render({
      ...headlineDecision,
      action: 'CHANGE_LANE_LEFT',
      recommendedSpeedKmh: 70,
      sourceRule: 'Overtake slow vehicle on the left',
      speedAction: 'MAINTAIN_SPEED',
      speedReason: 'No speed constraint ahead.',
      speedSourceRule: 'Maintain cruise speed',
      laneAction: 'CHANGE_LANE_LEFT',
      laneReason: 'Slow vehicle ahead.',
      laneSourceRule: 'Overtake slow vehicle on the left'
    });

    expect(text(root, 'decision-action')).toBe('CHANGE_LANE_LEFT');

    expect(text(root, 'decision-speed-action')).toBe('MAINTAIN_SPEED');
    expect(text(root, 'decision-speed-channel')).toContain('70 km/h');
    expect(text(root, 'decision-speed-rule')).toBe('— Maintain cruise speed');

    expect(text(root, 'decision-lane-action')).toBe('CHANGE_LANE_LEFT');
    expect(text(root, 'decision-lane-rule')).toBe('— Overtake slow vehicle on the left');
  });

  it('falls back to the headline fields when the channel fields are absent', () => {
    const root = render(headlineDecision);

    expect(text(root, 'decision-speed-action')).toBe('EMERGENCY_BRAKE');
    expect(text(root, 'decision-speed-rule')).toContain('Emergency brake when persistent TTC is critical');
    expect(text(root, 'decision-lane-action')).toBe('EMERGENCY_BRAKE');
    expect(text(root, 'decision-lane-rule')).toContain('Emergency brake when persistent TTC is critical');
  });

  describe('proposals', () => {
    const proposals: ApiProposalView[] = [
      {
        rule: 'Emergency brake when persistent TTC is critical',
        action: 'EMERGENCY_BRAKE',
        channel: 'LONGITUDINAL',
        priority: 70,
        recommendedSpeedKmh: 0,
        outcome: 'WON',
        detail: '',
        reason: 'Persistent collision risk.'
      },
      {
        rule: 'Maintain cruise speed',
        action: 'MAINTAIN_SPEED',
        channel: 'LONGITUDINAL',
        priority: 10,
        recommendedSpeedKmh: 50,
        outcome: 'LOST_TO',
        detail: 'Emergency brake when persistent TTC is critical',
        reason: 'No speed constraint ahead.'
      },
      {
        rule: 'Overtake slow vehicle on the left',
        action: 'CHANGE_LANE_LEFT',
        channel: 'LATERAL',
        priority: 30,
        recommendedSpeedKmh: null,
        outcome: 'VETOED_BY',
        detail: 'Emergency brake when persistent TTC is critical',
        reason: 'Slow vehicle ahead.'
      },
      {
        rule: 'Return to the right lane',
        action: 'CHANGE_LANE_RIGHT',
        channel: 'LATERAL',
        priority: 20,
        recommendedSpeedKmh: null,
        outcome: 'REJECTED_BY',
        detail: 'lane change already in progress',
        reason: 'Right lane is free.'
      }
    ];

    function toggle(root: HTMLElement): void {
      (root.querySelector('[data-testid="decision-proposals-toggle"]') as HTMLButtonElement).click();
      fixture.detectChanges();
    }

    it('starts collapsed and expands on toggle', () => {
      const root = render({ ...headlineDecision, proposals });

      expect(text(root, 'decision-proposals-toggle')).toContain('4');
      expect(root.querySelector('[data-testid="decision-proposals-list"]')).toBeNull();

      toggle(root);
      expect(root.querySelectorAll('[data-testid="decision-proposal-row"]').length).toBe(4);

      toggle(root);
      expect(root.querySelector('[data-testid="decision-proposals-list"]')).toBeNull();
    });

    it('renders an outcome badge and the arbitration detail per proposal', () => {
      const root = render({ ...headlineDecision, proposals });
      toggle(root);

      const rows = Array.from(root.querySelectorAll('[data-testid="decision-proposal-row"]')) as HTMLElement[];
      const outcomes = rows.map(row =>
        (row.querySelector('[data-testid="decision-proposal-outcome"]') as HTMLElement).textContent?.trim());
      const details = rows.map(row =>
        row.querySelector('[data-testid="decision-proposal-detail"]')?.textContent?.trim() ?? null);

      expect(outcomes).toEqual(['WON', 'LOST', 'VETOED', 'REJECTED']);
      expect(rows.map(row => row.className)).toEqual([
        'proposal outcome--won',
        'proposal outcome--lost-to',
        'proposal outcome--vetoed-by',
        'proposal outcome--rejected-by'
      ]);
      expect(details).toEqual([
        null,
        'lost to Emergency brake when persistent TTC is critical',
        'vetoed by Emergency brake when persistent TTC is critical',
        'actuator: lane change already in progress'
      ]);

      const won = rows[0].textContent?.replace(/\s+/g, ' ').trim();
      expect(won).toContain('SPEED');
      expect(won).toContain('EMERGENCY_BRAKE 0 km/h');
      expect(won).toContain('p70');

      const vetoed = rows[2].textContent?.replace(/\s+/g, ' ').trim();
      expect(vetoed).toContain('LANE');
      expect(vetoed).not.toContain('km/h');
    });

    it('shows an empty state when the decision carries no proposals', () => {
      const root = render(headlineDecision);
      toggle(root);

      expect(root.querySelectorAll('[data-testid="decision-proposal-row"]').length).toBe(0);
      expect(root.querySelector('.proposals__empty')).not.toBeNull();
    });
  });
});
