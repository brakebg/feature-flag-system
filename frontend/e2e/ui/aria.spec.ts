import type { APIRequestContext, Locator, Page } from '@playwright/test';
import { expect, test } from '../support/fixtures';
import { adminApi } from '../support/api';

// Spec 005 4.8, AC-UI-6: the ARIA snapshot of each of the five screens (docs/product/design/README.md)
// equals the one recorded from the code before the redesign. The snapshots were recorded once at
// 1440 x 900 on the seeded screenshot stack (scripts/lib/seed-shots.sh) and never change; relative
// times and dates are patterns. Read-only: the tests change no data on that stack.
const SHOTS_UI = process.env.SHOTS_UI_URL ?? 'http://localhost:3000';
const SHOTS_API = process.env.SHOTS_API_URL ?? 'http://localhost:8080';
const TOKEN_KEY = 'ff.accessToken';

test.use({ baseURL: SHOTS_UI, viewport: { width: 1440, height: 900 } });

async function signIn(page: Page, request: APIRequestContext): Promise<string> {
  const api = await adminApi(request, SHOTS_API);
  const orders = (await api.groups()).find((g) => g.key === 'orders');
  if (!orders) throw new Error('screenshot stack is not seeded (scripts/lib/seed-shots.sh)');
  await page.goto('/login');
  await page.evaluate(([k, t]) => sessionStorage.setItem(k, t), [TOKEN_KEY, api.token] as const);
  return orders.id;
}

const body = (page: Page): Locator => page.locator('body');

test.describe('[AC-UI-6] ARIA snapshot of each screen is unchanged (spec 005 4.8)', () => {
  test('screen 1: sign in', async ({ page }) => {
    await page.goto('/login?expired=1');
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await expect(body(page)).toMatchAriaSnapshot({ name: '1-sign-in.aria.yml' });
  });

  test('screen 2: flags workspace', async ({ page, request }) => {
    const orders = await signIn(page, request);
    await page.goto(`/groups/${orders}`);
    await expect(page.getByRole('heading', { name: 'Orders' })).toBeVisible();
    await expect(page.getByRole('switch')).toHaveCount(4);
    await expect(body(page)).toMatchAriaSnapshot({ name: '2-flags-workspace.aria.yml' });
  });

  test('screen 3: new flag dialog', async ({ page, request }) => {
    const orders = await signIn(page, request);
    await page.goto(`/groups/${orders}`);
    await expect(page.getByRole('switch')).toHaveCount(4);
    await page.getByRole('button', { name: 'New flag' }).click();
    const dialog = page.getByRole('dialog', { name: 'New flag in Orders' });
    await dialog.getByLabel('Key').fill('free-shipping-banner');
    await dialog
      .getByLabel('Description (optional)')
      .fill('Show the free shipping banner on the cart page');
    await expect(body(page)).toMatchAriaSnapshot({ name: '3-new-flag-dialog.aria.yml' });
  });

  test('screen 4: delete group confirmation', async ({ page, request }) => {
    const orders = await signIn(page, request);
    await page.goto(`/groups/${orders}`);
    await expect(page.getByRole('switch')).toHaveCount(4);
    await page.getByRole('button', { name: 'Delete group' }).click();
    await expect(page.getByRole('alertdialog')).toBeVisible();
    await expect(body(page)).toMatchAriaSnapshot({ name: '4-delete-group-confirmation.aria.yml' });
  });

  test('screen 5: audit log', async ({ page, request }) => {
    await signIn(page, request);
    await page.goto('/audit');
    await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
    await expect(page.getByRole('row').nth(1)).toBeVisible();
    await expect(body(page)).toMatchAriaSnapshot({ name: '5-audit-log.aria.yml' });
  });
});
