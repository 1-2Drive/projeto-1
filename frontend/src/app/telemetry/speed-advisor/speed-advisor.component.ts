import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

const MAX_SCALE_KMH = 160;
const ARC_LENGTH = 219.9;

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

  protected readonly maxScale = MAX_SCALE_KMH;
  protected readonly arcLength = ARC_LENGTH;

  protected readonly currentKmh = computed(() => this.snapshot()?.speedAdvisor.currentKmh ?? 0);
  protected readonly legalLimitKmh = computed(() => this.snapshot()?.speedAdvisor.legalLimitKmh ?? 0);
  protected readonly recommendedKmh = computed(() => this.snapshot()?.speedAdvisor.recommendedKmh ?? 0);

  /**
   * Before the first decision cycle the backend reports a placeholder recommendation
   * of 0 km/h. Showing that as "recommended 0, delta +60" would be misleading, so the
   * gauge announces that it is only waiting for START instead.
   */
  protected readonly idle = computed(() => {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return true;
    }
    return snapshot.status === 'STOPPED' && snapshot.simulationTimeMillis === 0;
  });

  protected readonly action = computed(() => (this.idle() ? 'READY' : this.snapshot()?.speedAdvisor.action ?? '—'));
  protected readonly recommendedLabel = computed(() => (this.idle() ? '—' : `${Math.round(this.recommendedKmh())} km/h`));

  protected readonly deltaKmh = computed(() =>
    Math.round((this.currentKmh() - this.recommendedKmh()) * 10) / 10
  );

  protected readonly deltaLabel = computed(() =>
    this.idle() ? '—' : `${this.deltaKmh() > 0 ? '+' : ''}${Math.round(this.deltaKmh() * 10) / 10} km/h`
  );

  protected readonly deltaClass = computed<'above' | 'equal' | 'below'>(() => {
    const delta = this.deltaKmh();
    if (delta > 0.1) {
      return 'above';
    }
    if (delta < -0.1) {
      return 'below';
    }
    return 'equal';
  });

  protected readonly withinLimit = computed(() => this.currentKmh() <= this.legalLimitKmh() + 0.1);

  protected valueDash(): number {
    return Math.min(this.currentKmh() / MAX_SCALE_KMH, 1) * ARC_LENGTH;
  }

  protected limitDash(): number {
    return Math.min(this.legalLimitKmh() / MAX_SCALE_KMH, 1) * ARC_LENGTH;
  }

  protected recommendedDash(): number {
    return Math.min(this.recommendedKmh() / MAX_SCALE_KMH, 1) * ARC_LENGTH;
  }

  /** Point on a circle around the arc's centre (90, 90), at an angle from -90 (zero) to +90 (full scale). */
  protected pinX(angleDegrees: number, radius: number): number {
    return Math.round((90 + radius * Math.sin((angleDegrees * Math.PI) / 180)) * 10) / 10;
  }

  protected pinY(angleDegrees: number, radius: number): number {
    return Math.round((90 - radius * Math.cos((angleDegrees * Math.PI) / 180)) * 10) / 10;
  }

  protected limitAngle(): number {
    return -90 + (this.limitDash() / ARC_LENGTH) * 180;
  }

  protected recommendedAngle(): number {
    return -90 + (this.recommendedDash() / ARC_LENGTH) * 180;
  }
}