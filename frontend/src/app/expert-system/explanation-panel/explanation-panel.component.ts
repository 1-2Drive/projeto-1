import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { BackendApiService } from '../../core/api/backend-api.service';
import { SimulationStateService } from '../../core/state/simulation-state.service';
import { ApiDecisionSnapshot, ApiSessionDetail, ApiSessionSummary, ApiSimulationSnapshot, ApiWhyAnswer } from '../../core/models/api.models';

/**
 * Presents the audit chain the engine publishes for the current decision:
 * what was decided, why, which rule fired, and the raw evidence it used.
 */
@Component({
  selector: 'app-explanation-panel',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './explanation-panel.component.html',
  styleUrl: './explanation-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExplanationPanelComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
  private readonly api = inject(BackendApiService);
  private readonly state = inject(SimulationStateService);

  protected readonly decision = computed(() => this.snapshot()?.currentDecision ?? null);
  protected readonly evidence = computed(() => this.decision()?.evidence ?? []);
  protected readonly hasEvidence = computed(() => this.evidence().length > 0);
  protected readonly sessions = signal<ApiSessionSummary[]>([]);
  protected readonly selectedSession = signal<ApiSessionDetail | null>(null);
  protected readonly selectedTimestamp = signal<number | null>(null);
  protected readonly question = signal('Why did you move left?');
  protected readonly answer = signal<ApiWhyAnswer | null>(null);
  protected readonly loadingHistory = signal(false);
  protected readonly requestedTimestamp = this.state.selectedHistoricalTimestamp;
  private readonly historyVersion = this.state.historyVersion;
  private readonly requestedSessionId = this.state.selectedHistoricalSessionId;
  private readonly historyEffect = effect(() => {
    this.historyVersion();
    const requestedSession = this.requestedSessionId();
    this.api.getSessions().subscribe({ next: (sessions) => this.sessions.set(sessions) });
    if (requestedSession) this.openSession(requestedSession);
  });

  protected readonly decisions = computed(() => this.selectedSession()?.decisionSnapshots ?? []);
  protected readonly selectedDecision = computed(() => {
    const timestamp = this.selectedTimestamp();
    const decisions = this.decisions();
    if (!decisions.length) return null;
    const selected = timestamp ?? decisions[decisions.length - 1].simulationTimeMillis;
    return decisions.reduce((best, item) => Math.abs(item.simulationTimeMillis - selected)
      < Math.abs(best.simulationTimeMillis - selected) ? item : best, decisions[0]);
  });

  protected openHistoricalDecision(timestamp: number): void {
    this.state.openHistoricalDecision(timestamp);
    const open = this.selectedSession();
    if (open) this.selectedTimestamp.set(timestamp);
    else if (this.sessions().length > 0) this.openSession(this.sessions()[0].id);
  }

  protected refreshSessions(): void {
    this.historyVersion();
    this.loadingHistory.set(true);
    this.api.getSessions().subscribe({
      next: (sessions) => this.sessions.set(sessions),
      error: () => this.sessions.set([]),
      complete: () => this.loadingHistory.set(false)
    });
  }

  protected openSession(id: string): void {
    if (!id) {
      this.selectedSession.set(null);
      this.selectedTimestamp.set(null);
      return;
    }
    this.api.getSession(id).subscribe({
      next: (session) => {
        this.selectedSession.set(session);
        const requested = this.requestedTimestamp();
        const matching = session.decisionSnapshots.filter((item) =>
          item.simulationTimeMillis <= (requested ?? Number.MAX_SAFE_INTEGER));
        this.selectedTimestamp.set(matching.at(-1)?.simulationTimeMillis
          ?? session.decisionSnapshots.at(-1)?.simulationTimeMillis ?? null);
        this.answer.set(null);
      }
    });
  }

  protected chooseDecision(timestamp: number): void {
    this.selectedTimestamp.set(timestamp);
    this.answer.set(null);
  }

  protected updateQuestion(value: string): void { this.question.set(value); }

  protected askWhy(): void {
    const session = this.selectedSession();
    if (!session || !this.question().trim()) return;
    this.api.askWhy(session.id, this.question(), this.selectedTimestamp() ?? undefined)
      .subscribe({ next: (answer) => this.answer.set(answer) });
  }

  protected formatTime(millis: number): string {
    const mm = Math.floor(millis / 60_000).toString().padStart(2, '0');
    const ss = Math.floor((millis % 60_000) / 1_000).toString().padStart(2, '0');
    const mmm = Math.floor(millis % 1_000).toString().padStart(3, '0');
    return `${mm}:${ss}.${mmm}`;
  }

  protected formatSessionTime(millis: number): string {
    const start = this.selectedSession()?.startedAtSimulationMillis ?? 0;
    return this.formatTime(Math.max(0, millis - start));
  }

  protected sessionLabel(session: ApiSessionSummary): string {
    return `${new Date(session.startedAt).toLocaleString()} · ${session.scenarioId} · ${session.backend} · ${session.decisionCount} decisions`;
  }
}
