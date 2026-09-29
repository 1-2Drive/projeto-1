import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-flow-stage',
  standalone: true,
  template: `
    <div class="flow">
      @for (step of steps; track step) {
        <span class="step" [class.active]="step === activeStage()">{{ step }}</span>
        @if (!$last) {
          <span class="arrow">→</span>
        }
      }
    </div>
  `,
  styleUrl: './flow-stage.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FlowStageComponent {
  readonly activeStage = input<string>('WORLD');

  protected readonly steps = ['WORLD', 'SENSORS', 'WORKING_MEMORY', 'RULES', 'DECISION', 'ACTUATOR'];
}
