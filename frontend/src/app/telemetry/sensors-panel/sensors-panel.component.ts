import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiSimulationSnapshot, SensorStatus } from '../../core/models/api.models';

interface SensorLine {
  label: string;
  value: string;
  tone?: 'critical' | 'warning';
}

/**
 * Live sensor telemetry grouped by the physical sensor that produced it. Values and
 * statuses come from the backend; the status names are descriptive mappings only and
 * never influence vehicle behaviour.
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

  protected readonly radar = computed(() => this.snapshot()?.sensors?.frontRadar ?? null);
  protected readonly camera = computed(() => this.snapshot()?.sensors?.frontCamera ?? null);
  protected readonly blindSpotLeft = computed(() => this.snapshot()?.sensors?.blindSpotLeft ?? null);
  protected readonly blindSpotRight = computed(() => this.snapshot()?.sensors?.blindSpotRight ?? null);
  protected readonly environment = computed(() => this.snapshot()?.sensors?.environment ?? null);

  protected readonly radarLines = computed<SensorLine[]>(() => {
    const radar = this.radar();
    if (!radar) {
      return [];
    }
    const tracking = radar.status !== 'CLEAR';
    const lines: SensorLine[] = [
      { label: 'Range', value: `${this.round(radar.rangeMeters)} m` },
      { label: 'Target', value: tracking ? this.shortId(radar.targetId) : 'none' },
      {
        label: 'Distance',
        value: tracking ? `${this.round(radar.distanceMeters)} m` : '—',
        tone: this.toneFor(radar.status)
      },
      {
        label: 'Closing',
        value: tracking ? `${this.signed(radar.relativeSpeedKmh)} km/h` : '—'
      },
      {
        label: 'TTC',
        value: tracking ? `${this.round(radar.ttcSeconds)} s` : '—',
        tone: this.toneFor(radar.status)
      }
    ];
    if (radar.speedRadarDistanceMeters != null && Number.isFinite(radar.speedRadarDistanceMeters) && radar.speedRadarDistanceMeters > 0) {
      lines.push({
        label: 'Speed radar',
        value: `${this.round(radar.speedRadarDistanceMeters)} m (${Math.round(radar.speedRadarLimitKmh ?? 50)} km/h limit)`
      });
    }
    return lines;
  });

  protected readonly cameraLines = computed<SensorLine[]>(() => {
    const camera = this.camera();
    if (!camera) {
      return [];
    }
    const lines: SensorLine[] = [{ label: 'Range', value: `${this.round(camera.rangeMeters)} m` }];
    if (camera.detections.length === 0) {
      lines.push({ label: 'Detected', value: 'nothing in range' });
      return lines;
    }
    for (const detection of camera.detections) {
      lines.push({
        label: detection.label,
        value: `${this.round(detection.distanceMeters)} m · ${detection.lane} lane`,
        tone: 'warning'
      });
    }
    return lines;
  });

  protected readonly environmentLines = computed<SensorLine[]>(() => {
    const environment = this.environment();
    if (!environment) {
      return [];
    }
    return [
      { label: 'Visibility', value: `${this.round(environment.effectiveRangeMeters)} m` },
      { label: 'Road grip', value: `${Math.round(environment.gripPercent)}%` },
      { label: 'Rain', value: environment.rain ? 'YES' : 'NO' }
    ];
  });

  protected statusTone(status: SensorStatus): string {
    switch (status) {
      case 'CRITICAL':
        return 'critical';
      case 'WARNING':
        return 'warning';
      case 'TRACKING':
      case 'DETECTED':
      case 'OCCUPIED':
        return 'active';
      default:
        return 'clear';
    }
  }

  private toneFor(status: SensorStatus): SensorLine['tone'] {
    if (status === 'CRITICAL') {
      return 'critical';
    }
    if (status === 'WARNING') {
      return 'warning';
    }
    return undefined;
  }

  private round(value: number): string {
    return Number.isFinite(value) ? value.toFixed(1) : '—';
  }

  private signed(value: number): string {
    if (!Number.isFinite(value)) {
      return '—';
    }
    return `${value >= 0 ? '+' : ''}${value.toFixed(1)}`;
  }

  /** Shortens generated identifiers so rows stay on one line. */
  private shortId(id: string): string {
    if (!id || id === 'none') {
      return 'none';
    }
    return id.length > 12 ? `#${id.slice(-4)}` : id;
  }
}
