import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-vehicle-state',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './vehicle-state.component.html',
  styleUrl: './vehicle-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VehicleStateComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
}
