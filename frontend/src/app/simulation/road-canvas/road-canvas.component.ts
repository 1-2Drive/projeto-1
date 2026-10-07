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
  | 'pedestrian'
  | 'speedRadar';

export interface RoadRenderItem {
  id: string;
  kind: RoadObjectKind;
  /** Human label drawn inside the asset (e.g. "50" on a speed limit sign). */
  label: string;
  /** Backend-reported state used to light the correct indicator (traffic light/barrier). */
  state: string;
  ariaLabel: string;
  relativeMeters: number;
  worldPositionMeters: number;
  xPercent: number;
  yPercent: number;
  detectedByCamera: boolean;
  detectedByRadar: boolean;
  visible: boolean;
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

  /** Visible road span in viewport (in world meters). */
  readonly viewportSpanMeters = 180;
  /** Left margin in world meters at scenario start so vehicle begins near the left edge (~5.5%). */
  readonly initialLeftMarginMeters = 10;
  /** Dead-zone threshold ratio (0.80 = 80%) before camera starts scrolling. */
  readonly deadZoneRatio = 0.80;

  /** Metres of road visible behind and ahead for backward compatibility. */
  readonly worldBackwardMeters = 40;
  readonly worldForwardMeters = 200;

  private lastSimTime = -1;
  private scenarioStartPos = 0;

  protected readonly ranges = computed(() => this.snapshot()?.sensorRanges ?? EMPTY_RANGES);

  /** Distance ticks drawn on the road so scale is always readable. */
  protected readonly distanceTicks = [0, 40, 80, 120, 160, 200];

  protected readonly egoPosition = computed(() => this.snapshot()?.egoVehicle?.positionMeters ?? 0);
  protected readonly egoSpeed = computed(() => this.snapshot()?.egoVehicle?.speedKmh ?? 0);
  protected readonly egoAcceleration = computed(() => this.snapshot()?.egoVehicle?.accelerationMps2 ?? 0);
  protected readonly currentLane = computed(() => this.snapshot()?.egoVehicle?.lane ?? 'CENTER');
  protected readonly drivingState = computed(() => this.snapshot()?.egoVehicle?.drivingState ?? (this.egoSpeed() > 0.5 ? 'CRUISING' : 'STOPPED'));
  protected readonly indicatorName = computed(() => {
    const ego = this.snapshot()?.egoVehicle;
    if (ego?.indicatorState && ego.indicatorState !== 'OFF') {
      return ego.indicatorState;
    }
    const m = this.snapshot()?.maneuver;
    if (m?.indicatorsLeft) return 'LEFT';
    if (m?.indicatorsRight) return 'RIGHT';
    return 'OFF';
  });
  protected readonly steeringName = computed(() => {
    return this.snapshot()?.egoVehicle?.steeringState ?? this.snapshot()?.maneuver?.steering ?? 'STRAIGHT';
  });
  protected readonly leftMarking = computed(() => this.snapshot()?.roadMarks?.leftMarking ?? 'DASHED');
  protected readonly rightMarking = computed(() => this.snapshot()?.roadMarks?.rightMarking ?? 'DASHED');

  protected readonly wheelSteerAngle = computed(() => {
    const st = this.steeringName();
    if (st === 'LEFT') return -12;
    if (st === 'RIGHT') return 12;
    return 0;
  });

  protected readonly indicatorHazard = computed(() => this.indicatorName() === 'HAZARD');

  /**
   * Evaluates the scenario starting position cleanly whenever simulation resets (time 0 or rewind).
   */
  private resolveStartPos(snapshot: ApiSimulationSnapshot | null): number {
    if (!snapshot) return 0;
    const t = snapshot.simulationTimeMillis;
    if (t === 0 || t < this.lastSimTime) {
      this.scenarioStartPos = snapshot.egoVehicle.positionMeters;
    }
    this.lastSimTime = t;
    return this.scenarioStartPos;
  }

