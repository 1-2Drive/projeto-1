import {
  ChangeDetectionStrategy, Component, ElementRef, Injector, OnInit, afterNextRender, computed, inject, input, output,
  signal, viewChild
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { BackendApiService } from '../../core/api/backend-api.service';
import { ApiSessionDetail, ApiWhyAnswer, WhyMode } from '../../core/models/api.models';
import { SimulationStateService } from '../../core/state/simulation-state.service';
import { DecisionExplanationComponent } from '../decision-explanation/decision-explanation.component';

type DecisionSnapshot = ApiSessionDetail['decisionSnapshots'][number];

/**
 * Opens on STOP for the session just saved: the user picks a recorded decision and a mode, and
 * reads why it was made, why each alternative was not, and how the rules reached it, for the
 * speed and the lane channel. The explanation is loaded once per decision; switching mode
 * only changes what is shown.
 */
@Component({
  selector: 'app-trip-questions-dialog',
  standalone: true,
  imports: [DecimalPipe, DecisionExplanationComponent],
  templateUrl: './trip-questions-dialog.component.html',
  styleUrl: './trip-questions-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TripQuestionsDialogComponent implements OnInit {
  readonly sessionId = input.required<string>();
  readonly close = output<void>();

  private readonly state = inject(SimulationStateService);
  private readonly api = inject(BackendApiService);
  private readonly injector = inject(Injector);
  private readonly decisionSelect = viewChild<ElementRef<HTMLSelectElement>>('decisionSelect');
  /** Only the latest request may set the answer, however the responses arrive. */
  private request = 0;

  protected readonly session = signal<ApiSessionDetail | null>(null);
  protected readonly mode = signal<WhyMode>('WHY');
  protected readonly selectedTimestamp = signal<number | null>(null);
  protected readonly answer = signal<ApiWhyAnswer | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly decisions = computed(() => this.session()?.decisionSnapshots ?? []);

  ngOnInit(): void { this.loadSession(); }

  protected loadSession(): void {
    this.api.getSession(this.sessionId()).subscribe({
      next: (session) => {
        this.session.set(session);
        const latest = session.decisionSnapshots.at(-1);
        if (latest) {
          this.explain(latest.simulationTimeMillis);
          afterNextRender(() => this.decisionSelect()?.nativeElement.focus(), { injector: this.injector });
        } else {
          this.error.set('This trip recorded no decision to explain.');
        }
      },
      error: () => this.error.set('The saved session could not be loaded. It remains available in Session History.')
    });
  }

  protected setMode(mode: WhyMode): void { this.mode.set(mode); }

  protected chooseTimestamp(value: string): void {
    if (value) this.explain(Number(value));
  }

  private explain(timestamp: number): void {
    const request = ++this.request;
    this.selectedTimestamp.set(timestamp);
    this.loading.set(true);
    this.error.set(null);
    this.state.explainSessionDecision(this.sessionId(), timestamp).subscribe({
      next: (answer) => {
        if (request !== this.request) return;
        this.answer.set(answer);
        this.loading.set(false);
      },
      error: () => {
        if (request !== this.request) return;
        this.answer.set(null);
        this.loading.set(false);
        this.error.set('Unable to explain this decision. The trip remains available in Session History.');
      }
    });
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      this.dismiss();
    }
  }

  protected dismiss(): void { this.close.emit(); }

  protected formatTime(millis: number): string {
    const mm = Math.floor(millis / 60_000).toString().padStart(2, '0');
    const ss = Math.floor((millis % 60_000) / 1_000).toString().padStart(2, '0');
    return `${mm}:${ss}.${Math.floor(millis % 1_000).toString().padStart(3, '0')}`;
  }

  protected decisionLabel(item: DecisionSnapshot): string {
    return `${this.formatTime(item.simulationTimeMillis)} — speed ${item.decision.speedAction ?? item.decision.action}`
      + ` · lane ${item.decision.laneAction ?? 'KEEP_LANE'}`;
  }

  protected modeLabel(): string {
    return this.mode() === 'WHY' ? 'WHY' : this.mode() === 'WHY_NOT' ? 'WHY NOT' : 'HOW';
  }
}
