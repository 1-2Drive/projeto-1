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
