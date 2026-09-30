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

  protected deltaClass(): 'above' | 'equal' | 'below' {
    const s = this.snapshot();
    if (!s) {
      return 'equal';
    }
    if (s.speedAdvisor.currentKmh > s.speedAdvisor.recommendedKmh + 0.1) {
      return 'above';
    }
    if (s.speedAdvisor.currentKmh + 0.1 < s.speedAdvisor.recommendedKmh) {
      return 'below';
    }
    return 'equal';
  }
}
