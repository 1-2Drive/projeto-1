import { ChangeDetectionStrategy, Component, computed, input, output, ViewChild } from '@angular/core';
import { ElementPropertiesComponent, ToolboxAction } from '../element-properties/element-properties.component';
import { ApiSimulationSnapshot, CreateActorRequest, CreateRoadElementRequest, EnvironmentUpdateRequest } from '../../core/models/api.models';

const SPEED_PRESETS = [30, 50, 70, 90, 100, 120];

/**
 * Scenario toolbox: creates world objects and changes environment through the REST
 * API. It contains no driving policy; Drools remains the only decision authority.
 *
 * The environment controls read the live snapshot instead of holding local state, so
 * the switch and the sliders always show what the world actually is after a scenario
 * load, a reset or a backend-side change.
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
  readonly environment = input<ApiSimulationSnapshot['environment'] | null>(null);
  readonly createRoadElement = output<CreateRoadElementRequest>();
  readonly createActor = output<CreateActorRequest>();
  readonly updateEnvironment = output<EnvironmentUpdateRequest>();

  protected readonly speedPresets = SPEED_PRESETS;

  protected readonly rain = computed(() =>
    (this.environment()?.weatherCondition ?? '').toUpperCase() === 'RAIN'
  );
  protected readonly visibilityPercent = computed(() =>
    Math.round((this.environment()?.visibilityFactor ?? 1) * 100)
  );
  protected readonly gripPercent = computed(() =>
    Math.round((this.environment()?.gripFactor ?? 1) * 100)
  );

  protected add(action: ToolboxAction, limit?: number): void {
    this.drawer?.open(action, limit);
  }

  protected setVisibility(value: string): void {
    this.updateEnvironment.emit({ visibilityFactor: Number(value) / 100 });
  }

  protected setGrip(value: string): void {
    this.updateEnvironment.emit({ gripFactor: Number(value) / 100 });
  }

  protected toggleRain(): void {
    this.updateEnvironment.emit({ rain: !this.rain() });
  }
}
