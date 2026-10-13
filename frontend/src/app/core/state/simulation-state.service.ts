import { computed, inject, Injectable, signal } from '@angular/core';
import { BackendApiService } from '../api/backend-api.service';
import { RealtimeStreamService } from '../websocket/realtime-stream.service';
import {
  ApiSimulationSnapshot,
  CreateActorRequest,
  CreateRoadElementRequest,
  EnvironmentUpdateRequest,
  MapCellRequest,
  ScenarioSummary,
  UiModel,
  UiStatus
} from '../models/api.models';
import { finalize, forkJoin, Observable, Subscription } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SimulationStateService {
  private readonly api = inject(BackendApiService);
  private readonly stream = inject(RealtimeStreamService);
  private streamSubscription?: Subscription;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private destroyed = false;

  private readonly state = signal<UiModel>({
    snapshot: null,
    scenarios: [],
    connection: {
      backendConnected: false,
      droolsActive: false,
      simulationStatus: 'STOPPED',
      streamStatus: 'DISCONNECTED'
    },
    error: null,
    loading: false
  });

  readonly vm = computed(() => this.state());
  readonly snapshot = computed(() => this.state().snapshot);
  readonly scenarios = computed(() => this.state().scenarios);
  readonly connection = computed(() => this.state().connection);
  readonly error = computed(() => this.state().error);
  readonly loading = computed(() => this.state().loading);

  initialize(): void {
    this.destroyed = false;
    this.patch({ loading: true, error: null });
    forkJoin({
      snapshot: this.api.getState(),
      status: this.api.getSystemStatus(),
      scenarios: this.api.getScenarios()
    })
      .pipe(finalize(() => this.patch({ loading: false })))
      .subscribe({
        next: ({ snapshot, status, scenarios }) => {
          this.patch({
            snapshot,
            scenarios,
            connection: {
              backendConnected: status.backend === 'CONNECTED',
              droolsActive: status.drools === 'ACTIVE',
              simulationStatus: snapshot.status,
              streamStatus: 'RECONNECTING'
            }
          });
          this.connectRealtime();
        },
        error: () => this.patch({
          error: 'Backend unavailable. Start Java server and refresh.',
          connection: { backendConnected: false, droolsActive: false, simulationStatus: 'STOPPED', streamStatus: 'DISCONNECTED' }
        })
      });
  }

  start(): void {
    this.runCommand(this.api.startSimulation(), 'Unable to start simulation.', 'RUNNING');
  }

  pause(): void {
    this.runCommand(this.api.pauseSimulation(), 'Unable to pause simulation.', 'PAUSED');
  }

  resume(): void {
    this.runCommand(this.api.resumeSimulation(), 'Unable to resume simulation.', 'RUNNING');
  }

  stop(): void {
    this.runCommand(this.api.stopSimulation(), 'Unable to stop simulation.', 'STOPPED');
  }

  reset(): void {
    this.runCommand(this.api.resetSimulation(), 'Unable to reset simulation.', 'STOPPED');
  }

  /** Driver request only; Drools decides whether the target lane is safe. */
  requestLaneChange(direction: 'LEFT' | 'RIGHT'): void {
    this.runCommand(this.api.requestLaneChange(direction), `Unable to request lane change ${direction.toLowerCase()}.`);
  }

  loadScenario(id: string): void {
    this.api.loadScenario(id).subscribe({
      next: (response) => {
        if (!response.success) this.patch({ error: response.message || `Unable to load scenario ${id}.` });
      },
      error: (err) => this.patch({ error: this.errorMessage(err, `Unable to load scenario ${id}.`) })
    });
  }

  runPresentationDemo(scenarioId = 'S12_PRESENTATION_DEMO'): void {
    this.api.loadScenario(scenarioId).subscribe({
      next: (response) => response.success ? this.start() : this.patch({ error: response.message }),
      error: (err) => this.patch({ error: this.errorMessage(err, `Unable to run demo scenario ${scenarioId}.`) })
    });
  }

  /** Paints one road map cell; the new matrix arrives with the next snapshot. */
  setMapCell(request: MapCellRequest): void {
    this.api.setMapCell(request).subscribe({
      next: (response) => {
        if (!response.success) this.patch({ error: response.message });
      },
      error: (err) => this.patch({ error: this.errorMessage(err, 'Failed to update the road map.') })
    });
  }

  addRoadElement(payload: CreateRoadElementRequest): void {
    this.api.addRoadElement(payload).subscribe({
      next: (response) => {
        if (!response.success) this.patch({ error: response.message });
      },
      error: (err) => this.patch({ error: this.errorMessage(err, 'Failed to add road element.') })
    });
  }

  addActor(payload: CreateActorRequest): void {
    this.api.addActor(payload).subscribe({
      next: (response) => {
        if (!response.success) this.patch({ error: response.message });
      },
      error: (err) => this.patch({ error: this.errorMessage(err, 'Failed to add actor.') })
    });
  }

  updateEnvironment(payload: EnvironmentUpdateRequest): void {
    this.api.updateEnvironment(payload).subscribe({
      next: (response) => {
        if (!response.success) this.patch({ error: response.message });
      },
      error: (err) => this.patch({ error: this.errorMessage(err, 'Failed to update environment.') })
    });
  }

  clearError(): void {
    this.patch({ error: null });
  }

  private connectRealtime(): void {
    if (this.destroyed) {
      return;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }
    this.streamSubscription?.unsubscribe();
    this.patch({
      connection: {
        ...this.state().connection,
        streamStatus: 'RECONNECTING'
      }
    });

    this.streamSubscription = this.stream.connect().subscribe({
      next: (message) => {
        this.patch({
          error: null,
          snapshot: message.payload,
          connection: {
            backendConnected: true,
            droolsActive: true,
            simulationStatus: message.payload.status,
            streamStatus: 'LIVE'
          }
        });
      },
      error: () => {
        this.patch({
          error: 'Realtime channel disconnected. Trying to reconnect...',
          connection: {
            ...this.state().connection,
            backendConnected: false,
            droolsActive: false,
            streamStatus: 'RECONNECTING'
          }
        });
        this.reconnectTimer = setTimeout(() => this.connectRealtime(), 1200);
      }
    });
  }

  private runCommand(command$: Observable<unknown>, fallbackMessage: string, status?: 'RUNNING' | 'PAUSED' | 'STOPPED'): void {
    command$.subscribe({
      next: (response: unknown) => {
        const command = response as { success?: boolean; message?: string };
        if (command.success === false) {
          this.patch({ error: command.message || fallbackMessage });
          return;
        }
        if (status) {
          this.patch({ connection: { ...this.state().connection, simulationStatus: status } });
        }
      },
      error: (err: unknown) => this.patch({ error: this.errorMessage(err, fallbackMessage) })
    });
  }

  private errorMessage(error: unknown, fallback: string): string {
    const maybeMessage = (error as { error?: { message?: string } })?.error?.message;
    return maybeMessage && typeof maybeMessage === 'string' ? maybeMessage : fallback;
  }

  private patch(partial: Partial<UiModel>): void {
    this.state.update((current) => ({ ...current, ...partial }));
  }

  destroy(): void {
    this.destroyed = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }
    this.streamSubscription?.unsubscribe();
    this.patch({
      connection: {
        ...this.state().connection,
        streamStatus: 'DISCONNECTED'
      }
    });
  }
}
