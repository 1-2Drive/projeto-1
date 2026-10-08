import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

/**
 * Renders the backend's narrative of the latest inference cycle. The steps come
 * verbatim from the API; this component only lays them out and does not derive any
 * conclusion from sensor values.
 */
@Component({
  selector: 'app-situation-panel',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './situation-panel.component.html',
  styleUrl: './situation-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SituationPanelComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);

  protected readonly steps = computed(() => this.snapshot()?.inference?.situationSteps ?? []);
  protected readonly lastEvent = computed(() => this.snapshot()?.inference?.lastImportantEvent ?? null);
  protected readonly running = computed(() => this.snapshot()?.status === 'RUNNING');

  /**
   * Before the first decision cycle the backend still reports its untouched default
   * decision, which would read as a real conclusion. The panel shows its own empty
   * state there instead of narrating a cycle that has not happened yet.
   */
  protected readonly idle = computed(() => {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return true;
    }
    return snapshot.status === 'STOPPED' && snapshot.simulationTimeMillis === 0;
  });

  /** Highlights the step that states the decision Drools reached. */
  protected isDecisionStep(step: string): boolean {
    return step.startsWith('Decision:');
  }

  protected isManeuverStep(step: string): boolean {
    return step.startsWith('Vehicle is') || step.startsWith('Vehicle completed');
  }
}
