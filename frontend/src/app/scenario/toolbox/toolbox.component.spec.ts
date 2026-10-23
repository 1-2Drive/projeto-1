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

  it('keeps every tool under its own section, each section a card of the same grid', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const groups = Array.from(compiled.querySelectorAll('.toolbox__groups > .group')) as HTMLElement[];
    const tools = (group: HTMLElement) => Array.from(group.querySelectorAll('[data-testid]'))
      .map((el) => el.getAttribute('data-testid'));

    expect(groups.map((g) => g.querySelector('h3')!.textContent!.trim()))
      .toEqual(['Road signs', 'Traffic control', 'Actors', 'Road markings', 'Hazards', 'Environment']);
    expect(tools(groups[0])).toEqual([
      'add-speed-30', 'add-speed-50', 'add-speed-70', 'add-speed-90', 'add-speed-100', 'add-speed-120', 'add-stop-sign'
    ]);
    expect(tools(groups[1])).toEqual(['add-traffic-light', 'add-barrier']);
    expect(tools(groups[2])).toEqual(['add-vehicle', 'add-pedestrian']);
    expect(tools(groups[3])).toEqual(['add-crosswalk']);
    expect(tools(groups[4])).toEqual(['add-pothole', 'add-obstacle']);
    expect(tools(groups[5])).toEqual(['toggle-rain', 'visibility-slider', 'grip-slider']);

    // Every tool is an icon followed by its label, so the label never sits on top of the icon.
    for (const tile of Array.from(compiled.querySelectorAll('.tile'))) {
      expect(tile.children[0].tagName.toLowerCase()).toBe('svg');
      expect(tile.children[1].classList.contains('tile__label')).toBeTrue();
    }
  });

  it('emits an environment update when rain is toggled off', () => {
    const component = fixture.componentInstance;
    fixture.componentRef.setInput('environment', {
      weatherCondition: 'RAIN',
      visibilityFactor: 1,
      gripFactor: 1
    });
    spyOn(component.updateEnvironment, 'emit');

    const rain = fixture.nativeElement.querySelector('[data-testid="toggle-rain"]') as HTMLButtonElement;
    rain.click();

    expect(component.updateEnvironment.emit).toHaveBeenCalledWith({ rain: false });
  });

  it('emits an environment update when rain is toggled on from a clear snapshot', () => {
    const component = fixture.componentInstance;
    fixture.componentRef.setInput('environment', {
      weatherCondition: 'CLEAR',
      visibilityFactor: 1,
      gripFactor: 1
    });
    spyOn(component.updateEnvironment, 'emit');

    const rain = fixture.nativeElement.querySelector('[data-testid="toggle-rain"]') as HTMLButtonElement;
    rain.click();

    expect(component.updateEnvironment.emit).toHaveBeenCalledWith({ rain: true });
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

  it('offers no lane for a speed limit, which holds for the whole road', async () => {
    const component = fixture.componentInstance;
    const emitted: unknown[] = [];
    component.createRoadElement.subscribe((payload) => emitted.push(payload));
    const compiled = fixture.nativeElement as HTMLElement;

    (compiled.querySelector('[data-testid="add-speed-30"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(compiled.querySelector('[data-testid="limit-input"]')).toBeTruthy();
    expect(compiled.querySelector('[data-testid="drawer-distance-input"]')).toBeTruthy();
    expect(compiled.querySelector('[data-testid="lane-select"]')).toBeNull();

    (compiled.querySelector('[data-testid="apply-config"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(emitted).toEqual([jasmine.objectContaining({ type: 'SPEED_LIMIT', speedLimitKmh: 30, lane: 'CENTER' })]);

    (compiled.querySelector('[data-testid="add-stop-sign"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(compiled.querySelector('[data-testid="lane-select"]')).toBeNull();
  });

  it('offers no lane for a traffic light, which governs the whole road', async () => {
    const component = fixture.componentInstance;
    const emitted: unknown[] = [];
    component.createRoadElement.subscribe((payload) => emitted.push(payload));
    const compiled = fixture.nativeElement as HTMLElement;

    (compiled.querySelector('[data-testid="add-traffic-light"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(compiled.querySelector('[data-testid="drawer-distance-input"]')).toBeTruthy();
    expect(compiled.querySelector('[data-testid="light-state-select"]')).toBeTruthy();
    expect(compiled.querySelector('[data-testid="lane-select"]')).toBeNull();

    (compiled.querySelector('[data-testid="apply-config"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(emitted).toEqual([jasmine.objectContaining({
      type: 'TRAFFIC_LIGHT', lane: 'CENTER', trafficLightState: 'RED', distanceAheadMeters: 150
    })]);
  });

  it('offers no lane for a stop sign, which governs the whole road', async () => {
    const component = fixture.componentInstance;
    const emitted: unknown[] = [];
    component.createRoadElement.subscribe((payload) => emitted.push(payload));
    const compiled = fixture.nativeElement as HTMLElement;

    (compiled.querySelector('[data-testid="add-stop-sign"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    const panel = compiled.querySelector('[data-testid="config-panel"]') as HTMLElement;
    expect(panel.textContent).toContain('Add stop sign');
    expect(panel.textContent).toContain('Applies to all lanes.');
    expect(compiled.querySelector('[data-testid="drawer-distance-input"]')).toBeTruthy();
    expect(compiled.querySelector('[data-testid="lane-select"]')).toBeNull();

    (compiled.querySelector('[data-testid="apply-config"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    // One road-wide element: the cell lane is only where the road map stores it.
    expect(emitted).toEqual([jasmine.objectContaining({ type: 'STOP_SIGN', lane: 'CENTER', distanceAheadMeters: 150 })]);
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