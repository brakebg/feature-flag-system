import { API_URL, clientToken, keyFor } from '../support/api';
import { expect, test } from '../support/fixtures';

const badge = (page: import('@playwright/test').Page, key: string) =>
  page
    .getByRole('navigation', { name: 'Flag groups' })
    .getByRole('button', { name: new RegExp(key) })
    .getByText(/^\d+\/\d+$/);

test.describe('flags (spec 8.4, 8.5)', () => {
  test('[AC-FLAG-1] add flag new-checkout, initially off, full key shown @cross-browser', async ({
    signedIn: page,
    api,
  }, info) => {
    const key = keyFor(info, 'orders');
    const group = await api.createGroup(key, 'Orders');
    await page.goto(`/groups/${group.id}`);
    await page.getByRole('button', { name: 'New flag' }).click();
    const dialog = page.getByRole('dialog', { name: 'New flag in Orders' });
    await expect(dialog.getByRole('switch', { name: 'Initial state' })).toHaveAttribute(
      'aria-checked',
      'false',
    );
    await dialog.getByLabel('Key').fill('new-checkout');
    await dialog.getByRole('button', { name: 'Create flag' }).click();

    await expect(page.getByRole('status')).toHaveText(`Flag ${key}.new-checkout created`);
    await expect(page.getByText(`${key}.new-checkout`, { exact: true })).toBeVisible();
    await expect(page.getByRole('switch', { name: `Toggle ${key}.new-checkout` })).toHaveAttribute(
      'aria-checked',
      'false',
    );
  });

  test('[AC-FLAG-2] the same flag key in two groups', async ({ signedIn: page, api }, info) => {
    const a = await api.createGroup(keyFor(info, 'a'), 'Group A');
    const b = await api.createGroup(keyFor(info, 'b'), 'Group B');
    await api.createFlag(a.id, 'same-key');
    await page.goto(`/groups/${b.id}`);
    await page.getByRole('button', { name: 'New flag' }).click();
    const dialog = page.getByRole('dialog', { name: 'New flag in Group B' });
    await dialog.getByLabel('Key').fill('same-key');
    await dialog.getByRole('button', { name: 'Create flag' }).click();
    await expect(page.getByText(`${b.key}.same-key`, { exact: true })).toBeVisible();
    await page.goto(`/groups/${a.id}`);
    await expect(page.getByText(`${a.key}.same-key`, { exact: true })).toBeVisible();
  });

  test('[AC-FLAG-3] [AC-EVAL-5] toggle: switch at once, badge, persists after reload, next evaluation is new @cross-browser', async ({
    signedIn: page,
    api,
    request,
  }, info) => {
    const key = keyFor(info, 'tgl');
    const group = await api.createGroup(key, 'Toggle');
    await api.createFlag(group.id, 'feature', false);
    await api.createFlag(group.id, 'other', false);
    await page.goto(`/groups/${group.id}`);
    const sw = page.getByRole('switch', { name: `Toggle ${key}.feature` });
    await expect(badge(page, key)).toHaveText('0/2');

    // Hold the response so "immediately" is observable before the server answers.
    let release!: () => void;
    const held = new Promise<void>((r) => (release = r));
    await page.route('**/api/v1/admin/flags/*/toggle', async (route) => {
      await held;
      await route.continue();
    });
    await sw.click();
    await expect(sw).toHaveAttribute('aria-checked', 'true');
    await expect(badge(page, key)).toHaveText('1/2');
    const done = page.waitForResponse((r) => r.url().endsWith('/toggle'));
    release();
    expect((await done).status()).toBe(200);
    await page.unroute('**/api/v1/admin/flags/*/toggle');

    await page.reload();
    await expect(page.getByRole('switch', { name: `Toggle ${key}.feature` })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    await expect(badge(page, key)).toHaveText('1/2');

    const res = await request.get(`${API_URL}/api/v1/evaluate/flags/${key}/feature`, {
      headers: { Authorization: `Bearer ${await clientToken(request)}` },
    });
    expect(res.status()).toBe(200);
    expect(await res.json()).toEqual({ key: `${key}.feature`, enabled: true });
  });

  test('[AC-FLAG-4] a failed toggle reverts the switch and shows the error toast', async ({
    signedIn: page,
    api,
  }, info) => {
    const key = keyFor(info, 'fail');
    const group = await api.createGroup(key, 'Fail');
    await api.createFlag(group.id, 'feature', false);
    await page.goto(`/groups/${group.id}`);
    await page.route('**/api/v1/admin/flags/*/toggle', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/problem+json',
        body: JSON.stringify({ type: 'https://featureflags.local/problems/internal', status: 500 }),
      }),
    );
    const sw = page.getByRole('switch', { name: `Toggle ${key}.feature` });
    await sw.click();
    await expect(page.getByRole('alert')).toHaveText(`Could not update flag ${key}.feature`);
    await expect(sw).toHaveAttribute('aria-checked', 'false');
    await expect(badge(page, key)).toHaveText('0/1');
  });

  test('[AC-FLAG-5] deleting one flag removes only that flag', async ({
    signedIn: page,
    api,
  }, info) => {
    const key = keyFor(info, 'del');
    const group = await api.createGroup(key, 'Delete one');
    await api.createFlag(group.id, 'a-flag', true);
    await api.createFlag(group.id, 'b-flag', false);
    await page.goto(`/groups/${group.id}`);
    await page.getByRole('button', { name: `Delete ${key}.a-flag` }).click();
    const dialog = page.getByRole('alertdialog', { name: `Delete flag ${key}.a-flag?` });
    await dialog.getByRole('button', { name: 'Delete' }).click();
    await expect(page.getByRole('status')).toHaveText(`Flag ${key}.a-flag deleted`);
    await expect(page.getByText(`${key}.a-flag`, { exact: true })).toHaveCount(0);
    await expect(page.getByText(`${key}.b-flag`, { exact: true })).toBeVisible();
    const detail = await api.group(group.id);
    expect(detail.flags.map((f) => f.key)).toEqual(['b-flag']);
  });

  test('[AC-FLAG-6] editing with a stale version: 409, toast, and the UI refetches', async ({
    signedIn: page,
    api,
    request,
  }, info) => {
    const key = keyFor(info, 'stale');
    const group = await api.createGroup(key, 'Stale');
    const flag = await api.createFlag(group.id, 'feature', false, 'Original');
    await page.goto(`/groups/${group.id}`);
    await page.getByRole('button', { name: `Edit ${key}.feature` }).click();
    const dialog = page.getByRole('dialog', { name: 'Edit flag' });
    // Someone else changes the flag while the dialog is open.
    const other = await request.patch(`${API_URL}/api/v1/admin/flags/${flag.id}`, {
      headers: { Authorization: `Bearer ${api.token}` },
      data: { description: 'Changed elsewhere', version: 0 },
    });
    expect(other.status()).toBe(200);
    await dialog.getByLabel('Description (optional)').fill('Mine');
    const conflict = page.waitForResponse(
      (r) => r.request().method() === 'PATCH' && r.url().includes(`/flags/${flag.id}`),
    );
    await dialog.getByRole('button', { name: 'Save changes' }).click();
    expect((await conflict).status()).toBe(409);
    await expect(page.getByRole('alert')).toHaveText('This item was changed by someone else');
    await expect(dialog).toHaveCount(0);
    await expect(page.getByText('Changed elsewhere', { exact: true })).toBeVisible();
  });
});
