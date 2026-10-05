import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { adminApi, type AdminApi } from './support/api';

// Spec 11.6 "Matching the design" 2: baselines for the five design screens on chromium-desktop,
// in their own stack (SHOTS_*), seeded with the sample data of the designs. Relative times and
// usernames are masked so the pictures do not depend on the clock.
const SHOTS_UI = process.env.SHOTS_UI_URL ?? 'http://localhost:3000';
const SHOTS_API = process.env.SHOTS_API_URL ?? 'http://localhost:8080';
const TOKEN_KEY = 'ff.accessToken';

test.use({ baseURL: SHOTS_UI });
test.describe.configure({ mode: 'serial' });

/** The screenshot stack is seeded by scripts/lib/seed-shots.sh before the tests run. */
async function seed(request: APIRequestContext): Promise<{ api: AdminApi; orders: string }> {
  const api = await adminApi(request, SHOTS_API);
  const orders = (await api.groups()).find((g) => g.key === 'orders');
  if (!orders) throw new Error('screenshot stack is not seeded (scripts/lib/seed-shots.sh)');
  return { api, orders: orders.id };
}

async function signIn(page: Page, token: string) {
  await page.goto('/login');
  await page.evaluate(([k, t]) => sessionStorage.setItem(k, t), [TOKEN_KEY, token] as const);
}

const masks = (page: Page) => [
  page.getByText(/(ago|just now)$/),
  page.getByText(/^(by )?(admin|system)$/),
  page.locator('[role="cell"]').filter({ hasText: /^\d{1,2}\/\d{1,2}\/\d{4}|^\d{4}-\d{2}-\d{2}/ }),
];

test('screen 1: sign in', async ({ page }) => {
  await page.goto('/login?expired=1');
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await expect(page).toHaveScreenshot('1-sign-in.png', { fullPage: true });
});

test('screen 2: flags workspace', async ({ page, request }) => {
  const { api, orders } = await seed(request);
  await signIn(page, api.token);
  await page.goto(`/groups/${orders}`);
  await expect(page.getByRole('heading', { name: 'Orders' })).toBeVisible();
  await expect(page.getByRole('switch')).toHaveCount(4);
  await expect(page).toHaveScreenshot('2-flags-workspace.png', {
    fullPage: true,
    mask: masks(page),
  });
});

test('screen 3: new flag dialog', async ({ page, request }) => {
  const { api, orders } = await seed(request);
  await signIn(page, api.token);
  await page.goto(`/groups/${orders}`);
  await page.getByRole('button', { name: 'New flag' }).click();
  const dialog = page.getByRole('dialog', { name: 'New flag in Orders' });
  await dialog.getByLabel('Key').fill('free-shipping-banner');
  await dialog
    .getByLabel('Description (optional)')
    .fill('Show the free shipping banner on the cart page');
  await expect(page).toHaveScreenshot('3-new-flag-dialog.png', {
    fullPage: true,
    mask: masks(page),
  });
});

test('screen 4: delete group confirmation', async ({ page, request }) => {
  const { api, orders } = await seed(request);
  await signIn(page, api.token);
  await page.goto(`/groups/${orders}`);
  await page.getByRole('button', { name: 'Delete group' }).click();
  await expect(page.getByRole('alertdialog')).toBeVisible();
  await expect(page).toHaveScreenshot('4-delete-group-confirmation.png', {
    fullPage: true,
    mask: masks(page),
  });
});

test('screen 5: audit log', async ({ page, request }) => {
  const { api } = await seed(request);
  await signIn(page, api.token);
  await page.goto('/audit');
  await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
  await expect(page.getByRole('row').nth(1)).toBeVisible();
  await expect(page).toHaveScreenshot('5-audit-log.png', {
    fullPage: true,
    mask: [...masks(page), page.locator('[role="row"] > [role="cell"]:first-child')],
  });
});
