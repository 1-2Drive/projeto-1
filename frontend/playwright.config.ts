import { defineConfig } from '@playwright/test';

const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
  || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

export default defineConfig({
  testDir: './e2e',
  timeout: 180_000,
  expect: {
    timeout: 12_000
  },
  use: {
    baseURL: 'http://localhost:4200',
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    channel: undefined,
    launchOptions: {
      executablePath
    }
  }
});
