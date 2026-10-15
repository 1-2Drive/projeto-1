import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ApiMapLegendEntry, ApiSimulationSnapshot, LaneName, MapCellRequest } from '../../core/models/api.models';

/** Colour family of a legend entry; light colours and limits keep their own tone. */
export type MapCellTone =
  | 'free'
  | 'obstacle'
  | 'pothole'
  | 'barrier'
  | 'stop'
  | 'light-red'
  | 'light-yellow'
  | 'light-green'
  | 'limit'
  | 'crosswalk';

export interface MapCellView {
  row: number;
  lane: LaneName;
  code: number;
}

const FREE_ENTRY: ApiMapLegendEntry = { code: 0, type: 'FREE', label: 'Free', detail: '' };

/** Lateral metres of the outer lane centres, mirroring the backend Lane contract. */
const OUTER_LANE_METRES = 3.5;

/**
 * The road map matrix as an editable strip. Columns are blocks along the road
 * (row 0 on the left, the road loops back to it after the last one) and rows are
 * lanes, LEFT on top as on the road view. Clicking or dragging paints cells with the
 * selected legend code; starting a stroke on a cell that already has that code
 * erases instead.
 */
@Component({
  selector: 'app-road-map',
  standalone: true,
  templateUrl: './road-map.component.html',
  styleUrl: './road-map.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoadMapComponent {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
  readonly paint = output<MapCellRequest>();

  protected readonly brush = signal(1);
  private strokeCode: number | null = null;

  protected readonly map = computed(() => this.snapshot()?.roadMap ?? null);

  protected readonly legend = computed(() => [FREE_ENTRY, ...(this.map()?.legend ?? [])]);

  private readonly legendByCode = computed(() => new Map(this.legend().map((entry) => [entry.code, entry])));

  /** Matrix contents only change on edits, so the grid is rebuilt on those, not on every snapshot. */
  private readonly cells = computed(() => this.map()?.cells ?? [], { equal: sameCells });

  protected readonly laneRows = computed(() => {
    const lanes = this.map()?.lanes ?? [];
    const cells = this.cells();
    return lanes.map((lane, laneIndex) => ({
      lane,
      cells: cells.map((row, rowIndex): MapCellView => ({ row: rowIndex, lane, code: row[laneIndex] ?? 0 }))
    }));
  });

  protected readonly rowTicks = computed(() => {
    const rows = this.map()?.rows ?? 0;
    const step = rows > 50 ? 10 : 5;
    const ticks: number[] = [];
    for (let row = 0; row < rows; row += step) {
      ticks.push(row);
    }
    return ticks;
  });

  protected readonly laneColumns = computed(() => [
    { lane: 'LEFT' as LaneName, index: this.map()?.lanes.indexOf('LEFT') ?? -1 },
    { lane: 'CENTER' as LaneName, index: this.map()?.lanes.indexOf('CENTER') ?? -1 },
    { lane: 'RIGHT' as LaneName, index: this.map()?.lanes.indexOf('RIGHT') ?? -1 }
  ]);

  /** Car marker: horizontal from the distance driven on this lap, vertical from the lateral position. */
  protected readonly ego = computed(() => {
    const snapshot = this.snapshot();
    const map = this.map();
    if (!snapshot || !map || map.rows === 0) {
      return null;
    }
    const length = map.rows * map.blockLengthMeters;
    const onLap = ((snapshot.egoVehicle.positionMeters % length) + length) % length;
    const lateral = Math.max(-OUTER_LANE_METRES, Math.min(OUTER_LANE_METRES, snapshot.egoVehicle.lateralPositionMeters));
    return {
      xPercent: (onLap / length) * 100,
      yPercent: ((lateral + OUTER_LANE_METRES * 1.5) / (OUTER_LANE_METRES * 3)) * 100
    };
  });

  protected selectBrush(code: number): void {
    this.brush.set(code);
  }

  protected startStroke(cell: MapCellView, event: PointerEvent): void {
    event.preventDefault();
    this.strokeCode = cell.code === this.brush() ? 0 : this.brush();
    this.apply(cell);
  }

  protected continueStroke(cell: MapCellView): void {
    if (this.strokeCode !== null) {
      this.apply(cell);
    }
  }

  protected endStroke(): void {
    this.strokeCode = null;
  }

  protected placeAhead(lane: LaneName): void {
    const map = this.map();
    if (!map || map.rows === 0) return;
    const row = (map.egoRow + Math.max(2, Math.ceil(50 / map.blockLengthMeters))) % map.rows;
    this.paint.emit({ row, lane, code: this.brush() || 1 });
  }

  protected entryFor(code: number): ApiMapLegendEntry | undefined {
    return this.legendByCode().get(code);
  }

  protected toneFor(code: number): MapCellTone {
    const entry = this.entryFor(code);
    switch (entry?.type) {
      case 'STATIC_OBSTACLE':
        return 'obstacle';
      case 'POTHOLE':
        return 'pothole';
      case 'BARRIER':
        return entry.detail === 'OPEN' ? 'free' : 'barrier';
      case 'STOP_SIGN':
        return 'stop';
      case 'TRAFFIC_LIGHT':
        return entry.detail === 'GREEN' ? 'light-green' : entry.detail === 'YELLOW' ? 'light-yellow' : 'light-red';
      case 'SPEED_LIMIT':
        return 'limit';
      case 'CROSSWALK':
        return 'crosswalk';
      default:
        return 'free';
    }
  }

  protected cellTitle(cell: MapCellView): string {
    const entry = this.entryFor(cell.code);
    return `Row ${cell.row} · ${cell.lane} · ${cell.code} = ${entry?.label ?? 'Unknown'}`;
  }

  private apply(cell: MapCellView): void {
    const code = this.strokeCode ?? this.brush();
    if (cell.code !== code) {
      this.paint.emit({ row: cell.row, lane: cell.lane, code });
    }
  }
}

function sameCells(a: number[][], b: number[][]): boolean {
  if (a === b) {
    return true;
  }
  if (a.length !== b.length) {
    return false;
  }
  for (let row = 0; row < a.length; row++) {
    const left = a[row];
    const right = b[row];
    if (left.length !== right.length) {
      return false;
    }
    for (let lane = 0; lane < left.length; lane++) {
      if (left[lane] !== right[lane]) {
        return false;
      }
    }
  }
  return true;
}
