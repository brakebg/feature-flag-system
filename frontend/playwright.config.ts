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
    // API-only tests run once, without a browser (spec 11.6).
    { name: 'api', testMatch: /e2e\/api\/.*\.spec\.ts/ },
    // Group limit and FF_REQUIRE_HTTPS run in their own stack with 1 worker (spec 11.5).
    { name: 'limits', testMatch: /e2e\/limits\/.*\.spec\.ts/, workers: 1 },
    {
      name: 'chromium-desktop',
      testMatch: /e2e\/(ui\/.*|style|screenshots)\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: desktop },
    },
    {
      name: 'chromium-narrow',
      testMatch: /e2e\/(ui\/.*|style)\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 800, height: 900 } },
    },
    {
      name: 'firefox-desktop',
      grep: /@cross-browser/,
      testMatch: /e2e\/ui\/.*\.spec\.ts/,
      use: { ...devices['Desktop Firefox'], viewport: desktop },
    },
    {
      name: 'webkit-desktop',
      grep: /@cross-browser/,
      testMatch: /e2e\/ui\/.*\.spec\.ts/,
      use: { ...devices['Desktop Safari'], viewport: desktop },
    },
    // Global revision / ETag tests: alone, after all other projects (spec 11.5).
    {
      name: 'serial',
      testMatch: /e2e\/serial\/.*\.spec\.ts/,
      workers: 1,
      dependencies: [
        'api',
        'limits',
        'chromium-desktop',
        'chromium-narrow',
        'firefox-desktop',
        'webkit-desktop',
      ],
    },
  ],
});
