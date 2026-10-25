import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ScenarioSummary } from '../../core/models/api.models';

@Component({
  selector: 'app-scenario-library',
  standalone: true,
  templateUrl: './scenario-library.component.html',
  styleUrl: './scenario-library.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScenarioLibraryComponent {
  readonly scenarios = input<ScenarioSummary[]>([]);
  /** The backend's random object frequency (0–100), null until the first snapshot. */
  readonly randomObjectFrequency = input<number | null>(null);
  readonly loadScenario = output<string>();
  readonly randomObjectFrequencyChange = output<number>();

  /**
   * The value under the thumb while it is dragged. Only the released value is sent, so a
   * drag is one request and the snapshots arriving meanwhile never pull the thumb back.
   */
  private readonly dragged = signal<number | null>(null);
  protected readonly shownFrequency = computed(() => this.dragged() ?? this.randomObjectFrequency() ?? 0);

  /** `S12_PRESENTATION_DEMO` is shown as `S12`: the name beside it already says what it is. */
  protected shortId(id: string): string {
    return id.split('_')[0];
  }

  protected dragFrequency(value: string): void {
    this.dragged.set(Number(value));
  }

  protected releaseFrequency(value: string): void {
    this.dragged.set(null);
    this.randomObjectFrequencyChange.emit(Number(value));
  }
}
