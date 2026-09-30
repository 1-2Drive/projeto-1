import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { FormsModule } from '@angular/forms';

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
  readonly filter = signal('');

  protected setFilter(value: string): void {
    this.filter.set(value.toLowerCase());
  }

  protected visibleRows() {
    const snap = this.snapshot();
    if (!snap) {
      return [];
    }
    const filter = this.filter();
    if (!filter) {
      return snap.workingMemory;
    }
    return snap.workingMemory.filter((row) =>
      row.type.toLowerCase().includes(filter) ||
      row.value.toLowerCase().includes(filter) ||
      row.stream.toLowerCase().includes(filter) ||
      this.statusFor(row.timestamp).toLowerCase().includes(filter)
    );
  }

  protected statusFor(timestamp: number | null): string {
    if (timestamp === null) {
      return 'STATIC';
    }
    const simTime = this.snapshot()?.simulationTimeMillis ?? 0;
    if (simTime - timestamp < 3000) {
      return 'RECENT';
    }
    return 'STABLE';
  }

}
