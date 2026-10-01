import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

type RiskLevel = 'risk-low' | 'risk-medium' | 'risk-high';
type ActionTone = 'go' | 'caution' | 'stop';

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
  protected readonly hasDecision = computed(() => this.decision()?.action !== 'AWAITING_DECISION');

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

  protected readonly tone = computed<ActionTone>(() => {
    const action = (this.decision()?.action ?? '').toUpperCase();
    if (action.includes('EMERGENCY') || action === 'STOP' || action.includes('HOLD')) {
      return 'stop';
    }
    if (action.includes('REDUCE') || action.includes('DECELER') || action.includes('CAUTION') || action.includes('YIELD')) {
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
