import type { Page } from '@playwright/test';
import { expect } from './fixtures';
import type { AdminApi } from './api';
import { TOKEN_KEY } from './fixtures';

/** The five screens of docs/product/design/README.md (spec 005 4.1). */
export const SCREENS = [
  'sign in',
  'flags workspace',
  'new flag dialog',
  'delete group confirmation',
  'audit log',
] as const;
export type Screen = (typeof SCREENS)[number];

export interface ScreenData {
  token: string;
  groupId: string;
  groupName: string;
}

/** A group with one flag on and one off, as on the design screens. */
export async function screenData(api: AdminApi, key: string): Promise<ScreenData> {
  const name = `Group ${key}`;
  const group = await api.createGroup(key, name, 'Screens of spec 005');
  await api.createFlag(group.id, 'first-flag', true);
  await api.createFlag(group.id, 'second-flag', false);
  return { token: api.token, groupId: group.id, groupName: name };
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
  if (screen === 'audit log') {
    await page.goto('/audit');
    await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
    await expect(page.getByRole('row').nth(1)).toBeVisible();
    return;
  }
  await page.goto(`/groups/${data.groupId}`);
  await expect(page.getByRole('heading', { name: data.groupName })).toBeVisible();
  await expect(page.getByRole('switch')).toHaveCount(2);
  if (screen === 'new flag dialog') {
    await page.getByRole('button', { name: 'New flag' }).click();
    await expect(page.getByRole('dialog', { name: `New flag in ${data.groupName}` })).toBeVisible();
  } else if (screen === 'delete group confirmation') {
    await page.getByRole('button', { name: 'Delete group' }).click();
    await expect(page.getByRole('alertdialog')).toBeVisible();
  }
}
