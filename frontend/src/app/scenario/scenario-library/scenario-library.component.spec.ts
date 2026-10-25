import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ScenarioLibraryComponent } from './scenario-library.component';

describe('ScenarioLibraryComponent', () => {
  let fixture: ComponentFixture<ScenarioLibraryComponent>;
  let slider: HTMLInputElement;
  let value: () => string;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScenarioLibraryComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ScenarioLibraryComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    slider = compiled.querySelector('[data-testid="random-frequency-slider"]') as HTMLInputElement;
    value = () => compiled.querySelector('[data-testid="random-frequency-value"]')!.textContent!.trim();
  });

  const drag = (to: number, event: 'input' | 'change') => {
    slider.value = String(to);
    slider.dispatchEvent(new Event(event));
    fixture.detectChanges();
  };

  it('keeps the frequency slider disabled until the backend reports a frequency', () => {
    expect(slider.disabled).toBeTrue();

    fixture.componentRef.setInput('randomObjectFrequency', 25);
    fixture.detectChanges();

    expect(slider.disabled).toBeFalse();
    expect(slider.value).toBe('25');
    expect(value()).toBe('25');
  });

  it('follows the thumb while dragging and sends only the released frequency', () => {
    fixture.componentRef.setInput('randomObjectFrequency', 25);
    fixture.detectChanges();
    const emit = spyOn(fixture.componentInstance.randomObjectFrequencyChange, 'emit');

    drag(40, 'input');
    drag(70, 'input');
    expect(value()).toBe('70');
    expect(emit).not.toHaveBeenCalled();

    drag(70, 'change');
    expect(emit).toHaveBeenCalledOnceWith(70);

    fixture.componentRef.setInput('randomObjectFrequency', 70);
    fixture.detectChanges();
    expect(value()).toBe('70');
  });

  it('shows Off at frequency 0', () => {
    fixture.componentRef.setInput('randomObjectFrequency', 0);
    fixture.detectChanges();

    expect(value()).toBe('Off');
  });
});
