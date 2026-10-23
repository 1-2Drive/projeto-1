import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-world-state',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './world-state.component.html',
  styleUrl: './world-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorldStateComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
}
