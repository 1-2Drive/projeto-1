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

  it('emits environment update when low visibility is clicked', () => {
    const component = fixture.componentInstance;
    spyOn(component.updateEnvironment, 'emit');

    const buttons = fixture.nativeElement.querySelectorAll('section.environment button') as NodeListOf<HTMLButtonElement>;
    buttons[2].click();

    expect(component.updateEnvironment.emit).toHaveBeenCalledWith({ visibilityFactor: 0.55 });
  });
});
