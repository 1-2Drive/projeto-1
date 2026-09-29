import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

@Component({
  selector: 'app-decision-panel',
  standalone: true,
  templateUrl: './decision-panel.component.html',
  styleUrl: './decision-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DecisionPanelComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
}
