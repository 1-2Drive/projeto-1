import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal, untracked } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiSimulationSnapshot, ApiTimelineEntry } from '../../core/models/api.models';
import { output } from '@angular/core';

/** Snapshots arrive every engine tick; the panel only repaints at this cadence so it stays readable. */
const REFRESH_INTERVAL_MS = 1000;

type TimelineTag = 'PERCEPTION' | 'RULE' | 'DECISION' | 'ACTUATOR' | 'FACT' | 'SENSOR' | 'MANEUVER';
type TimelineFilter = 'IMPORTANT' | 'ALL' | TimelineTag;

/**
 * Read-only evidence of the reasoning flow. The order shown here is the order the
 * engine executed: perception, working-memory facts, rule firing, decision, actuation.
 */
@Component({
  selector: 'app-rule-timeline',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './rule-timeline.component.html',
  styleUrl: './rule-timeline.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RuleTimelineComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
  readonly inspectDecision = output<number>();
  readonly historySession = input<string | null>(null);

  protected readonly eventTypes: TimelineFilter[] = ['IMPORTANT', 'ALL', 'PERCEPTION', 'RULE', 'DECISION', 'MANEUVER', 'ACTUATOR', 'FACT'];
  protected readonly selectedType = signal<TimelineFilter>('IMPORTANT');
  protected readonly frozen = signal(false);

  private readonly flushTick = signal(0);
  private readonly hasEntries = computed(() => (this.snapshot()?.recentTimeline.length ?? 0) > 0);

  /**
   * Timeline flushed to the view: every entry is kept, only the repaint is batched once per interval.
   * The first entries (and a reset back to empty) still show immediately via hasEntries.
   */
  private readonly displayedTimeline = computed<ApiTimelineEntry[]>(() => {
    this.flushTick();
    this.hasEntries();
    return untracked(() => this.snapshot()?.recentTimeline ?? []);
  });

  constructor() {
    const timer = setInterval(() => this.flushTick.update((tick) => tick + 1), REFRESH_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected setType(type: TimelineFilter): void {
    this.selectedType.set(type);
  }

  protected toggleFrozen(): void {
    this.frozen.update((value) => !value);
  }

  protected readonly counts = computed(() => {
    const tally: Record<string, number> = {
      IMPORTANT: 0,
      PERCEPTION: 0,
      FACT: 0,
      SENSOR: 0,
      RULE: 0,
      DECISION: 0,
      MANEUVER: 0,
      ACTUATOR: 0
    };
    for (const entry of this.displayedTimeline()) {
      const tag = this.tagFor(entry.type);
      tally[tag] += 1;
      if (this.isImportant(entry.type)) {
        tally['IMPORTANT'] += 1;
      }
    }
    return tally;
  });

  protected readonly filteredEntries = computed(() => {
    const entries = this.displayedTimeline();
    const selected = this.selectedType();
    if (selected === 'ALL') {
      return entries;
    }
    if (selected === 'IMPORTANT') {
      return entries.filter((entry) => this.isImportant(entry.type));
    }
    return entries.filter((entry) => this.tagFor(entry.type) === selected);
  });

  protected tagFor(type: string): TimelineTag {
    const normalized = type.toUpperCase();
    if (normalized === 'PERCEPTION_CHANGE') {
      return 'PERCEPTION';
    }
    if (normalized === 'MANEUVER') {
      return 'MANEUVER';
    }
    if (normalized === 'SENSOR') {
      return 'SENSOR';
    }
    if (normalized.startsWith('FACT_')) {
      return 'FACT';
    }
    if (normalized.startsWith('RULE_')) {
      return 'RULE';
    }
    if (normalized === 'DECISION') {
      return 'DECISION';
    }
    if (normalized === 'ACTUATOR') {
      return 'ACTUATOR';
    }
    return 'FACT';
  }

  protected isImportant(type: string): boolean {
    return ['PERCEPTION_CHANGE', 'RULE_FIRED', 'DECISION', 'ACTUATOR', 'MANEUVER'].includes(type.toUpperCase());
  }

  protected selectEntry(entry: ApiTimelineEntry): void {
    if (entry.type === 'DECISION' || entry.type === 'MANEUVER' || entry.type === 'PERCEPTION_CHANGE') {
      this.inspectDecision.emit(entry.timestamp);
    }
  }
}
