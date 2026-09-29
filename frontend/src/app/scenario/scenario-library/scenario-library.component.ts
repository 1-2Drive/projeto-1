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
}
