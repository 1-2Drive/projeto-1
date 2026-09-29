import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-speed-advisor',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './speed-advisor.component.html',
  styleUrl: './speed-advisor.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SpeedAdvisorComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
}
