import { expect, test } from '@playwright/test';

test.describe('Driving expert simulation UI', () => {
  test('start simulation and add speed sign triggers reduce speed flow', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByText('Sistema Inteligente de Apoio a Decisao de Conducao')).toBeVisible();
    await expect(page.locator('[data-testid="backend-status"]')).toContainText('CONNECTED');

    await page.locator('[data-testid="reset-btn"]').click();

    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await page.locator('[data-testid="distance-input"]').fill('150');

    await page.locator('[data-testid="add-speed-50"]').click();

    await expect(page.locator('[data-testid="speed-limit"]')).toContainText('50', { timeout: 25000 });
    await expect(page.locator('[data-testid="decision-action"]')).toContainText(/REDUCE_SPEED|BRAKE|EMERGENCY_BRAKE/, { timeout: 25000 });
    await expect(page.locator('[data-testid="speed-recommended"]')).toContainText(/50/, { timeout: 25000 });
  });

  test('pedestrian interaction produces safety decision', async ({ page }) => {
    await page.goto('/');

    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await page.locator('[data-testid="distance-input"]').fill('8');
    await page.locator('[data-testid="add-pedestrian"]').click();
    await page.locator('[data-testid="config-panel"] button.primary').click();

    await page.locator('button:has-text("Inference Timeline")').click();
    await expect(page.locator('[data-testid="timeline-panel"]')).toContainText(/Pedestrian|pedestrian|STOP|EMERGENCY_BRAKE/, { timeout: 15000 });
  });

  test('presentation demo progresses across speed, TTC and pedestrian phases', async ({ page }) => {
    test.setTimeout(130000);

    await page.goto('/');

    await page.locator('[data-testid="run-demo-btn"]').click();

    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');
    await expect(page.locator('.step.done').filter({ hasText: 'Normal driving' })).toHaveCount(1, { timeout: 15000 });

    await expect(page.locator('.step.done').filter({ hasText: 'Speed limit' })).toHaveCount(1, { timeout: 30000 });
    await expect(page.locator('.step.done').filter({ hasText: 'Weather' })).toHaveCount(1, { timeout: 30000 });
    await expect(page.locator('.step.done').filter({ hasText: 'TTC safety' })).toHaveCount(1, { timeout: 40000 });
    await expect(page.locator('.step.done').filter({ hasText: 'Yield / roundabout' })).toHaveCount(1, { timeout: 20000 });

    await expect(page.locator('.step.done').filter({ hasText: 'Pedestrian' })).toHaveCount(1, { timeout: 25000 });
  });
});
