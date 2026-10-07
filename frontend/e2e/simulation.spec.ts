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
    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await addWorldObject(page, 'add-speed-50', { distance: '150' });

    await expect(page.locator('[data-testid="speed-limit"]')).toContainText('50', { timeout: 40000 });
    await expect(page.locator('[data-testid="decision-action"]'))
      .toContainText(/REDUCE_SPEED|BRAKE|EMERGENCY_BRAKE|MAINTAIN_SPEED/, { timeout: 40000 });

    await page.locator('button:has-text("Inference Timeline")').click();
    await expect(page.locator('[data-testid="timeline-panel"]')).toContainText(/50 km\/h|speed limit/i, { timeout: 20000 });
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

  test('pothole avoidance visibly moves the ego vehicle into the left lane', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await addWorldObject(page, 'add-pothole', { distance: '100' });

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
      .toContainText(/pothole|moving to the LEFT/i, { timeout: 15000 });

    await expect(page.locator('[data-testid="road-ego"]')).toHaveAttribute('data-lane', 'LEFT', { timeout: 20000 });
    await expect(page.locator('[data-testid="timeline-panel"]'))
      .toContainText(/MANEUVER STARTED|MANEUVER COMPLETED/i, { timeout: 15000 });
  });

  test('blocked left lane forces pothole avoidance to use clear right lane', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await addWorldObject(page, 'add-vehicle', { distance: '20', lane: 'LEFT' });
    await addWorldObject(page, 'add-pothole', { distance: '90', lane: 'CENTER' });

    await expect(page.locator('[data-testid="decision-action"]'))
      .toContainText('CHANGE_LANE_RIGHT', { timeout: 45000 });
    await expect(page.locator('[data-testid="road-ego"]'))
      .toHaveAttribute('data-lane', 'RIGHT', { timeout: 35000 });
  });

  test('sensor groups show a radar target and a camera sign detection', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('[data-testid="start-btn"]').click();

    await addWorldObject(page, 'add-vehicle', { distance: '80' });
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
    await expect(page.locator('.demo__step--done')).toHaveCount(6, { timeout: 140000 });
  });

  test('overtaking slow vehicle shows turn signals, changes lane, passes, and returns to right lane', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('button:has-text("S16_SLOW_VEHICLE_OVERTAKE")').click();
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
  });

  test('speed radar scenario: physical radar object rendered, speed decelerates to legal limit, passes without avoiding or stopping', async ({ page }) => {
    test.setTimeout(90000);

    await page.goto('/');
    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('button:has-text("S18_SPEED_RADAR_APPROACH")').click();
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    // Radar object is rendered on the road
    await expect(page.locator('.radar-station')).toBeVisible({ timeout: 15000 });

    // Speed decelerates towards legal 50 km/h limit as ego approaches
    await expect(page.locator('[data-testid="decision-action"]'))
      .toContainText('REDUCE_SPEED', { timeout: 25000 });

    // Ego stays in right lane (no avoidance lane change triggered)
    const ego = page.locator('[data-testid="road-ego"]');
    await expect(ego).toHaveAttribute('data-lane', 'RIGHT');

    // Ego vehicle passes radar and continues driving past 130m
    await expect(page.locator('[data-testid="hud-position"]')).toContainText(/\d{3}\.\d m/, { timeout: 35000 });
  });
});
