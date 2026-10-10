import { keyFor } from '../support/api';
import { expect, test } from '../support/fixtures';

test.describe('audit log (spec 8.6)', () => {
  test('[AC-AUD-2] lists events newest first and filters by target key prefix @cross-browser', async ({
    signedIn: page,
    api,
  }, info) => {
    const key = keyFor(info, 'aud');
    const group = await api.createGroup(key, 'Audited');
    const flag = await api.createFlag(group.id, 'feature', false);
    await api.toggle(flag.id, true);

    await page.goto('/audit');
    await page.getByLabel('Filter by target key').fill(`${key}.`);
    const rows = page.getByRole('table', { name: 'Audit events' }).getByRole('row');
    // Header row + FLAG_TOGGLED + FLAG_CREATED (the group event has target key `key`, no dot).
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(1)).toContainText('Flag toggled');
    await expect(rows.nth(1)).toContainText(`${key}.feature`);
    await expect(rows.nth(1)).toContainText('false → true');
    await expect(rows.nth(2)).toContainText('Flag created');

    await page.getByLabel('Filter by target key').fill(key);
    await expect(rows).toHaveCount(4);
    await expect(rows.nth(3)).toContainText('Group created');
  });
});
