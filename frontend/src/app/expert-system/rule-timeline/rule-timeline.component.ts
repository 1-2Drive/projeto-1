import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-rule-timeline',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './rule-timeline.component.html',
  styleUrl: './rule-timeline.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RuleTimelineComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
}
