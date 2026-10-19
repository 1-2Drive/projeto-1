import { ChangeDetectionStrategy, Component, ElementRef, afterNextRender, inject, input, OnInit, output, signal, viewChild } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { BackendApiService } from '../../core/api/backend-api.service';
import { ApiSessionDetail, ApiWhyAnswer, WhyMode } from '../../core/models/api.models';
import { SimulationStateService } from '../../core/state/simulation-state.service';

@Component({
  selector: 'app-trip-questions-dialog',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './trip-questions-dialog.component.html',
  styleUrl: './trip-questions-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TripQuestionsDialogComponent implements OnInit {
  readonly sessionId = input.required<string>();
  readonly close = output<void>();
  readonly openHistory = output<string>();

  private readonly state = inject(SimulationStateService);
  private readonly api = inject(BackendApiService);
  private readonly questionInput = viewChild<ElementRef<HTMLTextAreaElement>>('questionInput');

  protected readonly session = signal<ApiSessionDetail | null>(null);
  protected readonly mode = signal<WhyMode>('WHY');
  protected readonly question = signal('Why did the vehicle change lane?');
  protected readonly selectedTimestamp = signal<number | null>(null);
  protected readonly answer = signal<ApiWhyAnswer | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showTechnical = signal(false);
  protected readonly resolvedTimestamp = signal<number | undefined>(undefined);

  constructor() {
    afterNextRender(() => this.questionInput()?.nativeElement.focus());
  }

  ngOnInit(): void { this.loadSession(); }

  protected loadSession(): void {
    this.api.getSession(this.sessionId()).subscribe({
      next: (session) => {
        this.session.set(session);
        this.selectedTimestamp.set(null);
      },
      error: () => this.error.set('The saved session could not be loaded. It remains available in Session History.')
    });
  }

  protected setQuestion(value: string): void {
    this.question.set(value);
    this.answer.set(null);
    this.selectedTimestamp.set(null);
  }

  protected setMode(mode: WhyMode): void {
    this.mode.set(mode);
    this.answer.set(null);
  }

  protected toggleTechnical(): void { this.showTechnical.update((value) => !value); }

  protected chooseTimestamp(value: string): void {
    this.selectedTimestamp.set(value ? Number(value) : null);
    this.answer.set(null);
  }

  protected ask(): void {
    const sessionId = this.sessionId();
    const question = this.question().trim();
    if (!question) {
      this.error.set('Enter a question about this trip.');
      this.questionInput()?.nativeElement.focus();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const chosen = this.selectedTimestamp();
    const resolution = this.resolveDecision(question, this.mode(), chosen);
    if (resolution.ambiguous.length > 1 && chosen === null) {
      this.error.set(null);
      this.answer.set({
        sessionId, timestampMillis: resolution.ambiguous[0].simulationTimeMillis,
        question, selected: '', why: [], whyNot: [], observations: [], facts: [], rules: [], actuator: [],
        proposals: [], laneSafety: [], engine: this.session()?.backend ?? 'DROOLS', candidates: resolution.ambiguous,
        how: []
      });
      return;
    }
    const timestamp = chosen ?? resolution.decision?.simulationTimeMillis;
    this.state.askAboutSession(sessionId, question, timestamp, this.mode())
      .subscribe({
        next: (answer) => {
          this.answer.set(answer);
          this.resolvedTimestamp.set(answer.timestampMillis);
          this.selectedTimestamp.set(answer.timestampMillis);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set('Unable to query the saved session. The trip remains available in Session History.');
        }
      });
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      this.dismiss();
    } else if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      event.stopPropagation();
      this.ask();
    }
  }

  protected dismiss(): void { this.close.emit(); }

  protected showHistory(): void {
    this.openHistory.emit(this.sessionId());
  }

  protected formatTime(millis: number): string {
    const mm = Math.floor(millis / 60_000).toString().padStart(2, '0');
    const ss = Math.floor((millis % 60_000) / 1_000).toString().padStart(2, '0');
    return `${mm}:${ss}.${Math.floor(millis % 1_000).toString().padStart(3, '0')}`;
  }

  protected technicalEvidence(answer: ApiWhyAnswer): string[] {
    return [...answer.observations, ...answer.facts, ...answer.rules, ...answer.actuator];
  }

  protected decisionLabel(item: ApiSessionDetail['decisionSnapshots'][number]): string {
    return `${this.formatTime(item.simulationTimeMillis)} — ${item.decision.action}`;
  }

  private latestRelevantDecision(session: ApiSessionDetail): ApiSessionDetail['decisionSnapshots'][number] | undefined {
    return [...session.decisionSnapshots].reverse().find((item) => this.isMeaningful(item)
      && this.isRelevantAction(item.decision.action, this.mode()));
  }

  private resolveDecision(question: string, mode: WhyMode, explicit: number | null): {
    decision?: ApiSessionDetail['decisionSnapshots'][number];
    ambiguous: ApiSessionDetail['decisionSnapshots'];
  } {
    const session = this.session();
    if (!session) return { ambiguous: [] };
    const q = question.toLowerCase();
    const candidates = session.decisionSnapshots.filter((item) => this.isMeaningful(item)
      && this.isRelevantAction(item.decision.action, mode)
      && this.questionMatchesSnapshot(q, item, mode));
    if (explicit !== null) {
      const closest = candidates.reduce<typeof candidates[number] | undefined>((best, item) =>
        !best || Math.abs(item.simulationTimeMillis - explicit) < Math.abs(best.simulationTimeMillis - explicit) ? item : best, undefined);
      return { decision: closest, ambiguous: [] };
    }
    const hasExplicitObject = /pothole|obstacle|vehicle|pedestrian|red|green|sign|stopp/.test(q);
    if (!hasExplicitObject && candidates.length > 1) return { ambiguous: candidates.slice(-6).reverse() };
    return { decision: candidates[0] ?? this.latestRelevantDecision(session), ambiguous: [] };
  }

  private questionMatchesAction(question: string, action: string): boolean {
    const q = question.toLowerCase();
    if (q.includes('lane') || q.includes('change lane') || q.includes('overtake')) return action.includes('LANE') || action.includes('OVERTAKE');
    if (q.includes('brak')) return action.includes('BRAKE') || action.includes('STOP');
    if (q.includes('stop')) return action.includes('STOP') || action.includes('BRAKE');
    if (q.includes('speed') || q.includes('accelerat')) return action.includes('SPEED') || action.includes('ACCELERATE');
    if (q.includes('left')) return action.includes('LEFT');
    if (q.includes('right') || q.includes('center') || q.includes('centre')) return action.includes('RIGHT') || action.includes('CENTER');
    return true;
  }

  private questionMatchesSnapshot(question: string, snapshot: ApiSessionDetail['decisionSnapshots'][number], mode: WhyMode): boolean {
    const action = snapshot.decision.action;
    const facts = snapshot.workingMemory;
    if (mode === 'WHY_NOT' && (question.includes('brak') || question.includes('stop'))) {
      return facts.some((fact) => /PotholeStateFact|ObstacleStateFact|PedestrianStateFact|FrontVehicleState|BarrierStateFact/.test(fact.type)
        && !/id='none'|Infinity/.test(fact.value));
    }
    const speed = Number(question.match(/\b(\d{1,3})\s*km\/?h\b/)?.[1]);
    if (Number.isFinite(speed)) {
      return Math.round(snapshot.vehicle.speedKmh) === speed
        || Math.round(snapshot.decision.recommendedSpeedKmh) === speed;
    }
    if (this.questionMatchesAction(question, action)) return true;
    if (question.includes('lane') || question.includes('overtake')) {
      return action.includes('LANE') || action.includes('OVERTAKE') || facts.some((fact) => fact.type === 'PotholeStateFact'
        && !fact.value.includes("potholeId='none'"));
    }
    return false;
  }

  private isRelevantAction(action: string, mode: WhyMode): boolean {
    if (mode === 'WHY_NOT') return true;
    return !['MAINTAIN_SPEED', 'KEEP_LANE'].includes(action);
  }

  private isMeaningful(item: ApiSessionDetail['decisionSnapshots'][number]): boolean {
    return !item.decision.sourceRule.startsWith('Maintain ongoing lane change');
  }
}
