import { expect, test } from '@playwright/test';

const REALTIME_SOCKET = '**/api/ws/simulation';

test.describe('Realtime WebSocket channel', () => {
  test('simulation updates arrive over the WebSocket, without HTTP polling', async ({ page }) => {
    const stateReads: string[] = [];
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/simulation/state') {
        stateReads.push(request.url());
      }
    });
    const socketOpened = page.waitForEvent('websocket', (socket) => socket.url().endsWith('/api/ws/simulation'));

    await page.goto('/');
    const socket = await socketOpened;
    let frames = 0;
    socket.on('framereceived', () => frames++);

    const stream = page.locator('app-status-badge', { hasText: 'Stream' });
    await expect(stream).toContainText('LIVE');

    await page.locator('[data-testid="reset-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('STOPPED');
    await page.locator('[data-testid="start-btn"]').click();
    await expect(page.locator('[data-testid="simulation-status"]')).toContainText('RUNNING');

    const clock = page.locator('[data-testid="simulation-clock"]');
    const before = await clock.textContent();
    await page.waitForTimeout(2000);
    await expect(clock).not.toHaveText(before ?? '');

    // About ten frames a second come in; the state is read over HTTP once, when the page loads.
    expect(frames).toBeGreaterThan(10);
    expect(stateReads.length).toBeLessThanOrEqual(1);
  });

  test('a dropped channel reconnects on its own and goes live again', async ({ page }) => {
    let connections = 0;
    let dropFirst: (() => Promise<void>) | undefined;
    await page.routeWebSocket(REALTIME_SOCKET, (socket) => {
      connections++;
      socket.connectToServer();
      if (connections === 1) {
        dropFirst = () => socket.close({ code: 1011, reason: 'dropped by the test' });
      }
    });

    await page.goto('/');
    const stream = page.locator('app-status-badge', { hasText: 'Stream' });
    await expect(stream).toContainText('LIVE');

    await dropFirst!();
    await expect(stream).toContainText('RECONNECTING');
    await expect(page.getByText('Realtime channel disconnected. Trying to reconnect...')).toBeVisible();

    await expect(stream).toContainText('LIVE', { timeout: 10_000 });
    await expect(page.getByText('Realtime channel disconnected. Trying to reconnect...')).toHaveCount(0);
    expect(connections).toBe(2);
  });
});
