import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ElementPropertiesComponent, MIN_VEHICLE_SPEED_KMH } from './element-properties.component';

describe('ElementPropertiesComponent', () => {
  let fixture: ComponentFixture<ElementPropertiesComponent>;
  let component: ElementPropertiesComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ElementPropertiesComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ElementPropertiesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function apply(): void {
    (fixture.nativeElement.querySelector('[data-testid="apply-config"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function speedInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid="actor-speed-input"]') as HTMLInputElement;
  }

  it('refuses a vehicle slower than 6 km/h', () => {
    spyOn(component.createActor, 'emit');
    component.open('FRONT_VEHICLE');
    component.actorSpeedKmh.set(5);
    fixture.detectChanges();

    expect(speedInput().min).toBe(String(MIN_VEHICLE_SPEED_KMH));
    apply();

    expect(component.createActor.emit).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent)
      .toContain('between 6 and 180 km/h');
  });

  it('adds a vehicle at 6 km/h', () => {
    spyOn(component.createActor, 'emit');
    component.open('FRONT_VEHICLE');
    component.actorSpeedKmh.set(6);
    fixture.detectChanges();
    apply();

    expect(component.createActor.emit).toHaveBeenCalledWith(jasmine.objectContaining({ type: 'FRONT_VEHICLE', speedKmh: 6 }));
  });

  it('still lets a pedestrian stand still', () => {
    spyOn(component.createActor, 'emit');
    component.open('PEDESTRIAN');
    component.actorSpeedKmh.set(0);
    fixture.detectChanges();

    expect(speedInput().min).toBe('0');
    apply();

    expect(component.createActor.emit).toHaveBeenCalledWith(jasmine.objectContaining({ type: 'PEDESTRIAN', speedKmh: 0 }));
  });

  it('opens a vehicle at its default speed after a pedestrian', () => {
    component.open('PEDESTRIAN');
    component.open('FRONT_VEHICLE');

    expect(component.actorSpeedKmh()).toBe(30);
  });
});
