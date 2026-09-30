import { ChangeDetectionStrategy, Component, input, output, signal, ViewChild } from '@angular/core';
import { ElementPropertiesComponent, ToolboxAction } from '../element-properties/element-properties.component';
import { CreateActorRequest, CreateRoadElementRequest, EnvironmentUpdateRequest } from '../../core/models/api.models';

const SPEED_PRESETS = [30, 50, 70, 90, 100, 120];

/**
 * Scenario toolbox: creates world objects and changes environment through the REST
 * API. It contains no driving policy; Drools remains the only decision authority.
 */
@Component({
  selector: 'app-toolbox',
  standalone: true,
  imports: [ElementPropertiesComponent],
  templateUrl: './toolbox.component.html',
  styleUrl: './toolbox.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ToolboxComponent {
  @ViewChild(ElementPropertiesComponent) private drawer?: ElementPropertiesComponent;

  readonly simulationStatus = input<'RUNNING' | 'PAUSED' | 'STOPPED'>('STOPPED');
  readonly createRoadElement = output<CreateRoadElementRequest>();
  readonly createActor = output<CreateActorRequest>();
  readonly updateEnvironment = output<EnvironmentUpdateRequest>();

  protected readonly speedPresets = SPEED_PRESETS;

  protected readonly rain = signal(true);
  protected readonly visibilityPercent = signal(100);
  protected readonly gripPercent = signal(100);

  protected add(action: ToolboxAction, limit?: number): void {
    this.drawer?.open(action, limit);
  }

  protected setVisibility(value: string): void {
    const percent = Number(value);
    this.visibilityPercent.set(percent);
    this.updateEnvironment.emit({ visibilityFactor: percent / 100 });
  }

  protected setGrip(value: string): void {
    const percent = Number(value);
    this.gripPercent.set(percent);
    this.updateEnvironment.emit({ gripFactor: percent / 100 });
  }

  protected toggleRain(): void {
    const next = !this.rain();
    this.rain.set(next);
    this.updateEnvironment.emit({ rain: next });
  }
}
