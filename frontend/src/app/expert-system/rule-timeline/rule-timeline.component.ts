import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { DecimalPipe } from '@angular/common';

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

  protected readonly eventTypes = ['ALL', 'SENSOR', 'FACT', 'RULE', 'DECISION', 'ACTUATOR'];
  protected selectedType: string = 'ALL';

  protected setType(type: string): void {
    this.selectedType = type;
  }

  protected filteredEntries() {
    const entries = this.snapshot()?.recentTimeline ?? [];
    if (this.selectedType === 'ALL') {
      return entries;
    }
    return entries.filter((entry) => this.tagFor(entry.type, entry.message) === this.selectedType);
  }

  protected tagFor(type: string, message: string): string {
    const normalizedType = type.toUpperCase();
    if (normalizedType === 'SENSOR') {
      return 'SENSOR';
    }
    if (normalizedType.startsWith('FACT_')) {
      return 'FACT';
    }
    if (normalizedType.startsWith('RULE_')) {
      return 'RULE';
    }
    if (normalizedType === 'DECISION') {
      return 'DECISION';
    }
    if (normalizedType === 'ACTUATOR') {
      return 'ACTUATOR';
    }
    return 'FACT';
  }
}
