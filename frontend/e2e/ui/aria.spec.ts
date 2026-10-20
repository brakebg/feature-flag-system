/// <reference lib="dom" />
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import type { APIRequestContext, Locator, Page, TestInfo } from '@playwright/test';
import { expect, test } from '../support/fixtures';
import { adminApi, keyFor } from '../support/api';

// Spec 005 4.8, AC-UI-6: the ARIA snapshot of each screen equals the one recorded from the code
// before the redesign. The five design screens were recorded at 1440 x 900 on the seeded screenshot
// stack (scripts/lib/seed-shots.sh) in 8f4a50a; the other screens of the criteria list ("the
// screens" in docs/project-acceptance-criteria.md) were recorded later from the same
// pre-redesign build (DECISIONS D-007). Relative times and dates are patterns. The snapshot
// files never change. Read-only on the screenshot stack: the tests change no data there.
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

async function openOrders(page: Page, request: APIRequestContext) {
  const orders = await signIn(page, request);
  await page.goto(`/groups/${orders}`);
  await expect(page.getByRole('heading', { name: 'Orders' })).toBeVisible();
  await expect(page.getByRole('switch')).toHaveCount(4);
}

/**
 * "Equals", not "contains" (TA-1): the recorded snapshot is compared with `/children: deep-equal`,
 * so an extra or missing node anywhere fails. A snapshot that does not exist yet is recorded.
 */
async function expectAria(locator: Locator, name: string, info: TestInfo) {
  const file = path.join(info.project.testDir, 'ui', 'aria.spec.ts-snapshots', name);
  if (!existsSync(file)) {
    await expect(locator).toMatchAriaSnapshot({ name });
    return;
  }
  // The footer shows the release (deployment.md 9.6). The snapshots were recorded from the 1.1.0
  // build; the footer must show this build's VERSION exactly (D-011).
  const version = readFileSync(
    path.join(info.project.testDir, '..', '..', 'VERSION'),
    'utf8',
  ).trim();
  const template = readFileSync(file, 'utf8').replace(
    /^- contentinfo: v1\.1\.0$/m,
    `- contentinfo: v${version}`,
  );
  await expect(locator).toMatchAriaSnapshot(`- /children: deep-equal\n${template}`);
}

/**
 * The ARIA states the snapshot leaves out (false values, aria-current), in DOM order, for every
 * element that carries aria-checked, aria-pressed, aria-current or aria-invalid (TA-1, TA-5).
 */
async function ariaStates(scope: Locator): Promise<string> {
  return scope.evaluate((root) => {
    const attrs = ['aria-checked', 'aria-pressed', 'aria-current', 'aria-invalid'];
    const selector = attrs.map((a) => `[${a}]`).join(', ');
    const nameOf = (el: Element) => {
      const label = el.getAttribute('aria-label');
      if (label) return label;
      const by = el.getAttribute('aria-labelledby');
      if (by) return document.getElementById(by)?.textContent?.trim() ?? '';
      const labels = (el as HTMLInputElement).labels;
      if (labels?.length) return labels[0].textContent?.trim() ?? '';
      return el.textContent?.trim() ?? '';
    };
    return Array.from(root.querySelectorAll(selector))
      .map((el) => {
        const role = el.getAttribute('role') ?? el.tagName.toLowerCase();
        const states = attrs
          .filter((a) => el.hasAttribute(a))
          .map((a) => `${a}=${el.getAttribute(a)}`)
          .join(' ');
        return `${role} "${nameOf(el)}" ${states}`;
      })
      .join('\n');
  });
}

async function expectScreen(scope: Locator, base: string, info: TestInfo) {
  await expectAria(scope, `${base}.aria.yml`, info);
  expect(await ariaStates(scope)).toMatchSnapshot(`${base}.states.txt`);
}

