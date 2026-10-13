import { ChangeDetectionStrategy, Component, computed, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CreateActorRequest, CreateRoadElementRequest } from '../../core/models/api.models';

export type ToolboxAction =
  | 'SPEED_LIMIT'
  | 'STOP_SIGN'
  | 'TRAFFIC_LIGHT'
  | 'POTHOLE'
  | 'STATIC_OBSTACLE'
  | 'BARRIER'
  | 'FRONT_VEHICLE'
  | 'PEDESTRIAN';

interface ActionDefinition {
  title: string;
  hint: string;
  needsLane: boolean;
  needsSeverity: boolean;
  needsSpeedLimit: boolean;
  needsLightState: boolean;
  needsBarrierState: boolean;
  needsActorSpeed: boolean;
}

const ACTIONS: Record<ToolboxAction, ActionDefinition> = {
  SPEED_LIMIT: {
    title: 'Add speed limit', hint: 'Applies to all three lanes. Camera detects it only inside its range.',
    needsLane: false, needsSeverity: false, needsSpeedLimit: true,
    needsLightState: false, needsBarrierState: false, needsActorSpeed: false
  },
  STOP_SIGN: {
    title: 'Add stop sign', hint: 'Vehicle must decelerate, stop and hold before proceeding.',
    needsLane: true, needsSeverity: false, needsSpeedLimit: false,
    needsLightState: false, needsBarrierState: false, needsActorSpeed: false
  },
  TRAFFIC_LIGHT: {
    title: 'Add traffic light', hint: 'Red requires a stop, yellow decelerates, green allows progress.',
    needsLane: true, needsSeverity: false, needsSpeedLimit: false,
    needsLightState: true, needsBarrierState: false, needsActorSpeed: false
  },
  POTHOLE: {
    title: 'Add pothole', hint: 'Camera detects the defect once the vehicle reaches it.',
    needsLane: true, needsSeverity: true, needsSpeedLimit: false,
    needsLightState: false, needsBarrierState: false, needsActorSpeed: false
  },
  STATIC_OBSTACLE: {
    title: 'Add obstacle', hint: 'Detected by the front camera, treated as a road hazard.',
    needsLane: true, needsSeverity: true, needsSpeedLimit: false,
    needsLightState: false, needsBarrierState: false, needsActorSpeed: false
  },
  BARRIER: {
    title: 'Add barrier', hint: 'A closed barrier produces a mandatory stop decision.',
    needsLane: true, needsSeverity: false, needsSpeedLimit: false,
    needsLightState: false, needsBarrierState: true, needsActorSpeed: false
  },
  FRONT_VEHICLE: {
    title: 'Add vehicle', hint: 'Radar measures distance, relative speed and time-to-collision.',
    needsLane: true, needsSeverity: false, needsSpeedLimit: false,
    needsLightState: false, needsBarrierState: false, needsActorSpeed: true
  },
  PEDESTRIAN: {
    title: 'Add pedestrian', hint: 'Camera detects the pedestrian inside its range only.',
    needsLane: true, needsSeverity: false, needsSpeedLimit: false,
    needsLightState: false, needsBarrierState: false, needsActorSpeed: true
  }
};

/**
 * Drawer used to configure and add a world object while the simulation is running.
 * Emits REST payloads only; it never decides anything about driving.
 */
