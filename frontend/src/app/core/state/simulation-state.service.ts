import { computed, inject, Injectable, signal } from '@angular/core';
import { BackendApiService } from '../api/backend-api.service';
import { RealtimeStreamService } from '../websocket/realtime-stream.service';
import {
  ApiSimulationSnapshot,
  CreateActorRequest,
  CreateRoadElementRequest,
  EnvironmentUpdateRequest,
  ScenarioSummary,
  UiModel,
  UiStatus
} from '../models/api.models';
import { finalize, forkJoin, Subscription } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SimulationStateService {
  private readonly api = inject(BackendApiService);
  private readonly stream = inject(RealtimeStreamService);
  private streamSubscription?: Subscription;

  private readonly state = signal<UiModel>({
    snapshot: null,
    scenarios: [],
    connection: {
      backendConnected: false,
      droolsActive: false,
      simulationStatus: 'STOPPED'
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
              simulationStatus: snapshot.status
            }
          });
          this.connectRealtime();
        },
        error: () => this.patch({
          error: 'Backend unavailable. Start Java server and refresh.',
          connection: { backendConnected: false, droolsActive: false, simulationStatus: 'STOPPED' }
        })
      });
  }

  start(): void {
    this.api.startSimulation().subscribe({
      error: () => this.patch({ error: 'Unable to start simulation.' })
    });
  }

  pause(): void {
    this.api.pauseSimulation().subscribe({
      error: () => this.patch({ error: 'Unable to pause simulation.' })
    });
  }

  resume(): void {
    this.api.resumeSimulation().subscribe({
      error: () => this.patch({ error: 'Unable to resume simulation.' })
    });
  }

  stop(): void {
    this.api.stopSimulation().subscribe({
      error: () => this.patch({ error: 'Unable to stop simulation.' })
    });
  }

  reset(): void {
    this.api.resetSimulation().subscribe({
      error: () => this.patch({ error: 'Unable to reset simulation.' })
    });
  }

  loadScenario(id: string): void {
    this.api.loadScenario(id).subscribe({
      error: () => this.patch({ error: `Unable to load scenario ${id}.` })
    });
  }

  addRoadElement(payload: CreateRoadElementRequest): void {
    this.api.addRoadElement(payload).subscribe({
      error: () => this.patch({ error: 'Failed to add road element.' })
    });
  }

  addActor(payload: CreateActorRequest): void {
    this.api.addActor(payload).subscribe({
      error: () => this.patch({ error: 'Failed to add actor.' })
    });
  }

  updateEnvironment(payload: EnvironmentUpdateRequest): void {
    this.api.updateEnvironment(payload).subscribe({
      error: () => this.patch({ error: 'Failed to update environment.' })
    });
  }

  clearError(): void {
    this.patch({ error: null });
  }

  private connectRealtime(): void {
    this.streamSubscription?.unsubscribe();
    this.streamSubscription = this.stream.connect().subscribe({
      next: (message) => {
        this.patch({
          snapshot: message.payload,
          connection: {
            backendConnected: true,
            droolsActive: true,
            simulationStatus: message.payload.status
          }
        });
      },
      error: () => {
        this.patch({
          error: 'Realtime channel disconnected.',
          connection: {
            ...this.state().connection,
            backendConnected: false,
            droolsActive: false
          }
        });
      }
    });
  }

  private patch(partial: Partial<UiModel>): void {
    this.state.update((current) => ({ ...current, ...partial }));
  }

  destroy(): void {
    this.streamSubscription?.unsubscribe();
  }
}