test.describe('[AC-UI-6] ARIA snapshot of each screen is unchanged (spec 005 4.8)', () => {
  test('screen 1: sign in', async ({ page }, info) => {
    await page.goto('/login?expired=1');
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await expectScreen(page.locator('body'), '1-sign-in', info);
  });

  test('screen 2: flags workspace', async ({ page, request }, info) => {
    await openOrders(page, request);
    await expectScreen(page.locator('body'), '2-flags-workspace', info);
  });

  test('screen 3: new flag dialog', async ({ page, request }, info) => {
    await openOrders(page, request);
    await page.getByRole('button', { name: 'New flag' }).click();
    const dialog = page.getByRole('dialog', { name: 'New flag in Orders' });
    await dialog.getByLabel('Key').fill('free-shipping-banner');
    await dialog
      .getByLabel('Description (optional)')
      .fill('Show the free shipping banner on the cart page');
    await expectScreen(page.locator('body'), '3-new-flag-dialog', info);
  });

  test('screen 4: delete group confirmation', async ({ page, request }, info) => {
    await openOrders(page, request);
    await page.getByRole('button', { name: 'Delete group' }).click();
    await expect(page.getByRole('alertdialog')).toBeVisible();
    await expectScreen(page.locator('body'), '4-delete-group-confirmation', info);
  });

  test('screen 5: audit log', async ({ page, request }, info) => {
    await signIn(page, request);
    await page.goto('/audit');
    await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
    await expect(page.getByRole('row').nth(1)).toBeVisible();
    await expectScreen(page.locator('body'), '5-audit-log', info);
  });

  test('no groups', async ({ page, request }, info) => {
    await signIn(page, request);
    // Spec 11.6: the empty group list is reached by answering GET /groups with 200 [].
    await page.route('**/api/v1/admin/groups', (route) =>
      route.request().method() === 'GET' ? route.fulfill({ json: [] }) : route.fallback(),
    );
    await page.goto('/groups');
    await expect(page.getByRole('button', { name: 'Create your first group' })).toBeVisible();
    await expectScreen(page.locator('body'), '6-no-groups', info);
  });

  test('group without flags', async ({ page, api }, info) => {
    // The screenshot stack has no group without flags and stays read-only, so this screen uses
    // the main stack; its group list depends on other tests, so the main landmark is compared.
    const ui = process.env.UI_URL ?? 'http://localhost:3000';
    const key = keyFor(info, 'aria-empty');
    const group = await api.createGroup(key, 'Empty group', 'No flags yet');
    await page.goto(`${ui}/login`);
    await page.evaluate(([k, t]) => sessionStorage.setItem(k, t), [TOKEN_KEY, api.token] as const);
    await page.goto(`${ui}/groups/${group.id}`);
    await expect(page.getByRole('button', { name: 'Add flag' })).toBeVisible();
    await expectScreen(page.getByRole('main'), '7-group-without-flags', info);
  });

  test('page not found', async ({ page }, info) => {
    await page.goto('/no-such-page');
    await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
    await expectScreen(page.locator('body'), '8-page-not-found', info);
  });

  test('new group dialog', async ({ page, request }, info) => {
    await openOrders(page, request);
    await page.getByRole('button', { name: 'New group' }).click();
    await expect(page.getByRole('dialog', { name: 'New group' })).toBeVisible();
    await expectScreen(page.locator('body'), '9-new-group-dialog', info);
  });

  test('edit group dialog', async ({ page, request }, info) => {
    await openOrders(page, request);
    await page.getByRole('button', { name: 'Edit group' }).click();
    await expect(page.getByRole('dialog', { name: 'Edit group' })).toBeVisible();
    await expectScreen(page.locator('body'), '10-edit-group-dialog', info);
  });

  test('edit flag dialog', async ({ page, request }, info) => {
    await openOrders(page, request);
    await page.getByRole('button', { name: 'Edit orders.new-checkout' }).click();
    await expect(page.getByRole('dialog', { name: 'Edit flag' })).toBeVisible();
    await expectScreen(page.locator('body'), '11-edit-flag-dialog', info);
  });

  test('delete flag dialog', async ({ page, request }, info) => {
    await openOrders(page, request);
    await page.getByRole('button', { name: 'Delete orders.new-checkout' }).click();
    await expect(page.getByRole('alertdialog')).toBeVisible();
    await expectScreen(page.locator('body'), '12-delete-flag-dialog', info);
  });
});
