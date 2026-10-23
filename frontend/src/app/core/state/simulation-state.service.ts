import { computed, inject, Injectable, signal } from '@angular/core';
import { BackendApiService } from '../api/backend-api.service';
import { RealtimeStreamService } from '../websocket/realtime-stream.service';
import {
  ApiSimulationSnapshot,
  ApiSessionSummary,
  ApiWhyAnswer,
  CreateActorRequest,
  CreateRoadElementRequest,
  EnvironmentUpdateRequest,
  ExpertBackend,
  MapCellRequest,
  ScenarioSummary,
  UiModel,
  UiStatus
} from '../models/api.models';
import { finalize, forkJoin, Observable, Subscription } from 'rxjs';

/** First wait before reopening a dropped realtime channel; it doubles per failed attempt. */
const RECONNECT_BASE_MS = 1000;
const RECONNECT_MAX_MS = 8000;

@Injectable({
  providedIn: 'root'
})
export class SimulationStateService {
  private readonly api = inject(BackendApiService);
  private readonly stream = inject(RealtimeStreamService);
  private streamSubscription?: Subscription;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private reconnectAttempts = 0;
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
  readonly expertBackend = computed<ExpertBackend>(() => this.state().snapshot?.expertBackend ?? 'DROOLS');
  readonly expertBackendError = computed(() => this.state().snapshot?.expertBackendError ?? null);
  private readonly historyRefresh = signal(0);
  readonly historyVersion = computed(() => this.historyRefresh());
  private readonly historicalTimestamp = signal<number | null>(null);
  readonly selectedHistoricalTimestamp = computed(() => this.historicalTimestamp());
  private readonly historicalSessionId = signal<string | null>(null);
  readonly selectedHistoricalSessionId = computed(() => this.historicalSessionId());
  private readonly stoppedSession = signal<string | null>(null);
  readonly stoppedSessionId = computed(() => this.stoppedSession());
  private readonly stopError = signal<string | null>(null);
  readonly stopPersistenceError = computed(() => this.stopError());
  private readonly stopRefresh = signal(0);
  readonly stopVersion = computed(() => this.stopRefresh());

  initialize(): void {
    this.destroyed = false;
    this.reconnectAttempts = 0;
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
    this.stopError.set(null);
    this.stoppedSession.set(null);
    this.api.stopSimulation().subscribe({
      next: (response) => {
        this.patch({ connection: { ...this.state().connection, simulationStatus: 'STOPPED' } });
        this.historyRefresh.update((v) => v + 1);
        if (response.success && response.sessionId) {
          this.stoppedSession.set(response.sessionId);
          this.stopRefresh.update((v) => v + 1);
        } else if (!response.success) {
          this.stopError.set(response.message || 'Simulation stopped, but the session could not be saved. Historical WHY/WHY-NOT is unavailable for this trip.');
        }
      },
      error: (error) => {
        // The endpoint reports persistence failure after stopping; keep controls stopped.
        this.patch({ connection: { ...this.state().connection, simulationStatus: 'STOPPED' } });
        this.stopError.set(this.errorMessage(error, 'Simulation stopped, but the session could not be saved. Historical WHY/WHY-NOT is unavailable for this trip.'));
      }
    });
  }

  explainSessionDecision(sessionId: string, timestampMillis: number | undefined) {
    return this.api.explainDecision(sessionId, timestampMillis);
  }

  clearStoppedSession(): void {
    this.stoppedSession.set(null);
    this.stopError.set(null);
  }

  reset(): void {
    this.runCommand(this.api.resetSimulation(), 'Unable to reset simulation.', 'STOPPED');
  }

  /**
   * Hands the decisions to another rule engine. The backend reloads the current scenario,
   * so the simulation comes back STOPPED with an empty working memory.
   */
  switchBackend(backend: ExpertBackend): void {
    if (backend === this.expertBackend() || this.state().loading) {
      return;
    }
    this.patch({ loading: true, error: null });
    this.api.switchBackend(backend)
      .pipe(finalize(() => this.patch({ loading: false })))
      .subscribe({
        next: (response) => {
          if (!response.success) {
            this.patch({ error: response.message || `Unable to switch to ${backend}.` });
            return;
          }
          this.patch({ connection: { ...this.state().connection, simulationStatus: 'STOPPED' } });
        },
        error: (err) => this.patch({ error: this.errorMessage(err, `Unable to switch to ${backend}.`) })
      });
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

  openHistoricalDecision(timestamp: number): void {
    this.historicalTimestamp.set(timestamp);
    this.historyRefresh.update((v) => v + 1);
  }

  openHistoricalSession(id: string): void {
    this.historicalSessionId.set(id);
    this.historicalTimestamp.set(null);
    this.historyRefresh.update((v) => v + 1);
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
        this.reconnectAttempts = 0;
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
        // Back off while the backend stays unreachable; the first snapshot resets the wait.
        const delay = Math.min(RECONNECT_MAX_MS, RECONNECT_BASE_MS * 2 ** this.reconnectAttempts);
        this.reconnectAttempts++;
        this.reconnectTimer = setTimeout(() => this.connectRealtime(), delay);
      }
    });
  }

  private runCommand(command$: Observable<unknown>, fallbackMessage: string,
                     status?: 'RUNNING' | 'PAUSED' | 'STOPPED', afterSuccess?: () => void): void {
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
        afterSuccess?.();
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