  /**
   * Dead-zone camera offset in world meters:
   * Stays completely static while ego vehicle traverses from start up to the dead-zone threshold (80% of viewport).
   * Only after ego exceeds the dead-zone threshold does the camera begin scrolling smoothly forward.
   */
  protected readonly cameraOffset = computed(() => {
    const snap = this.snapshot();
    const startPos = this.resolveStartPos(snap);
    const egoPos = this.egoPosition();
    const baseOffset = startPos - this.initialLeftMarginMeters;
    const thresholdWorldPos = baseOffset + this.viewportSpanMeters * this.deadZoneRatio;
    if (egoPos <= thresholdWorldPos) {
      return baseOffset;
    }
    return egoPos - this.viewportSpanMeters * this.deadZoneRatio;
  });

  /**
   * Deterministic conversion from world meters to screen percentage across the viewport.
   * Shared by ego vehicle, speed radar, distance ticks, sensor cones, and all road obstacles.
   */
  protected worldToPercentX(worldMeters: number): number {
    const offset = this.cameraOffset();
    return ((worldMeters - offset) / this.viewportSpanMeters) * 100;
  }

  /**
   * Ego vehicle screen X coordinate. Increases continuously as ego advances through the world!
   */
  protected readonly egoXPercent = computed(() => {
    return Math.max(0, Math.min(100, this.worldToPercentX(this.egoPosition())));
  });

  protected readonly speedRadarDist = computed(() => {
    const d = this.snapshot()?.sensors?.frontRadar?.speedRadarDistanceMeters;
    if (d != null && Number.isFinite(d) && d > 0 && d <= 135) {
      return d;
    }
    const egoPos = this.egoPosition();
    const radar = this.snapshot()?.worldObjects?.find((o) => o.type.includes('SpeedRadar'));
    if (radar) {
      const worldPos = radar.worldPositionMeters != null && Number.isFinite(radar.worldPositionMeters)
        ? radar.worldPositionMeters
        : (egoPos + radar.positionMeters);
      const diff = worldPos - egoPos;
      if (diff > 0 && diff <= 135) {
        return diff;
      }
    }
    return null;
  });

  protected readonly speedRadarLimit = computed(() => {
    const l = this.snapshot()?.sensors?.frontRadar?.speedRadarLimitKmh;
    if (l != null && l > 0) return l;
    const radar = this.snapshot()?.worldObjects?.find((o) => o.type.includes('SpeedRadar'));
    if (radar?.details) {
      const match = radar.details.match(/speedLimitKmh=([\d.]+)/);
      if (match) return Math.round(parseFloat(match[1]));
    }
    return 50;
  });

  protected readonly speedRadarPassed = computed(() => {
    const egoPos = this.egoPosition();
    const radar = this.snapshot()?.worldObjects?.find((o) => o.type.includes('SpeedRadar'));
    if (!radar) return false;
    const worldPos = radar.worldPositionMeters != null && Number.isFinite(radar.worldPositionMeters)
      ? radar.worldPositionMeters
      : (egoPos + radar.positionMeters);
    return egoPos >= worldPos;
  });

  /** Dynamic ticks in world metres. Completely static while camera is static; scrolls when camera scrolls. */
  protected readonly dynamicTicks = computed(() => {
    const camOffset = this.cameraOffset();
    const span = this.viewportSpanMeters;
    const minM = camOffset;
    const maxM = camOffset + span;
    const step = 40;
    const first = Math.floor(minM / step) * step;
    const ticks: { worldMeters: number; percent: number }[] = [];
    for (let m = first; m <= maxM + step; m += step) {
      const pct = this.worldToPercentX(m);
      if (pct >= -5 && pct <= 105) {
        ticks.push({
          worldMeters: m,
          percent: pct
        });
      }
    }
    return ticks;
  });

  /** Dash offset in percent for streaming lane line animation. Static while camera is static. */
  protected readonly dashOffsetPercent = computed(() => {
    const mod = ((this.cameraOffset() % 16) + 16) % 16;
    return -(mod / this.viewportSpanMeters) * 100;
  });

  protected readonly laneLabels = [
    { lane: 'LEFT' as const, y: LANE_CENTER_Y.LEFT },
    { lane: 'CENTER' as const, y: LANE_CENTER_Y.CENTER },
    { lane: 'RIGHT' as const, y: LANE_CENTER_Y.RIGHT }
  ];

