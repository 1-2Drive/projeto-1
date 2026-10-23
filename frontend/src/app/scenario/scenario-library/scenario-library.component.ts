import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
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
  readonly loadScenario = output<string>();

  /** `S12_PRESENTATION_DEMO` is shown as `S12`: the name beside it already says what it is. */
  protected shortId(id: string): string {
    return id.split('_')[0];
  }
}
