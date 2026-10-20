import type { Page } from '@playwright/test';
import { expect } from './fixtures';
import type { AdminApi } from './api';
import { TOKEN_KEY } from './fixtures';

/**
 * "The screens" of docs/project-acceptance-criteria.md ("UI redesign"): the five design screens
 * of docs/product/design/README.md plus the empty states, the 404 page and every dialog.
 */
export const SCREENS = [
  'sign in',
  'flags workspace',
  'new flag dialog',
  'delete group confirmation',
  'audit log',
  'no groups',
  'group without flags',
  'page not found',
  'new group dialog',
  'edit group dialog',
  'edit flag dialog',
  'delete flag dialog',
] as const;
export type Screen = (typeof SCREENS)[number];

export interface ScreenData {
  token: string;
  groupId: string;
  groupName: string;
  groupKey: string;
  emptyGroupId: string;
}

/** A group with one flag on and one off, as on the design screens, and a group without flags. */
export async function screenData(api: AdminApi, key: string): Promise<ScreenData> {
  const name = `Group ${key}`;
  const group = await api.createGroup(key, name, 'Screens of spec 005');
  await api.createFlag(group.id, 'first-flag', true, 'The first flag');
  await api.createFlag(group.id, 'second-flag', false, 'The second flag');
  const empty = await api.createGroup(`${key}-e`.slice(0, 50), `Empty ${key}`);
  return {
    token: api.token,
    groupId: group.id,
    groupName: name,
    groupKey: key,
    emptyGroupId: empty.id,
  };
}

/** Opens a screen in the state the design shows. */
export async function openScreen(page: Page, screen: Screen, data: ScreenData): Promise<void> {
  await page.goto('/login');
  if (screen === 'sign in') {
    await page.evaluate((k) => sessionStorage.removeItem(k), TOKEN_KEY);
    await page.goto('/login?expired=1');
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    return;
  }
  await page.evaluate(([k, t]) => sessionStorage.setItem(k, t), [TOKEN_KEY, data.token] as const);
  switch (screen) {
    case 'audit log':
      await page.goto('/audit');
      await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
      await expect(page.getByRole('row').nth(1)).toBeVisible();
      return;
    case 'no groups':
      // Spec 11.6: the empty group list is reached by answering GET /groups with 200 [].
      await page.route('**/api/v1/admin/groups', (route) =>
        route.request().method() === 'GET' ? route.fulfill({ json: [] }) : route.fallback(),
      );
      await page.goto('/groups');
      await expect(page.getByRole('button', { name: 'Create your first group' })).toBeVisible();
      return;
    case 'group without flags':
      await page.goto(`/groups/${data.emptyGroupId}`);
      await expect(page.getByRole('button', { name: 'Add flag' })).toBeVisible();
      return;
    case 'page not found':
      await page.goto('/no-such-page');
      await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
      return;
  }
  await page.goto(`/groups/${data.groupId}`);
  await expect(page.getByRole('heading', { name: data.groupName })).toBeVisible();
  await expect(page.getByRole('switch')).toHaveCount(2);
  const flag = `${data.groupKey}.first-flag`;
  switch (screen) {
    case 'new flag dialog':
      await page.getByRole('button', { name: 'New flag' }).click();
      await expect(
        page.getByRole('dialog', { name: `New flag in ${data.groupName}` }),
      ).toBeVisible();
      return;
    case 'delete group confirmation':
      await page.getByRole('button', { name: 'Delete group' }).click();
      await expect(page.getByRole('alertdialog')).toBeVisible();
      return;
    case 'new group dialog':
      await page.getByRole('button', { name: 'New group' }).click();
      await expect(page.getByRole('dialog', { name: 'New group' })).toBeVisible();
      return;
    case 'edit group dialog':
      await page.getByRole('button', { name: 'Edit group' }).click();
      await expect(page.getByRole('dialog', { name: 'Edit group' })).toBeVisible();
      return;
    case 'edit flag dialog':
      await page.getByRole('button', { name: `Edit ${flag}` }).click();
      await expect(page.getByRole('dialog', { name: 'Edit flag' })).toBeVisible();
      return;
    case 'delete flag dialog':
      await page.getByRole('button', { name: `Delete ${flag}` }).click();
      await expect(page.getByRole('alertdialog', { name: `Delete flag ${flag}?` })).toBeVisible();
      return;
  }
}
