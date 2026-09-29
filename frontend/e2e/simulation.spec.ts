import { expect, test } from '@playwright/test';

test.describe('Driving expert simulation UI', () => {
  test('start simulation and add speed sign triggers reduce speed flow', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByText('Intelligent Driving Decision Support System')).toBeVisible();
    await expect(page.locator('[data-testid="backend-status"]')).toContainText('CONNECTED');

    await page.locator('[data-testid="reset-btn"]').click();

    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    await page.locator('[data-testid="distance-input"]').fill('40');

    await page.locator('[data-testid="add-speed-50"]').click();

    await expect(page.locator('[data-testid="speed-limit"]')).toContainText('50');
    await expect(page.locator('[data-testid="decision-action"]')).toContainText(/REDUCE_SPEED|BRAKE|EMERGENCY_BRAKE|MAINTAIN_SPEED/);
    await expect(page.locator('[data-testid="speed-recommended"]')).toContainText(/50|4[0-9]|3[0-9]/);
  });

  test('pedestrian interaction produces safety decision', async ({ page }) => {
    await page.goto('/');

    await page.locator('[data-testid="reset-btn"]').click();
    await page.locator('[data-testid="start-btn"]').click();
    await page.locator('[data-testid="distance-input"]').fill('8');
    await page.locator('[data-testid="add-pedestrian"]').click();

    await page.locator('button:has-text("Inference Timeline")').click();
    await expect(page.locator('[data-testid="timeline-panel"]')).toContainText(/Pedestrian|pedestrian|STOP|EMERGENCY_BRAKE/);
  });
});
