import { defineConfig, devices } from '@playwright/test';

// Spec 11.6: four browser projects; retries 0 (gate 12 runs --repeat-each=2);
// screenshot baselines on chromium-desktop with maxDiffPixelRatio 0.01.
const desktop = { width: 1440, height: 900 };

export default defineConfig({
  testDir: './e2e',
  retries: 0,
  workers: 4,
  fullyParallel: true,
  forbidOnly: true,
  reporter: [
    ['list'],
    ['json', { outputFile: '../build/reports/playwright.json' }],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
  ],
  expect: {
    toHaveScreenshot: { maxDiffPixelRatio: 0.01 },
  },
  use: {
    baseURL: process.env.UI_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium-desktop',
      use: { ...devices['Desktop Chrome'], viewport: desktop },
    },
    {
      name: 'chromium-narrow',
      use: { ...devices['Desktop Chrome'], viewport: { width: 800, height: 900 } },
    },
    {
      name: 'firefox-desktop',
      grep: /@cross-browser/,
      use: { ...devices['Desktop Firefox'], viewport: desktop },
    },
    {
      name: 'webkit-desktop',
      grep: /@cross-browser/,
      use: { ...devices['Desktop Safari'], viewport: desktop },
    },
  ],
});
