import { DecimalPipe, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ApiRoadElement, ApiSimulationSnapshot } from '../../core/models/api.models';

/** Category drives which vector asset is rendered for a world object. */
export type RoadObjectKind =
  | 'speedLimit'
  | 'stopSign'
  | 'yieldSign'
  | 'trafficLight'
  | 'roundabout'
  | 'barrier'
  | 'pothole'
  | 'roadworks'
  | 'obstacle'
  | 'vehicle'
  | 'pedestrian';

export interface RoadRenderItem {
  id: string;
  kind: RoadObjectKind;
  /** Human label drawn inside the asset (e.g. "50" on a speed limit sign). */
  label: string;
  /** Backend-reported state used to light the correct indicator (traffic light/barrier). */
  state: string;
  ariaLabel: string;
  relativeMeters: number;
  xPercent: number;
  yPercent: number;
  detectedByCamera: boolean;
  detectedByRadar: boolean;
}

const LANE_CENTER_Y: Record<'LEFT' | 'CENTER' | 'RIGHT', number> = {
  LEFT: 25,
  CENTER: 50,
  RIGHT: 75
};

const EMPTY_RANGES = {
  cameraRangeMeters: 0,
  radarRangeMeters: 0,
  blindSpotRangeMeters: 0,
  cameraRateMillis: 0,
  radarRateMillis: 0
};

@Component({
  selector: 'app-road-canvas',
  standalone: true,
  imports: [NgIf, DecimalPipe],
  templateUrl: './road-canvas.component.html',
  styleUrl: './road-canvas.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoadCanvasComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);

  /** Metres of road visible behind and ahead of the ego vehicle. */
  readonly worldBackwardMeters = 40;
  readonly worldForwardMeters = 200;

  protected readonly ranges = computed(() => this.snapshot()?.sensorRanges ?? EMPTY_RANGES);

  /** Distance ticks drawn on the road so scale is always readable. */
  protected readonly distanceTicks = [0, 40, 80, 120, 160, 200];

  protected readonly laneLabels = [
    { lane: 'LEFT' as const, y: LANE_CENTER_Y.LEFT },
    { lane: 'CENTER' as const, y: LANE_CENTER_Y.CENTER },
    { lane: 'RIGHT' as const, y: LANE_CENTER_Y.RIGHT }
  ];

  protected readonly items = computed<RoadRenderItem[]>(() => {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return [];
    }
    return snapshot.worldObjects
      .filter((item) => this.isVisible(item.positionMeters))
      .map((item) => {
        const kind = this.kindOf(item);
        const state = this.stateOf(item);
        return {
          id: item.id,
          kind,
          label: this.labelOf(item),
          state,
          ariaLabel: `${this.objectIconLabel(kind)} ${state ? state.toLowerCase() + ' ' : ''}at ${item.positionMeters.toFixed(0)} metres`,
          relativeMeters: item.positionMeters,
          xPercent: this.toPercentX(item.positionMeters),
          yPercent: LANE_CENTER_Y[item.lane] ?? LANE_CENTER_Y.CENTER,
          detectedByCamera: snapshot.cameraDetectedObjectIds.includes(item.id),
          detectedByRadar: snapshot.radarDetectedObjectIds.includes(item.id)
        };
      });
  });

  /** Ego marker uses the same metric mapping as every other object. */
  protected readonly egoXPercent = computed(() => this.toPercentX(0));
  protected readonly egoYPercent = computed(() => {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return LANE_CENTER_Y.CENTER;
    }
    return LANE_CENTER_Y[snapshot.egoVehicle.lane] ?? LANE_CENTER_Y.CENTER;
  });

  protected readonly cameraEdgePercent = computed(() => this.toPercentX(this.ranges().cameraRangeMeters));
  protected readonly radarEdgePercent = computed(() => this.toPercentX(this.ranges().radarRangeMeters));

  /** True before the first tick so the UI can invite the user to press START. */
  protected readonly awaitingStart = computed(() => {
    const snapshot = this.snapshot();
    return !!snapshot && snapshot.simulationTimeMillis === 0 && snapshot.status !== 'RUNNING';
  });

  protected readonly running = computed(() => this.snapshot()?.status === 'RUNNING');

  protected tickPercent(meters: number): number {
    return this.toPercentX(meters);
  }

  protected isVisible(relativeMeters: number): boolean {
    return relativeMeters >= -this.worldBackwardMeters && relativeMeters <= this.worldForwardMeters;
  }

  protected toPercentX(relativeMeters: number): number {
    const span = this.worldForwardMeters + this.worldBackwardMeters;
    const normalized = (relativeMeters + this.worldBackwardMeters) / span;
    return Math.max(0, Math.min(100, normalized * 100));
  }

  protected objectIconLabel(kind: RoadObjectKind): string {
    switch (kind) {
      case 'speedLimit':
        return 'Speed limit sign';
      case 'stopSign':
        return 'Stop sign';
      case 'yieldSign':
        return 'Yield sign';
      case 'trafficLight':
        return 'Traffic light';
      case 'roundabout':
        return 'Roundabout';
      case 'barrier':
        return 'Barrier';
      case 'pothole':
        return 'Pothole';
      case 'roadworks':
        return 'Roadworks';
      case 'obstacle':
        return 'Obstacle';
      case 'vehicle':
        return 'Vehicle';
      case 'pedestrian':
        return 'Pedestrian';
    }
  }

  /** Reads the backend-reported state token (RED / GREEN / open=true / ...). */
  private stateOf(item: ApiRoadElement): string {
    const details = item.details ?? '';
    if (item.type.includes('TrafficLight')) {
      return details.match(/state=(\w+)/)?.[1] ?? '';
    }
    if (item.type.includes('Barrier')) {
      const open = details.match(/open=(\w+)/)?.[1];
      return open ? (open === 'true' ? 'OPEN' : 'CLOSED') : '';
    }
    return '';
  }

  private kindOf(item: ApiRoadElement): RoadObjectKind {
    const type = item.type;
    if (type.includes('SpeedLimit')) return 'speedLimit';
    if (type.includes('StopSign')) return 'stopSign';
    if (type.includes('YieldSign')) return 'yieldSign';
    if (type.includes('TrafficLight')) return 'trafficLight';
    if (type.includes('Roundabout')) return 'roundabout';
    if (type.includes('Barrier')) return 'barrier';
    if (type.includes('Pothole')) return 'pothole';
    if (type.includes('Roadworks')) return 'roadworks';
    if (type.includes('Obstacle')) return 'obstacle';
    if (type.includes('Pedestrian')) return 'pedestrian';
    if (type.includes('Vehicle')) return 'vehicle';
    return 'obstacle';
  }

  private labelOf(item: ApiRoadElement): string {
    const details = item.details ?? '';
    if (item.type.includes('SpeedLimit')) {
      return details.match(/limitKmh=(\d+)/)?.[1] ?? '';
    }
    if (item.type.includes('Vehicle')) {
      return details.match(/speedMps=([\d.]+)/)?.[1] ?? '';
    }
    return '';
  }
}
