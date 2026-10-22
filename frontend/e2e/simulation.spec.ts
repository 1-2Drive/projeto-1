import { expect, test } from '@playwright/test';

const TITLE = /Sistema Inteligente de Apoio . Decis.o de Condu..o/;

/** The evidence column keeps the decision, timeline, memory and explanation in tabs. */
async function evidenceTab(page: import('@playwright/test').Page, name: string): Promise<void> {
  const tab = page.getByRole('tab', { name, exact: true });
  if ((await tab.getAttribute('aria-selected')) !== 'true') {
    await tab.click();
  }
}

/** The decision panel's action line, with the Decision tab open. */
async function decisionAction(page: import('@playwright/test').Page) {
  await evidenceTab(page, 'Decision');
  return page.locator('[data-testid="decision-action"]');
}

/** The inference timeline panel, with its tab open. */
async function timelinePanel(page: import('@playwright/test').Page) {
  await evidenceTab(page, 'Inference Timeline');
  return page.locator('[data-testid="timeline-panel"]');
}

/** The dashboard keeps the scenario toolbox and the road map in tabs below the road. */
async function openBottomTab(page: import('@playwright/test').Page, name: 'Road Map' | 'Scenario Toolbox'): Promise<void> {
  const tab = page.getByRole('tab', { name, exact: true });
  if ((await tab.getAttribute('aria-selected')) !== 'true') {
    await tab.click();
  }
}

/** Clicks a toolbox button (add-obstacle, add-speed-50, ...), opening the toolbox tab first. */
async function clickTool(page: import('@playwright/test').Page, testId: string): Promise<void> {
  await openBottomTab(page, 'Scenario Toolbox');
  await page.locator(`[data-testid="${testId}"]`).click();
}

/** Fills the configuration drawer and confirms it, which is the only way to mutate the world. */
async function addWorldObject(
  page: import('@playwright/test').Page,
  trigger: string,
  options: { distance?: string; lane?: string } = {}
): Promise<void> {
  await clickTool(page, trigger);
  await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
  if (options.distance !== undefined) {
    await page.locator('[data-testid="drawer-distance-input"]').fill(options.distance);
  }
  if (options.lane !== undefined) {
    await page.locator('[data-testid="lane-select"]').selectOption(options.lane);
  }
  await page.locator('[data-testid="apply-config"]').click();
  await expect(page.locator('[data-testid="config-panel"]')).toHaveCount(0);
  await expect(await timelinePanel(page)).toContainText(/PERCEPTION|RULE|FACT|ACTUATOR/i, {
    timeout: 10000
  });
}

async function loadScenario(page: import('@playwright/test').Page, exactName: string): Promise<void> {
  await page.locator('[data-testid="reset-btn"]').click();
  await openBottomTab(page, 'Scenario Toolbox');
  await page.getByRole('button', { name: exactName, exact: true }).click();
  await expect(page.locator('[data-testid="simulation-status"]')).toContainText('STOPPED');
  await expect(page.locator('[data-testid="simulation-clock"]')).toContainText('00:00.000');
}

/** A pattern for mm:ss.mmm, the clock the trip dialog labels a recorded decision with. */
function clockPattern(millis: number): string {
  const mm = Math.floor(millis / 60_000).toString().padStart(2, '0');
  const ss = Math.floor((millis % 60_000) / 1_000).toString().padStart(2, '0');
  return `${mm}:${ss}\\.${Math.floor(millis % 1_000).toString().padStart(3, '0')}`;
}

