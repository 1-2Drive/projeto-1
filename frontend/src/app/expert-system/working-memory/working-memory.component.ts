import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiSimulationSnapshot, ApiWorkingMemoryRow } from '../../core/models/api.models';

type FactStatus = 'RECENT' | 'STABLE' | 'STATIC';

/**
 * Inspector for the Drools session working memory. It only reads the snapshot the
 * backend publishes; it never inserts or retires facts itself.
 */
@Component({
  selector: 'app-working-memory',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './working-memory.component.html',
  styleUrl: './working-memory.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkingMemoryComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
  protected readonly filter = signal('');

  protected setFilter(value: string): void {
    this.filter.set(value.toLowerCase());
  }

  protected readonly totalFacts = computed(() => this.snapshot()?.workingMemory.length ?? 0);

  protected readonly visibleRows = computed<ApiWorkingMemoryRow[]>(() => {
    const rows = this.snapshot()?.workingMemory ?? [];
    const filter = this.filter();
    if (!filter) {
      return rows;
    }
    return rows.filter((row) =>
      row.type.toLowerCase().includes(filter) ||
      row.value.toLowerCase().includes(filter) ||
      row.source.toLowerCase().includes(filter) ||
      row.stream.toLowerCase().includes(filter) ||
      this.statusFor(row.timestamp).toLowerCase().includes(filter)
    );
  });

  protected statusFor(timestamp: number | null): FactStatus {
    if (timestamp === null) {
      return 'STATIC';
    }
    const simTime = this.snapshot()?.simulationTimeMillis ?? 0;
    return simTime - timestamp < 3000 ? 'RECENT' : 'STABLE';
  }
}