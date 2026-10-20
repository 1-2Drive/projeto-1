import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import {
  AlternativeStatus,
  ApiAlternative,
  ApiChannelFlow,
  ApiCondition,
  ApiRuleRef,
  ApiWhyAnswer,
  WhyMode
} from '../../core/models/api.models';

const STATUS_LABELS: Record<AlternativeStatus, string> = {
  CONDITION_NOT_MET: 'Condition not met',
  OVERRIDDEN_BY_PRIORITY: 'A higher-priority rule prevailed',
  NOT_APPLICABLE: 'Not applicable',
  INSUFFICIENT_DATA: 'Not enough recorded information'
};

const OUTCOME_LABELS: Record<string, string> = {
  WON: 'won',
  LOST_TO: 'lost',
  VETOED_BY: 'vetoed',
  REJECTED_BY: 'refused by the actuator',
  CAPPED: 'capped the speed',
  PENDING: 'pending',
  DEFAULT: 'default',
  VETO: 'veto',
  ACTUATOR: 'actuator',
  RECORDED: 'recorded'
};

/**
 * Presents the backend's explanation of one recorded decision, per channel (speed and lane):
 * WHY it was made, WHY NOT each alternative, and HOW the cycle went through the decision
 * flowchart. It derives no conclusion of its own: every line comes from the answer.
 */
@Component({
  selector: 'app-decision-explanation',
  standalone: true,
  templateUrl: './decision-explanation.component.html',
  styleUrl: './decision-explanation.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DecisionExplanationComponent {
  readonly answer = input.required<ApiWhyAnswer>();
  readonly mode = input<WhyMode>('WHY');

  /** The alternative picked in the WHY NOT dropdown, as "CHANNEL:OPTION". */
  private readonly picked = signal<string | null>(null);

  protected readonly speedAlternatives = computed(() =>
    this.answer().whyNot.filter(alternative => alternative.channel === 'LONGITUDINAL'));
  protected readonly laneAlternatives = computed(() =>
    this.answer().whyNot.filter(alternative => alternative.channel === 'LATERAL'));

  /** The picked alternative, or the first applicable one while none is picked (or it is gone). */
  protected readonly alternative = computed<ApiAlternative | null>(() => {
    const all = this.answer().whyNot;
    const picked = this.picked();
    return all.find(alternative => this.key(alternative) === picked)
      ?? all.find(alternative => alternative.status !== 'NOT_APPLICABLE')
      ?? all[0]
      ?? null;
  });

  protected readonly selectedKey = computed(() => {
    const alternative = this.alternative();
    return alternative ? this.key(alternative) : '';
  });

  protected pick(key: string): void { this.picked.set(key); }

  protected key(alternative: ApiAlternative): string { return `${alternative.channel}:${alternative.option}`; }

  protected optionLabel(alternative: ApiAlternative): string {
    return alternative.status === 'NOT_APPLICABLE' ? `${alternative.label} — not applicable` : alternative.label;
  }

  protected statusLabel(status: AlternativeStatus): string { return STATUS_LABELS[status] ?? status; }

  protected statusClass(status: AlternativeStatus): string { return `status--${status.toLowerCase().replace(/_/g, '-')}`; }

  protected outcomeLabel(rule: ApiRuleRef): string {
    const label = OUTCOME_LABELS[rule.outcome] ?? rule.outcome.toLowerCase();
    if (rule.outcome === 'LOST_TO' && rule.detail) return `lost to ${rule.detail}`;
    if (rule.outcome === 'VETOED_BY' && rule.detail) return `vetoed by ${rule.detail}`;
    return label;
  }

  protected proposed(rule: ApiRuleRef): string {
    return rule.recommendedSpeedKmh === null || rule.recommendedSpeedKmh === undefined
      ? rule.action
      : `${rule.action} at ${Math.round(rule.recommendedSpeedKmh)} km/h`;
  }

  protected mark(condition: ApiCondition): string {
    return condition.holds === true ? '✓' : condition.holds === false ? '✗' : '–';
  }

  protected holdsText(condition: ApiCondition): string {
    return condition.holds === true ? 'holds' : condition.holds === false ? 'does not hold' : 'not recorded';
  }

  protected conditionClass(condition: ApiCondition): string {
    return condition.holds === true ? 'condition--holds' : condition.holds === false ? 'condition--fails' : 'condition--unknown';
  }

  /** The questions on the path the channel actually followed. */
  protected onPath(flow: ApiChannelFlow) { return flow.steps.filter(step => step.branch !== 'NOT_REACHED'); }

  /** The questions after the decision was settled, shown collapsed. */
  protected offPath(flow: ApiChannelFlow) { return flow.steps.filter(step => step.branch === 'NOT_REACHED'); }
}
