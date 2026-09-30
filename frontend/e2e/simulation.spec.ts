import { expect, test } from '@playwright/test';

const TITLE = /Sistema Inteligente de Apoio . Decis.o de Condu..o/;

/** Fills the configuration drawer and confirms it, which is the only way to mutate the world. */
async function addWorldObject(
  page: import('@playwright/test').Page,
  trigger: string,
  options: { distance?: string } = {}
): Promise<void> {
  await page.locator(`[data-testid="${trigger}"]`).click();
  await expect(page.locator('[data-testid="config-panel"]')).toBeVisible();
  if (options.distance !== undefined) {
    await page.locator('[data-testid="drawer-distance-input"]').fill(options.distance);
  }
  await page.locator('[data-testid="apply-config"]').click();
  await expect(page.locator('[data-testid="config-panel"]')).toHaveCount(0);
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

  test('presentation demo progresses across its phases', async ({ page }) => {
    test.setTimeout(150000);

    await page.goto('/');
    await page.locator('[data-testid="run-demo-btn"]').click();

    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');
    await expect(page.locator('[data-testid="demo-phase-normal"]')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('.demo__step--done')).toHaveCount(6, { timeout: 140000 });
  });
});