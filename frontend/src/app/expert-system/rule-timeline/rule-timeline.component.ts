import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

type TimelineTag = 'SENSOR' | 'FACT' | 'RULE' | 'DECISION' | 'ACTUATOR';
type TimelineFilter = 'ALL' | TimelineTag;

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

  protected readonly eventTypes: TimelineFilter[] = ['ALL', 'SENSOR', 'FACT', 'RULE', 'DECISION', 'ACTUATOR'];
  protected readonly selectedType = signal<TimelineFilter>('ALL');

  protected setType(type: TimelineFilter): void {
    this.selectedType.set(type);
  }

  protected readonly counts = computed(() => {
    const tally: Record<string, number> = { SENSOR: 0, FACT: 0, RULE: 0, DECISION: 0, ACTUATOR: 0 };
    for (const entry of this.snapshot()?.recentTimeline ?? []) {
      tally[this.tagFor(entry.type)] += 1;
    }
    return tally;
  });

  protected readonly filteredEntries = computed(() => {
    const entries = this.snapshot()?.recentTimeline ?? [];
    const selected = this.selectedType();
    if (selected === 'ALL') {
      return entries;
    }
    return entries.filter((entry) => this.tagFor(entry.type) === selected);
  });

  protected tagFor(type: string): TimelineTag {
    const normalized = type.toUpperCase();
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
}