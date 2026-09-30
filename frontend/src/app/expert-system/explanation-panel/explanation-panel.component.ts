import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

/**
 * Presents the audit chain the engine publishes for the current decision:
 * what was decided, why, which rule fired, and the raw evidence it used.
 */
@Component({
  selector: 'app-explanation-panel',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './explanation-panel.component.html',
  styleUrl: './explanation-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExplanationPanelComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);

  protected readonly decision = computed(() => this.snapshot()?.currentDecision ?? null);
  protected readonly evidence = computed(() => this.decision()?.evidence ?? []);
  protected readonly hasEvidence = computed(() => this.evidence().length > 0);
}