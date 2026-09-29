import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { KeyValuePipe } from '@angular/common';

@Component({
  selector: 'app-sensors-panel',
  standalone: true,
  imports: [KeyValuePipe],
  templateUrl: './sensors-panel.component.html',
  styleUrl: './sensors-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SensorsPanelComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
}
