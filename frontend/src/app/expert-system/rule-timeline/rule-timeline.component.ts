import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiSimulationSnapshot } from '../../core/models/api.models';

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

  protected readonly eventTypes: TimelineFilter[] = ['IMPORTANT', 'ALL', 'PERCEPTION', 'RULE', 'DECISION', 'MANEUVER', 'ACTUATOR', 'FACT'];
  protected readonly selectedType = signal<TimelineFilter>('IMPORTANT');
  protected readonly frozen = signal(false);

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
    for (const entry of this.snapshot()?.recentTimeline ?? []) {
      const tag = this.tagFor(entry.type);
      tally[tag] += 1;
      if (this.isImportant(entry.type)) {
        tally['IMPORTANT'] += 1;
      }
    }
    return tally;
  });

  protected readonly filteredEntries = computed(() => {
    const entries = this.snapshot()?.recentTimeline ?? [];
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
}
