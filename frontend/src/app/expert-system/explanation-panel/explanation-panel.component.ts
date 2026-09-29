import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

@Component({
  selector: 'app-explanation-panel',
  standalone: true,
  templateUrl: './explanation-panel.component.html',
  styleUrl: './explanation-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExplanationPanelComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
}