test.describe('Driving expert simulation UI', () => {
  test('starts on a clean stopped road and only runs after START', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: TITLE })).toBeVisible();
    await expect(page.locator('[data-testid="backend-status"]')).toContainText('CONNECTED');

    await page.locator('[data-testid="reset-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('STOPPED');

    const clock = page.locator('[data-testid="simulation-clock"]');
    await expect(clock).toContainText('00:00.000');
    await expect(page.getByText('Press START to begin')).toBeVisible();
    await expect(page.locator('[data-testid="pause-btn"]')).toBeDisabled();

    // The clock must stay frozen while stopped.
    await page.waitForTimeout(2500);
    await expect(clock).toContainText('00:00.000');

    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');
    await expect(clock).not.toContainText('00:00.000', { timeout: 15000 });
  });

  test('manual speed limit added at 150 m drives the expert system', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');
    await page.evaluate(() => fetch('/api/world/actors', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type: 'FRONT_VEHICLE', lane: 'RIGHT', distanceAheadMeters: 80, speedKmh: 30 }) }));
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await addWorldObject(page, 'add-speed-50', { distance: '150' });

    await expect(page.locator('[data-testid="speed-limit"]')).toContainText('50', { timeout: 40000 });
    // The timeline only keeps what it saw while it was on screen, so read it before switching tabs.
    await expect(await timelinePanel(page)).toContainText(/50 km\/h|speed limit/i, { timeout: 40000 });
    await expect(await decisionAction(page))
      .toContainText(/REDUCE_SPEED|BRAKE|EMERGENCY_BRAKE|MAINTAIN_SPEED/, { timeout: 40000 });
  });

  test('speed limit has no lane selector and is painted on all three lanes', async ({ page }) => {
    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');

    for (const limit of ['30', '90']) {
      await page.locator('[data-testid="reset-btn"]').click();
      await clickTool(page, `add-speed-${limit}`);
      await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
      await expect(page.locator('[data-testid="lane-select"]')).toHaveCount(0);
      await page.locator('[data-testid="drawer-distance-input"]').fill('60');
      await page.locator('[data-testid="apply-config"]').click();

      await expect(page.locator('[data-testid="road-speed-limit"]')).toHaveCount(1);
      const marks = page.locator('.limit-mark');
      await expect(marks).toHaveCount(3);
      await expect(marks).toHaveText([limit, limit, limit]);
      // One speed limit, one road position: the three paint marks sit at the same depth, so on
      // the same screen row, with LEFT / CENTER / RIGHT from left to right.
      const marksAt = await marks.evaluateAll((nodes) => nodes.map((node) => {
        const box = node.getBoundingClientRect();
        const holder = node.parentElement as Element;
        return { y: box.y + box.height / 2, x: box.x + box.width / 2, depth: holder.getAttribute('data-depth'), lane: holder.getAttribute('data-lane') };
      }));
      expect(new Set(marksAt.map((m) => m.depth)).size).toBe(1);
      expect(Math.max(...marksAt.map((m) => m.y)) - Math.min(...marksAt.map((m) => m.y))).toBeLessThan(1);
      const byLane = (lane: string) => marksAt.find((m) => m.lane === lane)!.x;
      expect(byLane('LEFT')).toBeLessThan(byLane('CENTER'));
      expect(byLane('CENTER')).toBeLessThan(byLane('RIGHT'));
    }
  });

  test('traffic light has no lane selector and is drawn on all three lanes', async ({ page }) => {
    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');

    await clickTool(page, 'add-traffic-light');
    await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="lane-select"]')).toHaveCount(0);
    await page.locator('[data-testid="drawer-distance-input"]').fill('100');
    await page.locator('[data-testid="light-state-select"]').selectOption('RED');
    await page.locator('[data-testid="apply-config"]').click();

    await expect(page.locator('[data-testid="road-traffic-light"]')).toHaveCount(1);
    const lights = page.locator('.obj--trafficLight');
    await expect(lights).toHaveCount(3);
    for (let i = 0; i < 3; i++) {
      await expect(lights.nth(i)).toHaveAttribute('data-state', 'RED');
    }
    // One light, one road position: the three heads hang over their lanes at the same depth, so on
    // the same screen row, LEFT / CENTER / RIGHT from left to right.
    const heads = await lights.evaluateAll((nodes) => nodes.map((node) => {
      const box = node.getBoundingClientRect();
      // The head in the ego lane also carries the distance tag above it, so compare the bottoms.
      return { x: box.x + box.width / 2, y: box.y + box.height, lane: node.getAttribute('data-lane'), depth: node.getAttribute('data-depth') };
    }));
    expect(heads.map((h) => h.lane)).toEqual(['LEFT', 'CENTER', 'RIGHT']);
    expect(new Set(heads.map((h) => h.depth)).size).toBe(1);
    expect(Math.max(...heads.map((h) => h.y)) - Math.min(...heads.map((h) => h.y))).toBeLessThan(1);
    expect(heads[0].x).toBeLessThan(heads[1].x);
    expect(heads[1].x).toBeLessThan(heads[2].x);
  });

  test('stop sign has no lane field: one road-wide sign on the verge with a stop line across the road', async ({ page }) => {
    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');

    await clickTool(page, 'add-stop-sign');
    const drawer = page.locator('[data-testid="config-panel"]');
    await expect(drawer).toBeVisible();
    await expect(drawer).toContainText('Add stop sign');
    await expect(drawer).toContainText('Applies to all lanes.');
    await expect(page.locator('[data-testid="drawer-distance-input"]')).toBeVisible();
    await expect(page.locator('[data-testid="lane-select"]')).toHaveCount(0);
    await page.locator('[data-testid="drawer-distance-input"]').fill('60');
    await page.locator('[data-testid="apply-config"]').click();

    const sign = page.locator('[data-testid="road-stop-sign"]');
    await expect(sign).toHaveCount(1);
    await expect(page.locator('.stop-line')).toHaveCount(1);
    // One element, written to one map cell, drawn once on the right verge (outside the 5.25 m asphalt).
    expect(Math.abs(Number(await sign.getAttribute('data-lateral')))).toBeGreaterThan(5.25);
    await openBottomTab(page, 'Road Map');
    await expect(page.locator('[data-testid^="map-cell-"][data-code="4"]')).toHaveCount(1);
  });

  test('one stop sign, placed with no lane, stops the vehicle whether it drives in the left, centre or right lane', async ({ page }) => {
    test.setTimeout(240000);
    await page.goto('/');
    const egoLane = async () => (await (await page.request.get('/api/simulation/state')).json()).egoVehicle;

    for (const target of ['RIGHT', 'CENTER', 'LEFT']) {
      await loadScenario(page, 'S1 Empty safe road');
      await page.locator('[data-testid="start-btn"]').click();
      await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');
      // Drive into the lane under test, one lane change at a time.
      const route = target === 'LEFT' ? [['LEFT', 'CENTER'], ['LEFT', 'LEFT']] : target === 'CENTER' ? [['LEFT', 'CENTER']] : [];
      for (const [direction, expected] of route) {
        await page.request.post('/api/simulation/lane-change/' + direction);
        await expect.poll(async () => {
          const ego = await egoLane();
          return `${ego.lane}/${ego.laneChangeState}`;
        }, { timeout: 25000 }).toBe(`${expected}/IDLE`);
      }
      expect((await egoLane()).lane).toBe(target);
      const startedAt = (await egoLane()).positionMeters;

      await addWorldObject(page, 'add-stop-sign', { distance: '150' });
      await expect(page.locator('[data-testid="road-stop-sign"]')).toHaveCount(1);

      await expect.poll(async () => (await egoLane()).speedKmh, { timeout: 60000, message: `from the ${target} lane` }).toBeLessThan(0.6);
      const stopped = await egoLane();
      expect(stopped.positionMeters - startedAt, `stopped before the sign, from ${target}`).toBeGreaterThan(100);
      expect(stopped.positionMeters - startedAt).toBeLessThan(150);
      await expect(await decisionAction(page)).toContainText(/STOP|WAIT/);
    }
  });

  test('pedestrian interaction produces a safety decision', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await addWorldObject(page, 'add-pedestrian', { distance: '8' });

    await expect(await decisionAction(page))
      .toContainText(/STOP|BRAKE|EMERGENCY_BRAKE/, { timeout: 40000 });

    await page.locator('button:has-text("Inference Timeline")').click();
    await expect(await timelinePanel(page))
      .toContainText(/pedestrian/i, { timeout: 20000 });
  });

  test('pothole avoidance visibly moves the ego vehicle from RIGHT to CENTER', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');
    await page.waitForTimeout(250);
    await clickTool(page, 'add-pothole');
    await page.locator('[data-testid="lane-select"]').selectOption('RIGHT');
    await page.locator('[data-testid="drawer-distance-input"]').fill('100');
    await page.locator('[data-testid="apply-config"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await expect(await decisionAction(page))
      .toContainText('CHANGE_LANE_LEFT', { timeout: 45000 });
    await expect(page.locator('[data-testid="road-ego"]'))
      .toHaveAttribute('data-maneuver', /REQUESTED|CHANGING/, { timeout: 15000 });

    const lateralAtStart = Number(await page.locator('[data-testid="road-ego"]').getAttribute('data-lateral'));
    await expect.poll(async () => {
      const value = await page.locator('[data-testid="road-ego"]').getAttribute('data-lateral');
      return Number(value);
    }, { timeout: 15000 }).toBeLessThan(lateralAtStart - 0.1);
    // The live narration describes the move only while it runs; data-lane switches when it completes.
    await page.getByRole('tab', { name: 'Situation', exact: true }).click();
    await expect(page.locator('[data-testid="situation-panel"]'))
      .toContainText(/pothole|moving to the CENTER/i, { timeout: 15000 });

    await expect(page.locator('[data-testid="road-ego"]')).toHaveAttribute('data-lane', 'CENTER', { timeout: 20000 });
    await expect(await timelinePanel(page))
      .toContainText(/MANEUVER STARTED|MANEUVER COMPLETED/i, { timeout: 15000 });
  });

  test('STOP saves the trip and opens a session-bound WHY / WHY-NOT dialog', async ({ page }) => {
    test.setTimeout(90000);
    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');
    await clickTool(page, 'add-pothole');
    await page.locator('[data-testid="lane-select"]').selectOption('RIGHT');
    // Far enough ahead to leave the lane before it (the rules need room for the manoeuvre; nearer, the vehicle only slows down).
    await page.locator('[data-testid="drawer-distance-input"]').fill('100');
    await page.locator('[data-testid="apply-config"]').click();
    await expect(await decisionAction(page)).toContainText('CHANGE_LANE_LEFT', { timeout: 45000 });
    await page.locator('[data-testid="stop-btn"]').click();

    const dialog = page.locator('[data-testid="trip-questions-dialog"]');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('ASK ABOUT THIS TRIP');
    const decisionSelect = page.locator('[data-testid="trip-decision-select"]');
    await expect(decisionSelect).toBeFocused();
    const sessionId = await dialog.getAttribute('data-session-id');
    expect(sessionId).toBeTruthy();
    const saved = await page.request.get('/api/history/sessions/' + sessionId);
    expect(saved.ok()).toBeTruthy();
    const session = await saved.json();
    const avoidance = session.decisionSnapshots.find((item: any) => item.decision.laneAction === 'CHANGE_LANE_LEFT'
      && item.decision.laneSourceRule === 'Avoid pothole in right lane by changing left');
    expect(avoidance).toBeTruthy();

    // WHY: both channels, from the rules that decided.
    await decisionSelect.selectOption(String(avoidance.simulationTimeMillis));
    const answer = page.locator('[data-testid="trip-answer"]');
    const lane = answer.locator('[data-testid="why-channel"][data-channel="LATERAL"]');
    await expect(lane).toContainText('Avoid pothole in right lane by changing left');
    await expect(lane).toContainText(/pothole [\d.]+m ahead/);
    await expect(answer.locator('[data-testid="why-channel"][data-channel="LONGITUDINAL"]')).toBeVisible();
    await expect(dialog).not.toContainText('SHOW TECHNICAL EVIDENCE');

    // WHY NOT: the alternatives not taken, each explained.
    await page.locator('[data-testid="why-not-mode"]').click();
    const whyNot = page.locator('[data-testid="why-not-select"]');
    await expect(whyNot.locator('option[value="LATERAL:CHANGE_LANE_LEFT"]')).toHaveCount(0);
    await whyNot.selectOption('LATERAL:KEEP_LANE');
    await expect(page.locator('[data-testid="why-not-answer"]')).toContainText('Avoid pothole in right lane by changing left');
    await whyNot.selectOption('LATERAL:CHANGE_LANE_RIGHT');
    await expect(page.locator('[data-testid="why-not-status"]')).toContainText('Not applicable');

    // HOW: the lateral path takes the pothole question's "yes" branch, and that decides the lane.
    await page.locator('[data-testid="how-mode"]').click();
    const lateral = page.locator('[data-testid="how-flow"][data-channel="LATERAL"]');
    await expect(lateral.locator('[data-node="F12"]')).toHaveAttribute('data-branch', 'YES');
    await expect(lateral.locator('[data-node="F12"]')).toHaveClass(/flow__step--decisive/);
    await expect(page.locator('[data-testid="how-flow"][data-channel="LONGITUDINAL"] [data-node="DV"]')).toBeVisible();

    await page.reload();
    await page.locator('button:has-text("Explanation")').click();
    await page.locator('[data-testid="session-select"]').selectOption(sessionId!);
    await expect(page.locator('[data-testid="historical-decision"]')).toContainText('CHANGE_LANE_LEFT');
    await expect(page.locator('[data-testid="why-answer"] [data-testid="why-channel"]')).toHaveCount(2);
    await page.locator('[data-testid="reset-btn"]').click();
    expect(await page.request.get('/api/history/sessions/' + sessionId).then((response) => response.ok())).toBeTruthy();
  });

  test('WHY / WHY NOT / HOW are rebuilt from each trip: a stop sign, a pothole and a red light never share an answer', async ({ page }) => {
    test.setTimeout(240000);
    await page.goto('/');

    interface Trip { name: string; scenario: string; until: string; pick: (snap: any) => boolean; node: string; world: RegExp | null }
    const trips: Trip[] = [
      { name: 'stop sign', scenario: 'S4', until: '00:10', node: 'V07', world: /World: STOP sign r\d+-\w+ [\d.]+ m ahead/,
        pick: (snap) => snap.decision.speedAction === 'STOP' },
      { name: 'pothole', scenario: 'S5', until: '00:05', node: 'F12', world: null,
        pick: (snap) => String(snap.decision.laneAction).startsWith('CHANGE_LANE') },
      { name: 'red light', scenario: 'S3', until: '00:12', node: 'VS', world: /World: traffic light r\d+-\w+ [\d.]+ m ahead, RED/,
        pick: (snap) => snap.decision.speedAction === 'STOP' }
    ];
    const answers: Record<string, { why: string; whyNot: string; how: string; rule: string; clock: string }> = {};

    for (const trip of trips) {
      await page.locator('[data-testid="reset-btn"]').click();
      await openBottomTab(page, 'Scenario Toolbox');
      await page.locator(`[data-scenario-id="${trip.scenario}"]`).click();
      await page.locator('[data-testid="start-btn"]').click();
      await expect(page.locator('[data-testid="simulation-clock"]')).toContainText(trip.until, { timeout: 60000 });
      await page.locator('[data-testid="stop-btn"]').click();

      const dialog = page.locator('[data-testid="trip-questions-dialog"]');
      await expect(dialog).toBeVisible();
      const sessionId = (await dialog.getAttribute('data-session-id'))!;
      const session = await (await page.request.get('/api/history/sessions/' + sessionId)).json();
      expect(session.scenarioId).toContain(trip.scenario);
      const snapshot = session.decisionSnapshots.find(trip.pick);
      expect(snapshot, `${trip.name}: the saved trip holds the decision to ask about`).toBeTruthy();

      const select = page.locator('[data-testid="trip-decision-select"]');
      await expect.poll(() => select.locator('option').count()).toBeGreaterThan(1);
      await select.selectOption(String(snapshot.simulationTimeMillis));
      const answer = page.locator('[data-testid="trip-answer"]');
      const rule: string = trip.name === 'pothole' ? snapshot.decision.laneSourceRule : snapshot.decision.speedSourceRule;
      const clock = clockPattern(snapshot.simulationTimeMillis);

      // WHY: the rule that decided, the clock and the speed of that very tick.
      await page.locator('[data-testid="why-mode"]').click();
      await expect(answer).toContainText(rule);
      await expect(answer).toContainText(new RegExp(clock));
      await expect(answer).toContainText(`${Math.round(snapshot.vehicle.speedKmh)} km/h in the ${snapshot.vehicle.lane} lane`);
      const why = await answer.innerText();

      await page.locator('[data-testid="why-not-mode"]').click();
      await expect(answer).toContainText(new RegExp(clock));
      const whyNot = await answer.innerText();

      // HOW: the decision path of that tick, with the question this trip turned on decisive.
      await page.locator('[data-testid="how-mode"]').click();
      await expect(answer).toContainText(new RegExp(clock));
      await expect(page.locator(`[data-testid="how-flow"] [data-node="${trip.node}"]`).first()).toHaveClass(/flow__step--decisive/);
      const how = await answer.innerText();
      if (trip.world) {
        expect(how).toMatch(trip.world);
      }
      answers[trip.name] = { why, whyNot, how, rule, clock };
      await dialog.locator('[data-testid="close-trip-dialog"]').click();
    }

    // Three different trips, three different answers in every mode, each with its own rule, clock and evidence.
    const names = Object.keys(answers);
    for (const mode of ['why', 'whyNot', 'how'] as const) {
      expect(new Set(names.map((n) => answers[n][mode])).size, `${mode} differs per trip`).toBe(names.length);
    }
    expect(new Set(names.map((n) => answers[n].rule)).size).toBe(names.length);
    for (const name of names) {
      for (const other of names.filter((n) => n !== name)) {
        for (const mode of ['why', 'whyNot'] as const) {
          expect(answers[name][mode], `${name} ${mode} must not carry the ${other} rule`).not.toContain(answers[other].rule);
        }
      }
    }
  });

  test('the decision selector explains each recorded decision of the trip', async ({ page }) => {
    test.setTimeout(90000);
    await page.goto('/');
    await loadScenario(page, 'S5 Pothole');
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');
    // Let several decisions (speed changes, the lane change around the pothole) be recorded.
    await page.waitForTimeout(6000);
    await page.locator('[data-testid="stop-btn"]').click();

    const dialog = page.locator('[data-testid="trip-questions-dialog"]');
    await expect(dialog).toBeVisible();
    const decisionSelect = page.locator('[data-testid="trip-decision-select"]');
    const options = decisionSelect.locator('option');
    // The options appear once the saved session has loaded.
    await expect.poll(() => options.count()).toBeGreaterThan(1);
    await expect(decisionSelect).toBeEnabled();

    const first = await options.first().getAttribute('value');
    await decisionSelect.selectOption(first!);
    const answer = page.locator('[data-testid="trip-answer"]');
    await expect(answer).toContainText(new RegExp(`WHY · ${clockPattern(Number(first))}`));
    await expect(answer.locator('[data-testid="why-channel"]')).toHaveCount(2);
    await page.locator('[data-testid="how-mode"]').click();
    await expect(answer.locator('[data-testid="how-flow"]')).toHaveCount(2);
  });

  test('blocked right lane forces pothole avoidance to use clear left lane', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');
    await page.waitForTimeout(250);
    await clickTool(page, 'add-vehicle');
    await page.locator('[data-testid="lane-select"]').selectOption('RIGHT');
    await page.locator('[data-testid="drawer-distance-input"]').fill('20');
    await page.locator('[data-testid="actor-speed-input"]').fill('30');
    await page.locator('[data-testid="apply-config"]').click();
    await clickTool(page, 'add-pothole');
    await page.locator('[data-testid="lane-select"]').selectOption('RIGHT');
    await page.locator('[data-testid="drawer-distance-input"]').fill('90');
    await page.locator('[data-testid="apply-config"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await expect(await decisionAction(page))
      .toContainText(/REDUCE_SPEED|EMERGENCY_BRAKE/, { timeout: 45000 });
    await expect(page.locator('[data-testid="road-ego"]'))
      .toHaveAttribute('data-lane', 'RIGHT', { timeout: 35000 });
  });

  test('sensor groups show a radar target and a camera sign detection', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('[data-testid="start-btn"]').click();

    await addWorldObject(page, 'add-vehicle', { distance: '80', lane: 'RIGHT' });
    await expect(page.locator('[data-testid="sensor-radar"]'))
      .toContainText(/TRACKING|WARNING|CRITICAL/, { timeout: 20000 });
    await expect(page.locator('[data-testid="sensor-radar"]')).toContainText(/Target|Distance|TTC/);

    await addWorldObject(page, 'add-speed-50', { distance: '100' });
    await expect(page.locator('[data-testid="sensor-camera"]'))
      .toContainText(/DETECTED|Speed limit 50/i, { timeout: 30000 });
  });

  test('presentation demo progresses across its phases', async ({ page }) => {
    test.setTimeout(150000);

    await page.goto('/');
    await page.locator('[data-testid="run-demo-btn"]').click();

    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');
    await expect(page.locator('[data-testid="demo-phase-normal"]')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('.demo__step--done')).toHaveCount(4, { timeout: 140000 });
  });

  test('overtaking slow vehicle shows turn signals, changes lane, passes, and returns to right lane', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await openBottomTab(page, 'Scenario Toolbox');
    await page.locator('[data-scenario-id="S14_SLOW_VEHICLE_OVERTAKE"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    // Telemetry HUD is visible and shows position increasing
    await expect(page.locator('[data-testid="telemetry-hud"]')).toBeVisible();
    await expect(page.locator('[data-testid="hud-position"]')).not.toContainText('0.0 m', { timeout: 15000 });

    // Vehicle decides to change left and signals left
    await expect(await decisionAction(page))
      .toContainText('CHANGE_LANE_LEFT', { timeout: 35000 });
    await expect(page.locator('[data-testid="hud-indicator"]')).toContainText('LEFT', { timeout: 15000 });

    // Vehicle reaches overtaking lane
    await expect(page.locator('[data-testid="road-ego"]')).toHaveAttribute('data-lane', 'CENTER', { timeout: 35000 });

    // Vehicle passes slow vehicle, decides return right, and returns to RIGHT lane per Art. 38 CE
    await expect(page.locator('[data-testid="road-ego"]')).toHaveAttribute('data-lane', 'RIGHT', { timeout: 45000 });
    await expect(page.locator('[data-testid="hud-indicator"]')).toContainText('OFF', { timeout: 15000 });
  });

  test('TEST A: full viewport responsiveness at 1440x900 and 1920x1080 without blank white area', async ({ page }) => {
    await page.goto('/');

    // 1440x900 verification
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.waitForTimeout(300);
    const bodyWidth1440 = await page.evaluate(() => document.body.clientWidth);
    const appRootWidth1440 = await page.evaluate(() => document.querySelector('app-root')?.clientWidth ?? 0);
    expect(bodyWidth1440).toBe(1440);
    expect(appRootWidth1440).toBe(1440);

    // 1920x1080 verification
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.waitForTimeout(300);
    const bodyWidth1920 = await page.evaluate(() => document.body.clientWidth);
    const appRootWidth1920 = await page.evaluate(() => document.querySelector('app-root')?.clientWidth ?? 0);
    expect(bodyWidth1920).toBe(1920);
    expect(appRootWidth1920).toBe(1920);
  });

  test('TEST B: toolbox object creation immediately appears in road canvas and matrix', async ({ page }) => {
    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.waitForTimeout(300);

    await page.locator('[data-testid="road-map"]').scrollIntoViewIfNeeded();

    // Add obstacle 150m ahead in CENTER
    await clickTool(page, 'add-obstacle');
    await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
    await page.locator('[data-testid="apply-config"]').click();
    await page.waitForTimeout(400);

    // Physical road obstacle is immediately visible
    const roadObstacle = page.locator('[data-testid="road-obstacle"]');
    await expect(roadObstacle).toBeVisible();
    await expect(roadObstacle).toHaveAttribute('data-object-id', 'r30-CENTER');

    // Matrix contains obstacle in row 30 CENTER
    await openBottomTab(page, 'Road Map');
    const cell = page.locator('[data-testid="map-cell-30-CENTER"]');
    await expect(cell).toHaveClass(/cell--obstacle/);
  });

  test('TEST C: painting cell in matrix immediately updates road canvas and erasing removes it', async ({ page }) => {
    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.waitForTimeout(300);

    // Select Obstacle brush (code 1)
    await page.locator('[data-testid="map-brush-1"]').click();
    const cell = page.locator('[data-testid="map-cell-25-RIGHT"]');
    await cell.scrollIntoViewIfNeeded();
    await cell.click();
    await page.waitForTimeout(400);

    // Assert road canvas immediately displays it
    const roadObstacle = page.locator('[data-testid="road-obstacle"]');
    await expect(roadObstacle).toBeVisible();
    await expect(roadObstacle).toHaveAttribute('data-object-id', 'r25-RIGHT');

    // Erase cell by clicking it again
    await cell.click();
    await page.waitForTimeout(400);
    await expect(roadObstacle).toHaveCount(0);
    await expect(cell).toHaveClass(/cell--free/);
  });

  test('TEST E: the ego car carries no visible EGO label', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('[data-testid="road-ego"]')).toBeVisible();
    await expect(page.locator('[data-testid="ego-badge"]')).toHaveCount(0);

    const egoText = await page.locator('[data-testid="road-ego"]').textContent();
    expect(egoText?.trim()).toBe('');
    await expect(page.locator('[data-testid="road-scene"] text', { hasText: /^EGO$/ })).toHaveCount(0);
  });

  test('capture evidence screenshots for viewports, toolbox objects, and the ego car', async ({ page }) => {
    test.setTimeout(60000);

    // 1. Viewport 1440x900
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    await page.waitForTimeout(500);
    await page.screenshot({ path: 'test-results/evidence/viewport-1440x900.png' });

    // 2. Viewport 1920x1080
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.waitForTimeout(500);
    await page.screenshot({ path: 'test-results/evidence/viewport-1920x1080.png' });

    // 3. Toolbox object and matrix synchronization
    await clickTool(page, 'add-obstacle');
    await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
    await page.locator('[data-testid="apply-config"]').click();
    await page.waitForTimeout(500);
    await expect(page.locator('[data-testid="road-obstacle"]')).toBeVisible();
    await page.screenshot({ path: 'test-results/evidence/toolbox-objects-road.png' });

    // 4. The ego car
    const egoEl = page.locator('[data-testid="road-ego"]');
    await egoEl.screenshot({ path: 'test-results/evidence/ego-car.png' });
  });

  test('world mutation drawer layout stability: no displacement, right-side alignment, clean cleanup', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    await page.waitForTimeout(400);

    const app = page.locator('.page');
    const beforeBox = await app.boundingBox();
    const scrollBefore = await page.evaluate(() => window.scrollY);
    const docHeightBefore = await page.evaluate(() => document.documentElement.scrollHeight);
    expect(beforeBox).not.toBeNull();

    // Open Stop sign drawer
    await clickTool(page, 'add-stop-sign');
    await page.waitForTimeout(300);

    const afterBox = await app.boundingBox();
    const scrollAfter = await page.evaluate(() => window.scrollY);
    const docHeightAfter = await page.evaluate(() => document.documentElement.scrollHeight);
    expect(afterBox).not.toBeNull();

    // Assert: zero vertical displacement of application content
    expect(afterBox!.y).toBeCloseTo(beforeBox!.y, 1);
    expect(afterBox!.width).toBeCloseTo(beforeBox!.width, 1);

    // Assert: zero scroll jump
    expect(scrollAfter).toBe(scrollBefore);

    // Assert: document height does not jump or expand
    expect(docHeightAfter).toBe(docHeightBefore);

    // Assert: drawer is pinned to right side and within viewport
    const drawer = page.locator('.drawer');
    await expect(drawer).toBeVisible();
    const drawerBox = await drawer.boundingBox();
    expect(drawerBox).not.toBeNull();
    expect(drawerBox!.x + drawerBox!.width).toBeLessThanOrEqual(1440 + 1);
    expect(drawerBox!.x).toBeGreaterThan(1000); // Right side
    expect(drawerBox!.y).toBe(0);
    expect(drawerBox!.height).toBe(900);

    // Close via close button
    await page.locator('.drawer__close').click();
    await page.waitForTimeout(300);
    await expect(drawer).toHaveCount(0);
    await expect(page.locator('.backdrop')).toHaveCount(0);

    // Open Obstacle and cancel
    await clickTool(page, 'add-obstacle');
    await page.waitForTimeout(300);
    await expect(drawer).toBeVisible();
    await page.locator('.drawer__btn--ghost').click();
    await page.waitForTimeout(300);
    await expect(drawer).toHaveCount(0);
    await expect(page.locator('.backdrop')).toHaveCount(0);

    // Open Pedestrian and add to road
    await clickTool(page, 'add-pedestrian');
    await page.waitForTimeout(300);
    await page.locator('[data-testid="apply-config"]').click();
    await page.waitForTimeout(400);
    await expect(drawer).toHaveCount(0);
    await expect(page.locator('.backdrop')).toHaveCount(0);
    await expect(page.locator('[data-testid="road-pedestrian"]')).toBeVisible();
  });

  test('VISUAL E2E: forward motion, approaching objects moving past ego and leaving forward view', async ({ page }) => {
    test.setTimeout(90000);
    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    // Add speed sign ahead at 35m
    await addWorldObject(page, 'add-speed-50', { distance: '100' });
    const sign = page.locator('[data-testid="road-speed-limit"]');
    await expect(sign).toBeVisible({ timeout: 15000 });

    const posEl = page.locator('[data-testid="hud-position"]');
    const speedEl = page.locator('[data-testid="hud-speed"]');
    const getPos = async () => parseFloat((await posEl.textContent())?.replace(/[^\d.]/g, '') ?? '0');
    const getSpeed = async () => parseFloat((await speedEl.textContent())?.replace(/[^\d.]/g, '') ?? '0');

    // Capture initial values
    const p0 = await getPos();
    // Measure the paint itself: the distance / DETECTED plate above it comes and goes.
    const paint = sign.locator('.limit-mark');
    const signBox0 = await paint.boundingBox();
    expect(signBox0).not.toBeNull();

    // Wait 2.5 seconds of live simulation
    await page.waitForTimeout(2500);

    const p1 = await getPos();
    const speed1 = await getSpeed();
    const signBox1 = await paint.boundingBox();
    expect(signBox1).not.toBeNull();

    // Assert: POSITION strictly increases
    expect(p1).toBeGreaterThan(p0);
    // Assert: Speed > 0
    expect(speed1).toBeGreaterThan(0);
    // Assert: the sign approaches the ego down the road, so it moves down the screen
    expect(signBox1!.y).toBeGreaterThan(signBox0!.y);

    // Wait until ego vehicle drives past the sign
    await page.waitForTimeout(4500);
    const p2 = await getPos();
    expect(p2).toBeGreaterThan(p1);

    // Once passed and out of the camera's view, the sign leaves the scene
    const roadBottom = (await page.locator('app-road-canvas').boundingBox())!.y
      + (await page.locator('app-road-canvas').boundingBox())!.height;
    await expect.poll(async () => {
      const count = await sign.count();
      if (count === 0) return true;
      const box = await sign.boundingBox();
      if (!box) return true;
      return box.y > roadBottom - 20;
    }, { timeout: 20000 }).toBe(true);
  });
});
