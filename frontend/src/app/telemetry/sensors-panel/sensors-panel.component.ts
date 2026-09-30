import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

interface SensorRow {
  key: string;
  label: string;
  value: string;
}

const PREFERRED_ORDER = [
  'TTCEvent',
  'FrontRadarReadingEvent',
  'BlindSpotReadingEvent',
  'ReducedVisibilityEvent',
  'ReducedGripEvent',
  'SpeedReadingEvent'
];

/**
 * Compact technical telemetry: live sensor facts plus the configured ranges the
 * backend actually applies. Detection counts explain *why* an object is or is not
 * visible on the road canvas.
 */
@Component({
  selector: 'app-sensors-panel',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './sensors-panel.component.html',
  styleUrl: './sensors-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SensorsPanelComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);

  protected readonly ranges = computed(() => this.snapshot()?.sensorRanges ?? null);

  protected readonly cameraDetections = computed(() => this.snapshot()?.cameraDetectedObjectIds ?? []);
  protected readonly radarDetections = computed(() => this.snapshot()?.radarDetectedObjectIds ?? []);
  protected readonly trackedDetections = computed(() => this.snapshot()?.detectedObjectIds ?? []);

  protected readonly sensorRows = computed<SensorRow[]>(() => {
    const readings = this.snapshot()?.sensorReadings ?? {};
    return Object.entries(readings)
      .sort(([a], [b]) => this.rank(a) - this.rank(b))
      .map(([key, value]) => ({ key, label: this.formatLabel(key), value }));
  });

  private rank(key: string): number {
    const index = PREFERRED_ORDER.findIndex((candidate) => key.includes(candidate));
    return index === -1 ? 99 : index;
  }

  private formatLabel(raw: string): string {
    return raw
      .replace(/Event$/g, '')
      .replace(/Reading/g, '')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .toUpperCase();
  }
}