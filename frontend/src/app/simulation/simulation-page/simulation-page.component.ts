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
  protected readonly simulationClock = computed(() => {
    const millis = this.snapshot()?.simulationTimeMillis ?? 0;
    const mm = Math.floor(millis / 60_000).toString().padStart(2, '0');
    const ss = Math.floor((millis % 60_000) / 1_000).toString().padStart(2, '0');
    const mmm = Math.floor(millis % 1_000).toString().padStart(3, '0');
    return `${mm}:${ss}.${mmm}`;
  });
  protected readonly simulationStatus = computed(() => this.vm().connection.simulationStatus);
  protected readonly isRunning = computed(() => this.simulationStatus() === 'RUNNING');
  protected readonly isPaused = computed(() => this.simulationStatus() === 'PAUSED');
  protected readonly canStart = computed(() => !this.vm().loading && this.simulationStatus() === 'STOPPED');
  protected readonly canPause = computed(() => !this.vm().loading && this.isRunning());
  protected readonly canResume = computed(() => !this.vm().loading && this.isPaused());
  protected readonly canStop = computed(() => !this.vm().loading && this.simulationStatus() !== 'STOPPED');
  protected readonly canReset = computed(() => !this.vm().loading);

  /** Backend only publishes demo phases while the scripted scenario is loaded. */
  protected readonly demoPhases = computed(() => this.snapshot()?.demoPhases ?? []);
  protected readonly demoRunning = computed(() => this.demoPhases().length > 0);

  /** True until the first tick, used to invite the user to press START. */
  protected readonly awaitingStart = computed(() => {
    const snapshot = this.snapshot();
    return !!snapshot && snapshot.simulationTimeMillis === 0 && snapshot.status === 'STOPPED';
  });

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

  protected runPresentationDemo(): void {
    this.state.runPresentationDemo();
  }

  protected restartDemo(): void {
    this.state.runPresentationDemo();
  }

  protected stopDemo(): void {
    this.state.stop();
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

  protected clearError(): void {
    this.state.clearError();
  }
}
