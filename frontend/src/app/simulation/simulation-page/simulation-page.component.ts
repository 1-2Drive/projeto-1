import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { SimulationStateService } from '../../core/state/simulation-state.service';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { FlowStageComponent } from '../../shared/flow-stage/flow-stage.component';
import { ToolboxComponent } from '../../scenario/toolbox/toolbox.component';
import { ScenarioLibraryComponent } from '../../scenario/scenario-library/scenario-library.component';
import { RoadCanvasComponent } from '../road-canvas/road-canvas.component';
import { VehicleStateComponent } from '../../telemetry/vehicle-state/vehicle-state.component';
import { SpeedAdvisorComponent } from '../../telemetry/speed-advisor/speed-advisor.component';
import { SensorsPanelComponent } from '../../telemetry/sensors-panel/sensors-panel.component';
import { DecisionPanelComponent } from '../../expert-system/decision-panel/decision-panel.component';
import { RuleTimelineComponent } from '../../expert-system/rule-timeline/rule-timeline.component';
import { WorkingMemoryComponent } from '../../expert-system/working-memory/working-memory.component';
import { ExplanationPanelComponent } from '../../expert-system/explanation-panel/explanation-panel.component';
import { CreateActorRequest, CreateRoadElementRequest, EnvironmentUpdateRequest } from '../../core/models/api.models';
import { WorldStateComponent } from '../world-state/world-state.component';

@Component({
  selector: 'app-simulation-page',
  standalone: true,
  imports: [
    StatusBadgeComponent,
    FlowStageComponent,
    ToolboxComponent,
    ScenarioLibraryComponent,
    RoadCanvasComponent,
    VehicleStateComponent,
    SpeedAdvisorComponent,
    SensorsPanelComponent,
    DecisionPanelComponent,
    RuleTimelineComponent,
    WorkingMemoryComponent,
    ExplanationPanelComponent,
    WorldStateComponent
  ],
  templateUrl: './simulation-page.component.html',
  styleUrl: './simulation-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SimulationPageComponent implements OnInit, OnDestroy {
  protected readonly state = inject(SimulationStateService);

  protected readonly vm = this.state.vm;
  protected readonly snapshot = this.state.snapshot;
  protected readonly scenarios = this.state.scenarios;

  protected readonly activeTab = signal<'timeline' | 'memory' | 'explanation' | 'world'>('timeline');
  protected readonly simulationSeconds = computed(() => ((this.snapshot()?.simulationTimeMillis ?? 0) / 1000).toFixed(1));

  ngOnInit(): void {
    this.state.initialize();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected setTab(tab: 'timeline' | 'memory' | 'explanation' | 'world'): void {
    this.activeTab.set(tab);
  }

  protected start(): void {
    this.state.start();
  }

  protected pause(): void {
    this.state.pause();
  }

  protected resume(): void {
    this.state.resume();
  }

  protected stop(): void {
    this.state.stop();
  }

  protected reset(): void {
    this.state.reset();
  }

  protected loadScenario(id: string): void {
    this.state.loadScenario(id);
  }

  protected addRoadElement(request: CreateRoadElementRequest): void {
    this.state.addRoadElement(request);
  }

  protected addActor(request: CreateActorRequest): void {
    this.state.addActor(request);
  }

  protected updateEnvironment(request: EnvironmentUpdateRequest): void {
    this.state.updateEnvironment(request);
  }
}
