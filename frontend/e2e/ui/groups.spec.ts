import { keyFor, clientToken, API_URL } from '../support/api';
import { expect, test } from '../support/fixtures';

const KEY_TEXT = 'Use 2 to 50 lowercase letters, digits or hyphens, starting with a letter';

test.describe('groups (spec 8.4, 8.5)', () => {
  test('[AC-GRP-1] create a group: selected, in the list with badge 0/0', async ({
    signedIn: page,
    api,
    cleanup,
  }, info) => {
    const key = keyFor(info, 'orders');
    await page.goto('/groups');
    await page.getByRole('button', { name: 'New group' }).click();
    const dialog = page.getByRole('dialog', { name: 'New group' });
    await dialog.getByLabel('Name').fill('Orders');
    await dialog.getByLabel('Key').fill(key);
    await dialog.getByRole('button', { name: 'Create group' }).click();

    await expect(page.getByRole('status')).toHaveText(`Group ${key} created`);
    const created = (await api.groups()).find((g) => g.key === key);
    if (!created) throw new Error(`group ${key} was not created`);
    cleanup.groups.push(created.id);
    await expect(page).toHaveURL(new RegExp(`/groups/${created.id}$`));
    const item = page
      .getByRole('navigation', { name: 'Flag groups' })
      .getByRole('button', { name: new RegExp(key) });
    await expect(item).toContainText('0/0');
    await expect(item).toHaveAttribute('aria-current', 'true');
  });

  test('[AC-GRP-2] a second group with the same key shows "Key already exists"', async ({
    signedIn: page,
    api,
  }, info) => {
    const key = keyFor(info, 'dup');
    await api.createGroup(key, 'First');
    await page.goto('/groups');
    await page.getByRole('button', { name: 'New group' }).click();
    const dialog = page.getByRole('dialog', { name: 'New group' });
    await dialog.getByLabel('Name').fill('Second');
    await dialog.getByLabel('Key').fill(key);
    await dialog.getByRole('button', { name: 'Create group' }).click();
    const keyField = dialog.getByLabel('Key');
    await expect(keyField).toHaveAttribute('aria-invalid', 'true');
    await expect(keyField).toHaveAccessibleDescription(/^Key already exists/);
    await expect(dialog.getByText('Key already exists', { exact: true })).toBeVisible();
  });

  for (const bad of ['Orders', '1abc', 'a', 'has space']) {
    test(`[AC-GRP-3] key "${bad}" is rejected in the UI and by the API`, async ({
      signedIn: page,
      request,
      api,
    }) => {
      const posts: string[] = [];
      page.on('request', (r) => {
        if (r.method() === 'POST' && r.url().endsWith('/api/v1/admin/groups')) posts.push(r.url());
      });
      await page.goto('/groups');
      await page.getByRole('button', { name: 'New group' }).click();
      const dialog = page.getByRole('dialog', { name: 'New group' });
      await dialog.getByLabel('Name').fill('Some name');
      await dialog.getByLabel('Key').fill(bad);
      await dialog.getByRole('button', { name: 'Create group' }).click();
      const keyField = dialog.getByLabel('Key');
      await expect(keyField).toHaveAttribute('aria-invalid', 'true');
      await expect(dialog.getByText(KEY_TEXT, { exact: true })).toBeVisible();
      expect(posts).toEqual([]);

      const res = await request.post(`${API_URL}/api/v1/admin/groups`, {
        headers: { Authorization: `Bearer ${api.token}` },
        data: { key: bad, name: 'Some name' },
      });
      expect(res.status()).toBe(400);
      const body = await res.json();
      expect(body.type).toBe('https://featureflags.local/problems/validation');
      expect(body.errors).toContainEqual(expect.objectContaining({ field: 'key' }));
    });
  }

  test('[AC-GRP-4] edit name and description; the key is read-only', async ({
    signedIn: page,
    api,
  }, info) => {
    const key = keyFor(info, 'edit');
    const group = await api.createGroup(key, 'Before', 'Old text');
    await page.goto(`/groups/${group.id}`);
    await page.getByRole('button', { name: 'Edit group' }).click();
    const dialog = page.getByRole('dialog', { name: 'Edit group' });
    await expect(dialog.getByLabel('Key')).toHaveValue(key);
    await expect(dialog.getByLabel('Key')).toHaveAttribute('readonly', '');
    await dialog.getByLabel('Name').fill('After');
    await dialog.getByLabel('Description (optional)').fill('New text');
    await dialog.getByRole('button', { name: 'Save changes' }).click();
    await expect(page.getByRole('heading', { name: 'After' })).toBeVisible();
    await expect(page.getByText('New text · 0 of 0 flags on')).toBeVisible();
    await expect(page.getByRole('status')).toHaveText(`Group ${key} updated`);
  });

  test('[AC-GRP-5] delete a group with 2 flags: typed key, then gone from UI, Admin API, Evaluation API; one audit event @cross-browser', async ({
    signedIn: page,
    api,
    request,
  }, info) => {
    const key = keyFor(info, 'del');
    const group = await api.createGroup(key, 'Doomed');
    await api.createFlag(group.id, 'new-checkout', true);
    await api.createFlag(group.id, 'split-payments', false);
    await page.goto(`/groups/${group.id}`);
    await page.getByRole('button', { name: 'Delete group' }).click();
    const dialog = page.getByRole('alertdialog', { name: 'Delete group “Doomed”?' });
    const confirm = dialog.getByRole('button', { name: 'Delete group and 2 flags' });
    await expect(confirm).toBeDisabled();
    await dialog.getByLabel(`Type ${key} to confirm`).fill(key.toUpperCase());
    await expect(confirm).toBeDisabled();
    await dialog.getByLabel(`Type ${key} to confirm`).fill(key);
    await confirm.click();

    await expect(page).toHaveURL(/\/groups$/);
    await expect(page.getByRole('status')).toHaveText(`Group ${key} and 2 flags deleted`);
    await expect(page.getByRole('navigation', { name: 'Flag groups' }).getByText(key)).toHaveCount(
      0,
    );

    const auth = { Authorization: `Bearer ${api.token}` };
    expect(
      (await request.get(`${API_URL}/api/v1/admin/groups/${group.id}`, { headers: auth })).status(),
    ).toBe(404);
    const client = { Authorization: `Bearer ${await clientToken(request)}` };
    expect(
      (await request.get(`${API_URL}/api/v1/evaluate/groups/${key}`, { headers: client })).status(),
    ).toBe(404);
    for (const flag of ['new-checkout', 'split-payments']) {
      const res = await request.get(`${API_URL}/api/v1/evaluate/flags/${key}/${flag}`, {
        headers: client,
      });
      expect(res.status()).toBe(404);
    }
    const audit = await request.get(`${API_URL}/api/v1/admin/audit?targetKey=${key}&size=50`, {
      headers: auth,
    });
    const deleted = (
      (await audit.json()).content as {
        action: string;
        targetKey: string;
        details: { deletedFlags: string[] };
      }[]
    ).filter((e) => e.action === 'GROUP_DELETED' && e.targetKey === key);
    expect(deleted).toHaveLength(1);
    expect(deleted[0].details.deletedFlags).toEqual([
      `${key}.new-checkout`,
      `${key}.split-payments`,
    ]);
  });
});
