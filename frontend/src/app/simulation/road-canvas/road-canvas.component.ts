import { DecimalPipe, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ApiRoadElement, ApiSimulationSnapshot, LaneName } from '../../core/models/api.models';

/** Category drives which vector asset is rendered for a world object. */
export type RoadObjectKind =
  | 'speedLimit'
  | 'stopSign'
  | 'yieldSign'
  | 'trafficLight'
  | 'barrier'
  | 'pothole'
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

/**
 * Lane centre offsets in metres, mirroring the backend Lane.centreMeters() contract.
 * The road view maps this continuously, so a lane change is visible as smooth motion
 * rather than a jump between discrete bands.
 */
const LANE_CENTRE_METRES: Record<LaneName, number> = {
  LEFT: -3.5,
  CENTER: 0,
  RIGHT: 3.5
};

/** Vertical centre band of each lane, as a percentage of the road height. */
const LANE_CENTER_Y: Record<LaneName, number> = {
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
          yPercent: this.lateralToY(LANE_CENTRE_METRES[item.lane] ?? 0),
          detectedByCamera: snapshot.cameraDetectedObjectIds.includes(item.id),
          detectedByRadar: snapshot.radarDetectedObjectIds.includes(item.id)
        };
      });
  });

  /** Ego marker uses the same metric mapping as every other object. */
  protected readonly egoXPercent = computed(() => this.toPercentX(0));

  /**
   * Vertical position comes straight from the backend lateral coordinate, so during a
   * lane change the car visibly slides across the road.
   */
  protected readonly egoYPercent = computed(() => {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return LANE_CENTER_Y.CENTER;
    }
    const lateral = snapshot.egoVehicle.lateralPositionMeters;
    if (Number.isFinite(lateral)) {
      return this.lateralToY(lateral);
    }
    return this.lateralToY(LANE_CENTRE_METRES[snapshot.egoVehicle.lane] ?? 0);
  });

  protected readonly maneuverState = computed(() => this.snapshot()?.maneuver?.state ?? 'IDLE');
  protected readonly maneuverProgress = computed(() => this.snapshot()?.maneuver?.progress ?? 0);
  protected readonly maneuverTargetLane = computed(() => this.snapshot()?.maneuver?.targetLane ?? 'CENTER');
  protected readonly indicatorLeft = computed(() => this.snapshot()?.maneuver?.indicatorsLeft ?? false);
  protected readonly indicatorRight = computed(() => this.snapshot()?.maneuver?.indicatorsRight ?? false);
  protected readonly braking = computed(() => this.snapshot()?.maneuver?.braking ?? false);

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

  /** Maps a lateral offset in metres onto the vertical centre band of the road. */
  protected lateralToY(lateralMeters: number): number {
    const clamped = Math.max(LANE_CENTRE_METRES.LEFT, Math.min(LANE_CENTRE_METRES.RIGHT, lateralMeters));
    return LANE_CENTER_Y.CENTER + (clamped / LANE_CENTRE_METRES.RIGHT)
      * (LANE_CENTER_Y.RIGHT - LANE_CENTER_Y.CENTER);
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
      case 'barrier':
        return 'Barrier';
      case 'pothole':
        return 'Pothole';
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
    if (type.includes('Barrier')) return 'barrier';
    if (type.includes('Pothole')) return 'pothole';
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
