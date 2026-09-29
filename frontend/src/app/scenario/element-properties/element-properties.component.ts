import { ChangeDetectionStrategy, Component, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CreateActorRequest, CreateRoadElementRequest } from '../../core/models/api.models';

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
      severity: 'HIGH'
    });
  }

  protected submitRoadworks(): void {
    this.createRoadElement.emit({
      type: 'ROADWORKS',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      severity: 'MEDIUM'
    });
  }

  protected submitRoundabout(): void {
    this.createRoadElement.emit({
      type: 'ROUNDABOUT',
      lane: this.lane(),
      distanceAheadMeters: this.distanceAheadMeters(),
      roundaboutApproachSpeedKmh: 30
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
      speedKmh: 4
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
}
