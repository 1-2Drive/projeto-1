import { expect, test } from '@playwright/test';

const TITLE = /Sistema Inteligente de Apoio . Decis.o de Condu..o/;

/** Fills the configuration drawer and confirms it, which is the only way to mutate the world. */
async function addWorldObject(
  page: import('@playwright/test').Page,
  trigger: string,
  options: { distance?: string; lane?: string } = {}
): Promise<void> {
  await page.locator(`[data-testid="${trigger}"]`).click();
  await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
  if (options.distance !== undefined) {
    await page.locator('[data-testid="drawer-distance-input"]').fill(options.distance);
  }
  if (options.lane !== undefined) {
    await page.locator('[data-testid="lane-select"]').selectOption(options.lane);
  }
  await page.locator('[data-testid="apply-config"]').click();
  await expect(page.locator('[data-testid="config-panel"]')).toHaveCount(0);
  await expect(page.locator('[data-testid="timeline-panel"]')).toContainText(/PERCEPTION|RULE|FACT|ACTUATOR/i, {
    timeout: 10000
  });
}

async function loadScenario(page: import('@playwright/test').Page, exactName: string): Promise<void> {
  await page.locator('[data-testid="reset-btn"]').click();
  await page.getByRole('button', { name: exactName, exact: true }).click();
  await expect(page.locator('[data-testid="simulation-status"]')).toContainText('STOPPED');
  await expect(page.locator('[data-testid="simulation-clock"]')).toContainText('00:00.000');
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
    await expect(page.locator('[data-testid="decision-action"]'))
      .toContainText(/REDUCE_SPEED|BRAKE|EMERGENCY_BRAKE|MAINTAIN_SPEED/, { timeout: 40000 });

    await page.locator('button:has-text("Inference Timeline")').click();
    await expect(page.locator('[data-testid="timeline-panel"]')).toContainText(/50 km\/h|speed limit/i, { timeout: 20000 });
  });

  test('speed limit has no lane selector and is painted on all three lanes', async ({ page }) => {
    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');

    for (const limit of ['30', '90']) {
      await page.locator('[data-testid="reset-btn"]').click();
      await page.locator(`[data-testid="add-speed-${limit}"]`).click();
      await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
      await expect(page.locator('[data-testid="lane-select"]')).toHaveCount(0);
      await page.locator('[data-testid="drawer-distance-input"]').fill('60');
      await page.locator('[data-testid="apply-config"]').click();

      await expect(page.locator('[data-testid="road-speed-limit"]')).toHaveCount(1);
      const marks = page.locator('.limit-mark');
      await expect(marks).toHaveCount(3);
      await expect(marks).toHaveText([limit, limit, limit]);
      const boxes = await marks.evaluateAll((nodes) => nodes.map((node) => node.getBoundingClientRect().x));
      expect(Math.max(...boxes) - Math.min(...boxes)).toBeLessThan(1);
    }
  });

  test('traffic light has no lane selector and is drawn on all three lanes', async ({ page }) => {
    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');

    await page.locator('[data-testid="add-traffic-light"]').click();
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
    const xs = await lights.evaluateAll((nodes) => nodes.map((node) => {
      const box = node.getBoundingClientRect();
      return box.x + box.width / 2;
    }));
    expect(Math.max(...xs) - Math.min(...xs)).toBeLessThan(1);
  });

  test('stop sign is placed only in the lane chosen in the drawer', async ({ page }) => {
    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');

    for (const lane of ['LEFT', 'CENTER', 'RIGHT']) {
      await page.locator('[data-testid="reset-btn"]').click();
      await page.locator('[data-testid="add-stop-sign"]').click();
      await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
      await page.locator('[data-testid="drawer-distance-input"]').fill('60');
      await page.locator('[data-testid="lane-select"]').selectOption(lane);
      await page.locator('[data-testid="apply-config"]').click();

      const sign = page.locator('[data-testid="road-stop-sign"]');
      await expect(sign).toHaveCount(1);
      await expect(sign).toHaveAttribute('data-object-id', `r12-${lane}`);
    }
  });

  test('pedestrian interaction produces a safety decision', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await addWorldObject(page, 'add-pedestrian', { distance: '8' });

    await expect(page.locator('[data-testid="decision-action"]'))
      .toContainText(/STOP|BRAKE|EMERGENCY_BRAKE/, { timeout: 40000 });

    await page.locator('button:has-text("Inference Timeline")').click();
    await expect(page.locator('[data-testid="timeline-panel"]'))
      .toContainText(/pedestrian/i, { timeout: 20000 });
  });

  test('pothole avoidance visibly moves the ego vehicle from RIGHT to CENTER', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');
    await page.waitForTimeout(250);
    await page.locator('[data-testid="add-pothole"]').click();
    await page.locator('[data-testid="lane-select"]').selectOption('RIGHT');
    await page.locator('[data-testid="drawer-distance-input"]').fill('100');
    await page.locator('[data-testid="apply-config"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await expect(page.locator('[data-testid="decision-action"]'))
      .toContainText('CHANGE_LANE_LEFT', { timeout: 45000 });
    await expect(page.locator('[data-testid="road-ego"]'))
      .toHaveAttribute('data-maneuver', /REQUESTED|CHANGING/, { timeout: 15000 });

    const lateralAtStart = Number(await page.locator('[data-testid="road-ego"]').getAttribute('data-lateral'));
    await expect.poll(async () => {
      const value = await page.locator('[data-testid="road-ego"]').getAttribute('data-lateral');
      return Number(value);
    }, { timeout: 15000 }).toBeLessThan(lateralAtStart - 0.1);
    // The live narration describes the move only while it runs; data-lane switches when it completes.
    await expect(page.locator('[data-testid="situation-panel"]'))
      .toContainText(/pothole|moving to the CENTER/i, { timeout: 15000 });

    await expect(page.locator('[data-testid="road-ego"]')).toHaveAttribute('data-lane', 'CENTER', { timeout: 20000 });
    await expect(page.locator('[data-testid="timeline-panel"]'))
      .toContainText(/MANEUVER STARTED|MANEUVER COMPLETED/i, { timeout: 15000 });
  });

  test('STOP saves the trip and opens a session-bound WHY / WHY-NOT dialog', async ({ page }) => {
    test.setTimeout(90000);
    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');
    await page.locator('[data-testid="add-pothole"]').click();
    await page.locator('[data-testid="lane-select"]').selectOption('RIGHT');
    await page.locator('[data-testid="drawer-distance-input"]').fill('25');
    await page.locator('[data-testid="apply-config"]').click();
    await expect(page.locator('[data-testid="decision-action"]')).toContainText('CHANGE_LANE_LEFT', { timeout: 45000 });
    await page.locator('[data-testid="stop-btn"]').click();

    const dialog = page.locator('[data-testid="trip-questions-dialog"]');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('ASK ABOUT THIS TRIP');
    await expect(page.locator('[data-testid="trip-question-input"]')).toBeFocused();
    const sessionId = await dialog.getAttribute('data-session-id');
    expect(sessionId).toBeTruthy();
    const saved = await page.request.get('/api/history/sessions/' + sessionId);
    expect(saved.ok()).toBeTruthy();
    const session = await saved.json();
    expect(session.decisionSnapshots.some((item: any) => item.decision.action === 'CHANGE_LANE_LEFT')).toBeTruthy();

    await page.locator('[data-testid="trip-question-input"]').fill('Why did the vehicle change to the left lane?');
    await page.locator('[data-testid="ask-trip-question"]').click();
    await expect(page.locator('[data-testid="trip-answer"]')).toContainText('Avoid pothole in right lane by changing left');
    await expect(page.locator('[data-testid="trip-answer"]')).toContainText(/pothole [\d.]+m ahead/);

    await page.locator('[data-testid="why-not-mode"]').click();
    await page.locator('[data-testid="trip-question-input"]').fill("Why didn't it brake?");
    await page.locator('[data-testid="ask-trip-question"]').click();
    await expect(page.locator('[data-testid="trip-answer"]')).toContainText('pothole rule');
    await expect(page.locator('[data-testid="trip-answer"]')).toContainText('BRAKE was not proposed');

    await page.locator('[data-testid="how-mode"]').click();
    await page.locator('[data-testid="trip-question-input"]').fill('How did the system decide to change lane?');
    await page.locator('[data-testid="ask-trip-question"]').click();
    const howSteps = page.locator('[data-testid="how-steps"]');
    await expect(howSteps).toBeVisible();
    await expect(howSteps).toContainText('WORLD');
    await expect(howSteps).toContainText('SENSORS');
    await expect(howSteps).toContainText('WORKING MEMORY');
    await expect(howSteps).toContainText(/RULE EVALUATION/);
    await expect(howSteps).toContainText('DECISION / ARBITRATION');
    await expect(howSteps).toContainText('ACTUATOR');

    await page.reload();
    await page.locator('button:has-text("Explanation")').click();
    await page.locator('[data-testid="session-select"]').selectOption(sessionId!);
    await expect(page.locator('[data-testid="historical-decision"]')).toContainText('CHANGE_LANE_LEFT');
    await page.locator('[data-testid="reset-btn"]').click();
    expect(await page.request.get('/api/history/sessions/' + sessionId).then((response) => response.ok())).toBeTruthy();
  });

  test('ambiguous question shows a decision selector instead of guessing', async ({ page }) => {
    test.setTimeout(90000);
    await page.goto('/');
    await loadScenario(page, 'S5 Pothole');
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');
    // Let several ticks (multiple MAINTAIN_SPEED / REDUCE_SPEED / lane-change decisions) accumulate
    // so the WHY-NOT question below has more than one plausible historical match.
    await page.waitForTimeout(6000);
    await page.locator('[data-testid="stop-btn"]').click();

    const dialog = page.locator('[data-testid="trip-questions-dialog"]');
    await expect(dialog).toBeVisible();

    await page.locator('[data-testid="why-not-mode"]').click();
    await page.locator('[data-testid="trip-question-input"]').fill("Why didn't it brake?");
    await page.locator('[data-testid="ask-trip-question"]').click();

    const ambiguity = page.locator('[data-testid="decision-ambiguity"]');
    await expect(ambiguity).toBeVisible();
    await expect(ambiguity).toContainText('Which decision do you mean?');
    await ambiguity.locator('button').first().click();
    await expect(page.locator('[data-testid="trip-answer"]')).toBeVisible();
  });

  test('blocked right lane forces pothole avoidance to use clear left lane', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await loadScenario(page, 'S1 Empty safe road');
    await page.waitForTimeout(250);
    await page.locator('[data-testid="add-vehicle"]').click();
    await page.locator('[data-testid="lane-select"]').selectOption('RIGHT');
    await page.locator('[data-testid="drawer-distance-input"]').fill('20');
    await page.locator('[data-testid="actor-speed-input"]').fill('30');
    await page.locator('[data-testid="apply-config"]').click();
    await page.locator('[data-testid="add-pothole"]').click();
    await page.locator('[data-testid="lane-select"]').selectOption('RIGHT');
    await page.locator('[data-testid="drawer-distance-input"]').fill('90');
    await page.locator('[data-testid="apply-config"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await expect(page.locator('[data-testid="decision-action"]'))
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
    await page.locator('button:has-text("S14_SLOW_VEHICLE_OVERTAKE")').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    // Telemetry HUD is visible and shows position increasing
    await expect(page.locator('[data-testid="telemetry-hud"]')).toBeVisible();
    await expect(page.locator('[data-testid="hud-position"]')).not.toContainText('0.0 m', { timeout: 15000 });

    // Vehicle decides to change left and signals left
    await expect(page.locator('[data-testid="decision-action"]'))
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
    await page.locator('[data-testid="add-obstacle"]').click();
    await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
    await page.locator('[data-testid="apply-config"]').click();
    await page.waitForTimeout(400);

    // Physical road obstacle is immediately visible
    const roadObstacle = page.locator('[data-testid="road-obstacle"]');
    await expect(roadObstacle).toBeVisible();
    await expect(roadObstacle).toHaveAttribute('data-object-id', 'r30-CENTER');

    // Matrix contains obstacle in row 30 CENTER
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

  test('TEST E: clean EGO badge is displayed and EGOCRUISING never appears', async ({ page }) => {
    await page.goto('/');
    const egoBadge = page.locator('[data-testid="ego-badge"]');
    await expect(egoBadge).toBeVisible();
    await expect(egoBadge).toHaveText('EGO');

    const egoText = await page.locator('[data-testid="road-ego"]').textContent();
    expect(egoText?.trim()).toBe('EGO');
    expect(egoText).not.toContain('EGOCRUISING');
    expect(egoText).not.toContain('CRUISING');
  });

  test('capture evidence screenshots for viewports, toolbox objects, and clean EGO badge', async ({ page }) => {
    test.setTimeout(60000);

    // 1. Viewport 1440x900
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    await page.waitForTimeout(500);
    await page.screenshot({ path: '../screenshot_viewport_1440x900.png' });

    // 2. Viewport 1920x1080
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.waitForTimeout(500);
    await page.screenshot({ path: '../screenshot_viewport_1920x1080.png' });

    // 3. Toolbox object and matrix synchronization
    await page.locator('[data-testid="add-obstacle"]').click();
    await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
    await page.locator('[data-testid="apply-config"]').click();
    await page.waitForTimeout(500);
    await expect(page.locator('[data-testid="road-obstacle"]')).toBeVisible();
    await page.screenshot({ path: '../screenshot_toolbox_objects_road.png' });

    // 4. Clean EGO badge
    const egoEl = page.locator('[data-testid="road-ego"]');
    await egoEl.screenshot({ path: '../screenshot_clean_ego_badge.png' });
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
    await page.locator('[data-testid="add-stop-sign"]').click();
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
    await page.locator('[data-testid="add-obstacle"]').click();
    await page.waitForTimeout(300);
    await expect(drawer).toBeVisible();
    await page.locator('.drawer__btn--ghost').click();
    await page.waitForTimeout(300);
    await expect(drawer).toHaveCount(0);
    await expect(page.locator('.backdrop')).toHaveCount(0);

    // Open Pedestrian and add to road
    await page.locator('[data-testid="add-pedestrian"]').click();
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
    const signBox0 = await sign.boundingBox();
    expect(signBox0).not.toBeNull();

    // Wait 2.5 seconds of live simulation
    await page.waitForTimeout(2500);

    const p1 = await getPos();
    const speed1 = await getSpeed();
    const signBox1 = await sign.boundingBox();
    expect(signBox1).not.toBeNull();

    // Assert: POSITION strictly increases
    expect(p1).toBeGreaterThan(p0);
    // Assert: Speed > 0
    expect(speed1).toBeGreaterThan(0);
    // Assert: sign screen coordinates change and approach ego from right to left
    expect(signBox1!.x).toBeLessThan(signBox0!.x);

    // Wait until ego vehicle drives past the sign
    await page.waitForTimeout(4500);
    const p2 = await getPos();
    expect(p2).toBeGreaterThan(p1);

    // Once passed and moved off-screen, sign leaves forward view
    await expect.poll(async () => {
      const count = await sign.count();
      if (count === 0) return true;
      const box = await sign.boundingBox();
      if (!box) return true;
      return box.x < 300;
    }, { timeout: 20000 }).toBe(true);
  });
});