@Component({
  selector: 'app-element-properties',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './element-properties.component.html',
  styleUrl: './element-properties.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ElementPropertiesComponent {
  readonly createRoadElement = output<CreateRoadElementRequest>();
  readonly createActor = output<CreateActorRequest>();

  readonly selectedAction = signal<ToolboxAction | null>(null);
  readonly lane = signal('CENTER');
  readonly distanceAheadMeters = signal(150);
  readonly speedLimitKmh = signal(50);
  readonly trafficLightState = signal('RED');
  readonly barrierOpen = signal(false);
  readonly hazardSeverity = signal('HIGH');
  readonly actorSpeedKmh = signal(30);
  readonly submitted = signal(false);

  protected readonly definition = computed(() => {
    const action = this.selectedAction();
    return action ? ACTIONS[action] : null;
  });

  /** Mirrors the backend request validation so invalid input never reaches the API. */
  protected readonly distanceError = computed(() => {
    if (!this.submitted()) {
      return null;
    }
    const value = this.distanceAheadMeters();
    if (!Number.isFinite(value) || value < 1 || value > 1000) {
      return 'Distance must be between 1 and 1000 m.';
    }
    return null;
  });

  protected readonly speedError = computed(() => {
    if (!this.submitted()) {
      return null;
    }
    const definition = this.definition();
    if (!definition) {
      return null;
    }
    if (definition.needsSpeedLimit) {
      const value = this.speedLimitKmh();
      return value >= 20 && value <= 130 ? null : 'Limit must be between 20 and 130 km/h.';
    }
    if (definition.needsActorSpeed) {
      const value = this.actorSpeedKmh();
      return value >= 0 && value <= 180 ? null : 'Speed must be between 0 and 180 km/h.';
    }
    return null;
  });

  protected readonly invalid = computed(() => !!this.distanceError() || !!this.speedError());

  /** Opens the drawer, pre-filled with an optional default limit (speed presets). */
  open(action: ToolboxAction, defaultLimit?: number): void {
    if (defaultLimit !== undefined) {
      this.speedLimitKmh.set(defaultLimit);
    }
    if (action === 'PEDESTRIAN') {
      this.actorSpeedKmh.set(4.5);
    }
    if (action === 'BARRIER') {
      this.lane.set('RIGHT');
    } else if (action === 'POTHOLE' || action === 'STATIC_OBSTACLE') {
      this.lane.set('CENTER');
    }
    this.submitted.set(false);
    this.selectedAction.set(action);
  }

  protected close(): void {
    this.selectedAction.set(null);
    this.submitted.set(false);
  }

  protected confirm(): void {
    this.submitted.set(true);
    if (this.invalid() || !this.definition()) {
      return;
    }

    const action = this.selectedAction();
    if (!action) {
      return;
    }

    switch (action) {
      case 'SPEED_LIMIT':
        // One sign in the map, valid for the whole road: the road view repeats it in every lane.
        this.createRoadElement.emit({
          type: 'SPEED_LIMIT',
          lane: 'CENTER',
          distanceAheadMeters: this.distanceAheadMeters(),
          speedLimitKmh: this.speedLimitKmh()
        });
        break;
      case 'STOP_SIGN':
        this.createRoadElement.emit(this.simpleElement('STOP_SIGN'));
        break;
      case 'TRAFFIC_LIGHT':
        this.createRoadElement.emit({
          ...this.simpleElement('TRAFFIC_LIGHT'),
          trafficLightState: this.trafficLightState()
        });
        break;
      case 'POTHOLE':
        this.createRoadElement.emit({ ...this.simpleElement('POTHOLE'), severity: this.hazardSeverity() });
        break;
      case 'STATIC_OBSTACLE':
        this.createRoadElement.emit({ ...this.simpleElement('STATIC_OBSTACLE'), severity: this.hazardSeverity() });
        break;
      case 'BARRIER':
        this.createRoadElement.emit({ ...this.simpleElement('BARRIER'), barrierOpen: this.barrierOpen() });
        break;
      case 'FRONT_VEHICLE':
        this.createActor.emit({
          type: 'FRONT_VEHICLE',
          lane: this.lane(),
          distanceAheadMeters: this.distanceAheadMeters(),
          speedKmh: this.actorSpeedKmh()
        });
        break;
      case 'PEDESTRIAN':
        this.createActor.emit({
          type: 'PEDESTRIAN',
          lane: this.lane(),
          distanceAheadMeters: this.distanceAheadMeters(),
          speedKmh: Math.min(this.actorSpeedKmh(), 6)
        });
        break;
    }

    this.close();
  }

  protected setLane(value: string): void {
    this.lane.set(value);
  }

  protected setDistance(value: string): void {
    this.distanceAheadMeters.set(Number(value));
  }

  protected setSpeedLimit(value: string): void {
    this.speedLimitKmh.set(Number(value));
  }

  protected setTrafficLightState(value: string): void {
    this.trafficLightState.set(value);
  }

  protected setBarrierOpen(value: string): void {
    this.barrierOpen.set(value === 'true');
  }

  protected setSeverity(value: string): void {
    this.hazardSeverity.set(value);
  }

  protected setActorSpeed(value: string): void {
    this.actorSpeedKmh.set(Number(value));
  }

  private simpleElement(type: string): CreateRoadElementRequest {
    return {
      type,
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters()
    };
  }
}
