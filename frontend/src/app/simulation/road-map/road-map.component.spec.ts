import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MapCellRequest } from '../../core/models/api.models';
import { RoadMapComponent } from './road-map.component';

describe('RoadMapComponent', () => {
  let fixture: ComponentFixture<RoadMapComponent>;
  let painted: MapCellRequest[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoadMapComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(RoadMapComponent);
    painted = [];
    fixture.componentInstance.paint.subscribe((request) => painted.push(request));
    fixture.componentRef.setInput('snapshot', {
      egoVehicle: { positionMeters: 27.5, lateralPositionMeters: 0, lane: 'CENTER' },
      roadMap: {
        rows: 4,
        lanes: ['LEFT', 'CENTER', 'RIGHT'],
        blockLengthMeters: 5,
        egoRow: 1,
        lap: 1,
        cells: [[0, 0, 0], [0, 1, 0], [2, 0, 9], [0, 0, 0]],
        legend: [
          { code: 1, type: 'STATIC_OBSTACLE', label: 'Obstacle', detail: 'HIGH' },
          { code: 2, type: 'POTHOLE', label: 'Pothole', detail: 'HIGH' },
          { code: 9, type: 'SPEED_LIMIT', label: 'Limit 50 km/h', detail: '50' }
        ]
      }
    });
    fixture.detectChanges();
  });

  function cell(row: number, lane: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-testid="map-cell-${row}-${lane}"]`);
  }

  it('draws every matrix cell with its code and tone', () => {
    expect(fixture.nativeElement.querySelectorAll('.cell').length).toBe(12);
    expect(cell(1, 'CENTER').dataset['code']).toBe('1');
    expect(cell(1, 'CENTER').classList).toContain('cell--obstacle');
    expect(cell(2, 'LEFT').classList).toContain('cell--pothole');
    expect(cell(2, 'RIGHT').classList).toContain('cell--limit');
    expect(fixture.nativeElement.textContent).toContain('4 × 3');
    expect(fixture.nativeElement.textContent).toContain('lap 2');
  });

  it('places the car on its block of the current lap', () => {
    const ego: HTMLElement = fixture.nativeElement.querySelector('[data-testid="road-map-ego"]');
    // 27.5 m on a 20 m loop is 7.5 m into the lap: 37.5 % of the strip, centre lane.
    expect(parseFloat(ego.style.left)).toBeCloseTo(37.5, 5);
    expect(parseFloat(ego.style.top)).toBeCloseTo(50, 5);
  });

  it('paints with the selected brush and erases when the stroke starts on that value', () => {
    fixture.nativeElement.querySelector('[data-testid="map-brush-2"]').click();
    fixture.detectChanges();

    cell(0, 'RIGHT').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    cell(1, 'RIGHT').dispatchEvent(new PointerEvent('pointerenter'));
    cell(1, 'RIGHT').dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    cell(2, 'LEFT').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));

    expect(painted).toEqual([
      { row: 0, lane: 'RIGHT', code: 2 },
      { row: 1, lane: 'RIGHT', code: 2 },
      { row: 2, lane: 'LEFT', code: 0 }
    ]);
  });
});
