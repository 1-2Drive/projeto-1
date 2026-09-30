import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

@Component({
  selector: 'app-sensors-panel',
  standalone: true,
  templateUrl: './sensors-panel.component.html',
  styleUrl: './sensors-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SensorsPanelComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);

  protected sensorRows() {
    const readings = this.snapshot()?.sensorReadings ?? {};
    const preferredOrder = [
      'FrontRadarReadingEvent',
      'BlindSpotReadingEvent',
      'TTCEvent',
      'ReducedVisibilityEvent',
      'ReducedGripEvent',
      'SpeedReadingEvent'
    ];

    return Object.entries(readings)
      .sort(([a], [b]) => {
        const ai = preferredOrder.findIndex((k) => a.includes(k));
        const bi = preferredOrder.findIndex((k) => b.includes(k));
        return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
      })
      .map(([key, value]) => ({ key, value }));
  }

  protected formatLabel(raw: string): string {
    return raw
      .replace(/Event$/g, '')
      .replace(/Reading/g, '')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .toUpperCase();
  }
}
