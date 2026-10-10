import { test as base, expect, type Page } from '@playwright/test';
import { adminApi, type AdminApi } from './api';

export const TOKEN_KEY = 'ff.accessToken';

interface Fixtures {
  api: AdminApi;
  cleanup: { groups: string[] };
  signedIn: Page;
}

/**
 * Spec 11.5, 11.3 gate 12: per-test data with cleanup in afterEach, and any Content-Security-Policy
 * violation in the browser console fails the test.
 */
export const test = base.extend<Fixtures>({
  page: async ({ page }, provide) => {
    const csp: string[] = [];
    page.on('console', (msg) => {
      if (/Content Security Policy|Content-Security-Policy/i.test(msg.text())) csp.push(msg.text());
    });
    await provide(page);
    expect(csp, 'Content-Security-Policy violations').toEqual([]);
  },
  cleanup: async ({ request }, provide) => {
    const data = { groups: [] as string[] };
    await provide(data);
    if (data.groups.length) {
      const api = await adminApi(request);
      for (const id of data.groups) await api.deleteGroup(id);
    }
  },
  api: async ({ request, cleanup }, provide) => {
    const api = await adminApi(request);
    const createGroup = api.createGroup.bind(api);
    api.createGroup = async (key, name, description) => {
      const g = await createGroup(key, name, description);
      cleanup.groups.push(g.id);
      return g;
    };
    await provide(api);
  },
  signedIn: async ({ page, api }, provide) => {
    await page.goto('/login');
    await page.evaluate(([k, t]) => sessionStorage.setItem(k, t), [TOKEN_KEY, api.token] as const);
    await provide(page);
  },
});

export { expect };
