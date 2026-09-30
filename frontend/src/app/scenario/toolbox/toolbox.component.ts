import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ElementPropertiesComponent } from '../element-properties/element-properties.component';
import { CreateActorRequest, CreateRoadElementRequest, EnvironmentUpdateRequest } from '../../core/models/api.models';

@Component({
  selector: 'app-toolbox',
  standalone: true,
  imports: [ElementPropertiesComponent],
  template: `
    <app-element-properties
      (createRoadElement)="createRoadElement.emit($event)"
      (createActor)="createActor.emit($event)"
    />

    <section class="environment">
      <h3>Environment</h3>
      <div class="buttons">
        <button type="button" (click)="updateEnvironment.emit({ rain: true })">Rain</button>
        <button type="button" (click)="updateEnvironment.emit({ rain: false, visibilityFactor: 1, gripFactor: 1 })">Clear</button>
        <button type="button" (click)="updateEnvironment.emit({ visibilityFactor: 0.55 })">Low visibility</button>
        <button type="button" (click)="updateEnvironment.emit({ gripFactor: 0.55 })">Low grip</button>
      </div>
      <p class="hint">Simulation: {{ simulationStatus() }}</p>
    </section>
  `,
  styleUrl: './toolbox.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ToolboxComponent {
  readonly simulationStatus = input<'RUNNING' | 'PAUSED' | 'STOPPED'>('STOPPED');
  readonly createRoadElement = output<CreateRoadElementRequest>();
  readonly createActor = output<CreateActorRequest>();
  readonly updateEnvironment = output<EnvironmentUpdateRequest>();
}
