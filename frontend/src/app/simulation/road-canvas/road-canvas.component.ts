import { DecimalPipe, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiRoadElement, ApiSimulationSnapshot } from '../../core/models/api.models';

interface RenderItem {
  id: string;
  label: string;
  kind: string;
  typeClass: string;
  x: number;
  y: number;
  relativeMeters: number;
  detectedByCamera: boolean;
  detectedByRadar: boolean;
}

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

  readonly worldForwardMeters = 240;
  readonly worldBackwardMeters = 40;

  protected renderItems(): RenderItem[] {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return [];
    }
    return snapshot.worldObjects
      .filter((item) => item.positionMeters >= -this.worldBackwardMeters && item.positionMeters <= this.worldForwardMeters)
      .map((item) => ({
        id: item.id,
        label: this.typeLabel(item),
        kind: item.type,
        typeClass: this.typeClass(item),
        x: this.toPercentX(item.positionMeters),
        y: this.toPercentY(item.lane),
        relativeMeters: item.positionMeters,
        detectedByCamera: snapshot.cameraDetectedObjectIds.includes(item.id),
        detectedByRadar: snapshot.radarDetectedObjectIds.includes(item.id)
      }));
  }

  protected laneGuides(): number[] {
    return [27, 50, 73];
  }

  protected shouldUseDetail(item: RenderItem): boolean {
    return item.typeClass === 'vehicle' || item.typeClass === 'pedestrian';
  }

  protected egoLaneY(): number {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return 50;
    }
    return this.toPercentY(snapshot.egoVehicle.lane);
  }

  private toPercentX(relativeMeters: number): number {
    const normalized = (relativeMeters + this.worldBackwardMeters) / (this.worldForwardMeters + this.worldBackwardMeters);
    return Math.max(0, Math.min(100, normalized * 100));
  }

  private toPercentY(lane: 'LEFT' | 'CENTER' | 'RIGHT'): number {
    switch (lane) {
      case 'LEFT':
        return 27;
      case 'RIGHT':
        return 73;
      default:
        return 50;
    }
  }

  private typeLabel(item: ApiRoadElement): string {
    if (item.type.includes('SpeedLimit')) {
      const match = item.details.match(/limitKmh=(\d+)/);
      return match ? match[1] : 'SL';
    }
    if (item.type.includes('Pedestrian')) {
      return 'PED';
    }
    if (item.type.includes('Vehicle')) {
      return 'CAR';
    }
    if (item.type.includes('Pothole')) {
      return 'PH';
    }
    if (item.type.includes('Roadworks')) {
      return 'RW';
    }
    if (item.type.includes('Barrier')) {
      return 'BAR';
    }
    if (item.type.includes('TrafficLight')) {
      return 'TL';
    }
    if (item.type.includes('StopSign')) {
      return 'STOP';
    }
    if (item.type.includes('YieldSign')) {
      return 'YLD';
    }
    if (item.type.includes('Roundabout')) {
      return 'RB';
    }
    return item.type.slice(0, 3).toUpperCase();
  }

  private typeClass(item: ApiRoadElement): string {
    if (item.type.includes('Pedestrian')) {
      return 'pedestrian';
    }
    if (item.type.includes('Vehicle')) {
      return 'vehicle';
    }
    if (item.type.includes('SpeedLimit') || item.type.includes('StopSign') || item.type.includes('YieldSign')) {
      return 'sign';
    }
    if (item.type.includes('TrafficLight') || item.type.includes('Barrier')) {
      return 'control';
    }
    if (item.type.includes('Pothole') || item.type.includes('Roadworks') || item.type.includes('Obstacle')) {
      return 'hazard';
    }
    return 'structure';
  }
}