  protected readonly items = computed<RoadRenderItem[]>(() => {
    const snapshot = this.snapshot();
    if (!snapshot || !snapshot.worldObjects) {
      return [];
    }
    const egoPos = this.egoPosition();
    return snapshot.worldObjects
      .map((item) => {
        const kind = this.kindOf(item);
        const state = this.stateOf(item);
        const worldPos = item.worldPositionMeters != null && Number.isFinite(item.worldPositionMeters)
          ? item.worldPositionMeters
          : (egoPos + item.positionMeters);
        const xPct = this.worldToPercentX(worldPos);
        return {
          id: item.id,
          kind,
          label: this.labelOf(item),
          state,
          ariaLabel: `${this.objectIconLabel(kind)} ${state ? state.toLowerCase() + ' ' : ''}at ${worldPos.toFixed(0)} metres`,
          relativeMeters: item.positionMeters,
          worldPositionMeters: worldPos,
          xPercent: xPct,
          yPercent: this.lateralToY(LANE_CENTRE_METRES[item.lane] ?? 0),
          detectedByCamera: snapshot.cameraDetectedObjectIds?.includes(item.id) ?? false,
          detectedByRadar: snapshot.radarDetectedObjectIds?.includes(item.id) ?? false,
          visible: xPct >= -15 && xPct <= 115
        };
      })
      .filter((item) => item.visible);
  });

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
  protected readonly indicatorLeft = computed(() => {
    const ego = this.snapshot()?.egoVehicle;
    if (ego?.indicatorState === 'LEFT') return true;
    return this.snapshot()?.maneuver?.indicatorsLeft ?? false;
  });
  protected readonly indicatorRight = computed(() => {
    const ego = this.snapshot()?.egoVehicle;
    if (ego?.indicatorState === 'RIGHT') return true;
    return this.snapshot()?.maneuver?.indicatorsRight ?? false;
  });
  protected readonly braking = computed(() => {
    const ego = this.snapshot()?.egoVehicle;
    if (ego?.drivingState === 'BRAKING' || (ego?.accelerationMps2 ?? 0) < -0.8) return true;
    return this.snapshot()?.maneuver?.braking ?? false;
  });

  protected readonly cameraEdgePercent = computed(() => {
    return this.worldToPercentX(this.egoPosition() + this.ranges().cameraRangeMeters);
  });

  protected readonly radarEdgePercent = computed(() => {
    return this.worldToPercentX(this.egoPosition() + this.ranges().radarRangeMeters);
  });

  protected readonly blindSpotEdgePercent = computed(() => {
    return this.worldToPercentX(this.egoPosition() + this.ranges().blindSpotRangeMeters);
  });

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
    const xPct = this.toPercentX(relativeMeters);
    return xPct >= -15 && xPct <= 115;
  }

  /** Maps a lateral offset in metres onto the vertical centre band of the road. */
  protected lateralToY(lateralMeters: number): number {
    const clamped = Math.max(LANE_CENTRE_METRES.LEFT, Math.min(LANE_CENTRE_METRES.RIGHT, lateralMeters));
    return LANE_CENTER_Y.CENTER + (clamped / LANE_CENTRE_METRES.RIGHT)
      * (LANE_CENTER_Y.RIGHT - LANE_CENTER_Y.CENTER);
  }

  /** Retained for test compatibility */
  protected toPercentX(relativeMeters: number): number {
    return this.worldToPercentX(this.egoPosition() + relativeMeters);
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
      case 'speedRadar':
        return 'Speed radar';
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
    if (type.includes('SpeedRadar')) return 'speedRadar';
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
    if (item.type.includes('SpeedRadar')) {
      return details.match(/speedLimitKmh=([\d.]+)/)?.[1] ?? '50';
    }
    if (item.type.includes('SpeedLimit')) {
      return details.match(/limitKmh=(\d+)/)?.[1] ?? '';
    }
    if (item.type.includes('Vehicle')) {
      return details.match(/speedMps=([\d.]+)/)?.[1] ?? '';
    }
    return '';
  }
}
