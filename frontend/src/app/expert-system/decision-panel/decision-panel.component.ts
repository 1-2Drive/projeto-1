import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { DecimalPipe } from '@angular/common';

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

  protected riskClass(): string {
    const risk = (this.snapshot()?.currentDecision.riskLevel ?? 'LOW').toUpperCase();
    if (risk.includes('HIGH') || risk.includes('CRITICAL')) {
      return 'risk-high';
    }
    if (risk.includes('MODERATE')) {
      return 'risk-medium';
    }
    return 'risk-low';
  }
}
