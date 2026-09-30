import { ChangeDetectionStrategy, Component, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CreateActorRequest, CreateRoadElementRequest } from '../../core/models/api.models';

type ToolboxAction =
  | 'SPEED_LIMIT'
  | 'STOP_SIGN'
  | 'YIELD_SIGN'
  | 'TRAFFIC_LIGHT'
  | 'ROUNDABOUT'
  | 'POTHOLE'
  | 'ROADWORKS'
  | 'STATIC_OBSTACLE'
  | 'BARRIER'
  | 'FRONT_VEHICLE'
  | 'PEDESTRIAN';

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

  readonly lane = signal('CENTER');
  readonly distanceAheadMeters = signal(150);
  readonly actorSpeedKmh = signal(30);
  readonly selectedAction = signal<ToolboxAction | null>(null);
  readonly speedLimitKmh = signal(50);
  readonly trafficLightState = signal('RED');
  readonly hazardSeverity = signal('HIGH');
  readonly barrierOpen = signal(false);
  readonly roundaboutSpeedKmh = signal(30);

  protected openConfig(action: ToolboxAction): void {
    this.selectedAction.set(action);
  }

  protected closeConfig(): void {
    this.selectedAction.set(null);
  }

  protected submitConfiguredAction(): void {
    const action = this.selectedAction();
    if (!action) {
      return;
    }
    switch (action) {
      case 'SPEED_LIMIT':
        this.submitSign(this.speedLimitKmh());
        break;
      case 'STOP_SIGN':
        this.submitStop();
        break;
      case 'YIELD_SIGN':
        this.submitYield();
        break;
      case 'TRAFFIC_LIGHT':
        this.submitTrafficLight(this.trafficLightState());
        break;
      case 'ROUNDABOUT':
        this.submitRoundabout();
        break;
      case 'POTHOLE':
        this.submitPothole();
        break;
      case 'ROADWORKS':
        this.submitRoadworks();
        break;
      case 'STATIC_OBSTACLE':
        this.submitStaticObstacle();
        break;
      case 'BARRIER':
        this.submitBarrier(this.barrierOpen());
        break;
      case 'FRONT_VEHICLE':
        this.submitVehicle();
        break;
      case 'PEDESTRIAN':
        this.submitPedestrian();
        break;
    }
    this.closeConfig();
  }

  protected quickSpeedLimit(limit: number): void {
    this.speedLimitKmh.set(limit);
    this.submitSign(limit);
  }

  protected submitSign(limit: number): void {
    this.createRoadElement.emit({
      type: 'SPEED_LIMIT',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      speedLimitKmh: limit
    });
  }

  protected submitStop(): void {
    this.createRoadElement.emit({ type: 'STOP_SIGN', lane: this.lane(), distanceAheadMeters: this.distanceAheadMeters() });
  }

  protected submitYield(): void {
    this.createRoadElement.emit({ type: 'YIELD_SIGN', lane: this.lane(), distanceAheadMeters: this.distanceAheadMeters() });
  }

  protected submitTrafficLight(state: string): void {
    this.createRoadElement.emit({
      type: 'TRAFFIC_LIGHT',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      trafficLightState: state
    });
  }

  protected submitBarrier(open: boolean): void {
    this.createRoadElement.emit({
      type: 'BARRIER',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      barrierOpen: open
    });
  }

  protected submitPothole(): void {
    this.createRoadElement.emit({
      type: 'POTHOLE',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      severity: this.hazardSeverity()
    });
  }

  protected submitRoadworks(): void {
    this.createRoadElement.emit({
      type: 'ROADWORKS',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      severity: this.hazardSeverity()
    });
  }

  protected submitStaticObstacle(): void {
    this.createRoadElement.emit({
      type: 'STATIC_OBSTACLE',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      severity: this.hazardSeverity()
    });
  }

  protected submitRoundabout(): void {
    this.createRoadElement.emit({
      type: 'ROUNDABOUT',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      roundaboutApproachSpeedKmh: this.roundaboutSpeedKmh()
    });
  }

  protected submitVehicle(): void {
    this.createActor.emit({
      type: 'FRONT_VEHICLE',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      speedKmh: this.actorSpeedKmh()
    });
  }

  protected submitPedestrian(): void {
    this.createActor.emit({
      type: 'PEDESTRIAN',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      speedKmh: Math.min(this.actorSpeedKmh(), 10)
    });
  }

  protected setLane(value: string): void {
    this.lane.set(value);
  }

  protected setDistance(value: string): void {
    this.distanceAheadMeters.set(Math.max(1, Number(value)));
  }

  protected setActorSpeed(value: string): void {
    this.actorSpeedKmh.set(Math.max(0, Number(value)));
  }

  protected setSpeedLimit(value: string): void {
    this.speedLimitKmh.set(Math.max(20, Math.min(130, Number(value))));
  }

  protected setTrafficLightState(value: string): void {
    this.trafficLightState.set(value);
  }

  protected setSeverity(value: string): void {
    this.hazardSeverity.set(value);
  }

  protected setBarrierOpen(value: string): void {
    this.barrierOpen.set(value === 'true');
  }

  protected setRoundaboutSpeed(value: string): void {
    this.roundaboutSpeedKmh.set(Math.max(10, Math.min(80, Number(value))));
  }
}
