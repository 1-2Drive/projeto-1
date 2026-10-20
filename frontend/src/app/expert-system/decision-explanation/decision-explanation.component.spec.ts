import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApiWhyAnswer, WhyMode } from '../../core/models/api.models';
import { DecisionExplanationComponent } from './decision-explanation.component';
import { POTHOLE_ANSWER } from './decision-explanation.fixture';

describe('DecisionExplanationComponent', () => {
  let fixture: ComponentFixture<DecisionExplanationComponent>;

  function render(mode: WhyMode, answer: ApiWhyAnswer = POTHOLE_ANSWER): HTMLElement {
    fixture.componentRef.setInput('answer', answer);
    fixture.componentRef.setInput('mode', mode);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DecisionExplanationComponent] }).compileComponents();
    fixture = TestBed.createComponent(DecisionExplanationComponent);
  });

  it('WHY explains the speed and the lane channel from their deciding rules', () => {
    const element = render('WHY');
    const channels = element.querySelectorAll('[data-testid="why-channel"]');
    expect(channels.length).toBe(2);
    const speed = element.querySelector('[data-channel="LONGITUDINAL"]')!.textContent!;
    expect(speed).toContain('Build back up to the scenario cruise speed when no sign governs the road');
    expect(speed).toContain('cruise=50.0 km/h');
    const lane = element.querySelector('[data-channel="LATERAL"]')!.textContent!;
    expect(lane).toContain('Avoid pothole in right lane by changing left');
    expect(lane).toContain('Lane safety (left): safe to change left');
    expect(lane).toContain('lost to Avoid pothole in right lane by changing left');
    expect(element.textContent).not.toContain('TECHNICAL EVIDENCE');
  });

  it('WHY shows an untouched default channel as a single line', () => {
    const answer: ApiWhyAnswer = {
      ...POTHOLE_ANSWER,
      why: [POTHOLE_ANSWER.why[0], { ...POTHOLE_ANSWER.why[1], relevant: false, summary: 'The vehicle stays in the RIGHT lane because no lane rule found a reason to change lane.' }]
    };
    const element = render('WHY', answer);
    const lane = element.querySelector('[data-channel="LATERAL"]')!;
    expect(lane.classList).toContain('channel--quiet');
    expect(lane.querySelector('dl')).toBeNull();
  });

  it('WHY NOT offers only the alternatives not taken, grouped by channel, not-applicable ones tagged', () => {
    const element = render('WHY_NOT');
    const select = element.querySelector<HTMLSelectElement>('[data-testid="why-not-select"]')!;
    const groups = Array.from(select.querySelectorAll('optgroup')).map(group => group.label);
    expect(groups).toEqual(['Speed · longitudinal', 'Lane · lateral']);
    const options = Array.from(select.options).map(option => option.value);
    expect(options).toEqual(['LONGITUDINAL:HOLD', 'LONGITUDINAL:REDUCE', 'LATERAL:KEEP_LANE', 'LATERAL:CHANGE_LANE_RIGHT']);
    expect(options).not.toContain('LONGITUDINAL:INCREASE');
    expect(options).not.toContain('LATERAL:CHANGE_LANE_LEFT');
    expect(select.options[3].textContent).toContain('not applicable');
    expect(select.value).withContext('the first applicable alternative is shown first').toBe('LONGITUDINAL:HOLD');
    const answer = element.querySelector('[data-testid="why-not-answer"]')!;
    expect(answer.getAttribute('data-status')).toBe('CONDITION_NOT_MET');
    expect(answer.textContent).toContain('Condition not met');
    const marks = Array.from(answer.querySelectorAll('.conditions__mark')).map(mark => mark.textContent!.trim());
    expect(marks).toEqual(['✗', '✓']);
  });

  it('WHY NOT explains the alternative the user picks', () => {
    const element = render('WHY_NOT');
    const select = element.querySelector<HTMLSelectElement>('[data-testid="why-not-select"]')!;
    select.value = 'LATERAL:CHANGE_LANE_RIGHT';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const answer = element.querySelector('[data-testid="why-not-answer"]')!;
    expect(answer.getAttribute('data-option')).toBe('CHANGE_LANE_RIGHT');
    expect(element.querySelector('[data-testid="why-not-status"]')!.textContent).toContain('Not applicable');
    expect(answer.textContent).toContain('There is no lane to the right of the RIGHT lane.');
  });

  it('WHY NOT says so when nothing was recorded instead of showing an empty list', () => {
    const element = render('WHY_NOT', { ...POTHOLE_ANSWER, whyNot: [] });
    expect(element.querySelector('[data-testid="why-not-select"]')).toBeNull();
    expect(element.textContent).toContain('No alternative was recorded');
  });

  it('HOW shows each channel branch by branch, the taken branch apart from the other', () => {
    const element = render('HOW');
    expect(element.querySelectorAll('[data-testid="how-flow"]').length).toBe(2);
    const decisive = element.querySelector('[data-channel="LONGITUDINAL"] [data-node="R03C"]')!;
    expect(decisive.getAttribute('data-branch')).toBe('YES');
    expect(decisive.classList).toContain('flow__step--decisive');
    const taken = decisive.querySelector('.branch--taken')!;
    expect(taken.textContent).toContain('Yes');
    const no = element.querySelector('[data-channel="LONGITUDINAL"] [data-node="V10"]')!;
    expect(no.getAttribute('data-branch')).toBe('NO');
    expect(no.querySelector('.branch--taken')!.textContent).toContain('No');
    expect(no.textContent).toContain('Front radar: no vehicle ahead');
    const offPath = element.querySelector('[data-channel="LATERAL"] details [data-node="F11"]')!;
    expect(offPath.getAttribute('data-branch')).toBe('NOT_REACHED');
    const safety = element.querySelector('[data-channel="LATERAL"] [data-node="00"]')!;
    expect(safety.querySelectorAll('.condition--fails').length).toBe(1);
    expect(element.querySelector('[data-node="DV"] .flow__branches')).toBeNull();
  });
});
