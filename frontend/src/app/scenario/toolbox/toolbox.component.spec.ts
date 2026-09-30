import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToolboxComponent } from './toolbox.component';

describe('ToolboxComponent', () => {
  let fixture: ComponentFixture<ToolboxComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ToolboxComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ToolboxComponent);
    fixture.detectChanges();
  });

  it('groups road mutations and environment controls', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[data-testid="add-speed-50"]')).toBeTruthy();
    expect(compiled.querySelector('[data-testid="add-traffic-light"]')).toBeTruthy();
    expect(compiled.querySelector('[data-testid="add-vehicle"]')).toBeTruthy();
    expect(compiled.querySelector('[data-testid="add-pothole"]')).toBeTruthy();
  });

  it('emits an environment update when rain is toggled off', () => {
    const component = fixture.componentInstance;
    spyOn(component.updateEnvironment, 'emit');

    const rain = fixture.nativeElement.querySelector('[data-testid="toggle-rain"]') as HTMLButtonElement;
    rain.click();

    expect(component.updateEnvironment.emit).toHaveBeenCalledWith({ rain: false });
  });

  it('converts the visibility slider into a visibility factor', () => {
    const component = fixture.componentInstance;
    spyOn(component.updateEnvironment, 'emit');

    const slider = fixture.nativeElement.querySelector('[data-testid="visibility-slider"]') as HTMLInputElement;
    slider.value = '55';
    slider.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(component.updateEnvironment.emit).toHaveBeenCalledWith({ visibilityFactor: 0.55 });
  });

  it('opens the configuration drawer instead of adding an object immediately', async () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[data-testid="config-panel"]')).toBeNull();

    (compiled.querySelector('[data-testid="add-speed-50"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(compiled.querySelector('[data-testid="config-panel"]')).toBeTruthy();
    expect((compiled.querySelector('[data-testid="config-title"]') as HTMLElement).textContent).toContain('speed limit');
    expect((compiled.querySelector('[data-testid="limit-input"]') as HTMLInputElement).value).toBe('50');
  });

  it('adds the configured object only after the drawer is confirmed', async () => {
    const component = fixture.componentInstance;
    const emitted: unknown[] = [];
    component.createRoadElement.subscribe((payload) => emitted.push(payload));
    const compiled = fixture.nativeElement as HTMLElement;

    (compiled.querySelector('[data-testid="add-speed-50"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(emitted.length).toBe(0);

    (compiled.querySelector('[data-testid="drawer-distance-input"]') as HTMLInputElement).value = '150';
    (compiled.querySelector('[data-testid="drawer-distance-input"]') as HTMLInputElement)
      .dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();

    (compiled.querySelector('[data-testid="apply-config"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(emitted.length).toBe(1);
    expect(emitted[0]).toEqual(jasmine.objectContaining({
      type: 'SPEED_LIMIT',
      speedLimitKmh: 50,
      distanceAheadMeters: 150
    }));
    expect(compiled.querySelector('[data-testid="config-panel"]')).toBeNull();
  });

  it('blocks confirmation when the distance is out of range', async () => {
    const component = fixture.componentInstance;
    const emitted: unknown[] = [];
    component.createRoadElement.subscribe((payload) => emitted.push(payload));
    const compiled = fixture.nativeElement as HTMLElement;

    (compiled.querySelector('[data-testid="add-stop-sign"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    (compiled.querySelector('[data-testid="drawer-distance-input"]') as HTMLInputElement).value = '0';
    (compiled.querySelector('[data-testid="drawer-distance-input"]') as HTMLInputElement)
      .dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();

    (compiled.querySelector('[data-testid="apply-config"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(emitted.length).toBe(0);
    expect(compiled.textContent).toContain('between 1 and 1000');
  });
});