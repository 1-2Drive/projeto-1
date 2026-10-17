import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiLaneCheckView, ApiProposalView, ApiSimulationSnapshot } from '../../core/models/api.models';

type RiskLevel = 'risk-low' | 'risk-medium' | 'risk-high';
type ActionTone = 'go' | 'caution' | 'stop';

/** What the speed (longitudinal) channel of the decision settled on. */
interface SpeedChannel {
  action: string;
  recommendedSpeedKmh: number;
  sourceRule: string;
}

/** What the lane (lateral) channel of the decision settled on. */
interface LaneChannel {
  action: string;
  sourceRule: string;
}

/** A proposal row ready for display: the outcome explained in words. */
interface ProposalRow extends ApiProposalView {
  outcomeLabel: string;
  outcomeClass: string;
  detailLabel: string | null;
}

const OUTCOME_LABELS: Record<ApiProposalView['outcome'], string> = {
  WON: 'WON',
  LOST_TO: 'LOST',
  VETOED_BY: 'VETOED',
  REJECTED_BY: 'REJECTED',
  PENDING: 'PENDING'
};

@Component({
  selector: 'app-decision-panel',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './decision-panel.component.html',
  styleUrl: './decision-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DecisionPanelComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);

  protected readonly decision = computed(() => this.snapshot()?.currentDecision ?? null);

  /** Which rule engine reached the decision. */
  protected readonly source = computed(() =>
    this.snapshot()?.expertBackend === 'PROLOG' ? 'SWI-Prolog · swipl' : 'Drools · KieSession');

  /**
   * True before the first decision cycle ever ran (fresh load, or after RESET). The
   * backend still ships its untouched default decision there, so the panel shows its
   * own idle state instead of presenting that placeholder as a real conclusion.
   */
  protected readonly idle = computed(() => {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return true;
    }
    return snapshot.status === 'STOPPED' && snapshot.simulationTimeMillis === 0;
  });

  protected readonly riskClass = computed<RiskLevel>(() => {
    const risk = (this.decision()?.riskLevel ?? 'LOW').toUpperCase();
    if (risk.includes('CRITICAL') || risk.includes('HIGH')) {
      return 'risk-high';
    }
    if (risk.includes('MODERATE') || risk.includes('MEDIUM')) {
      return 'risk-medium';
    }
    return 'risk-low';
  });

  /**
   * Colour of the decision card. It mirrors `DrivingAction`: stopping actions are red,
   * actions that slow the vehicle down are amber, progressing actions are green.
   */
  protected readonly tone = computed<ActionTone>(() => {
    const action = (this.decision()?.action ?? '').toUpperCase();
    if (action.includes('EMERGENCY') || action === 'STOP') {
      return 'stop';
    }
    if (action.includes('REDUCE') || action.includes('BRAKE') || action.includes('WAIT')) {
      return 'caution';
    }
    return 'go';
  });

  protected readonly speedGapKmh = computed(() => {
    const decision = this.decision();
    const egoSpeed = this.snapshot()?.egoVehicle.speedKmh ?? 0;
    if (!decision) {
      return 0;
    }
    return Math.round((egoSpeed - decision.recommendedSpeedKmh) * 10) / 10;
  });

  /**
   * Speed channel of the decision. Snapshots from before the two-channel arbitration only
   * carry the headline fields, which then describe the speed action, so they are the fallback.
   */
  protected readonly speedChannel = computed<SpeedChannel | null>(() => {
    const decision = this.decision();
    if (!decision) {
      return null;
    }
    return {
      action: decision.speedAction ?? decision.action,
      recommendedSpeedKmh: decision.recommendedSpeedKmh,
      sourceRule: decision.speedSourceRule ?? decision.sourceRule
    };
  });

  /** Lane channel of the decision; falls back to the headline fields like {@link speedChannel}. */
  protected readonly laneChannel = computed<LaneChannel | null>(() => {
    const decision = this.decision();
    if (!decision) {
      return null;
    }
    return {
      action: decision.laneAction ?? decision.action,
      sourceRule: decision.laneSourceRule ?? decision.sourceRule
    };
  });

  /** Whether the "why / why not" proposal list is expanded. Not persisted on purpose. */
  protected readonly proposalsOpen = signal(false);

  protected readonly proposals = computed<ProposalRow[]>(() =>
    (this.decision()?.proposals ?? []).map(proposal => ({
      ...proposal,
      outcomeLabel: OUTCOME_LABELS[proposal.outcome] ?? proposal.outcome,
      outcomeClass: `outcome--${proposal.outcome.toLowerCase().replace(/_/g, '-')}`,
      detailLabel: describeDetail(proposal)
    })));

  /** "Why not change lane?", left first, as the engine answered it after the rules fired. */
  protected readonly laneChecks = computed<ApiLaneCheckView[]>(() =>
    [...(this.decision()?.laneChecks ?? [])].sort((a, b) => a.side.localeCompare(b.side)));

  protected toggleProposals(): void {
    this.proposalsOpen.update(open => !open);
  }

  protected readonly previousDecision = computed(() => this.snapshot()?.inference?.previousDecision ?? null);
  protected readonly decisionChangedAtMillis = computed(() => this.snapshot()?.inference?.decisionChangedAtMillis ?? 0);

  protected readonly decisionAge = computed(() => {
    const snapshot = this.snapshot();
    const changedAt = this.decisionChangedAtMillis();
    if (!snapshot || !changedAt) {
      return '—';
    }
    return `${((snapshot.simulationTimeMillis - changedAt) / 1000).toFixed(3)}s`;
  });
}

/** Turns the arbitration detail into the "why not" phrase shown on a proposal row. */
function describeDetail(proposal: ApiProposalView): string | null {
  const detail = proposal.detail?.trim();
  if (!detail) {
    return null;
  }
  switch (proposal.outcome) {
    case 'LOST_TO':
      return `lost to ${detail}`;
    case 'VETOED_BY':
      return `vetoed by ${detail}`;
    case 'REJECTED_BY':
      return `actuator: ${detail}`;
    default:
      return detail;
  }
}
