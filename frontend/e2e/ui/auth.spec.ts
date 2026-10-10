import { keyFor } from '../support/api';
import { expect, test, TOKEN_KEY } from '../support/fixtures';
import { expiredAdminToken, tamperedToken } from '../support/jwt';

test.describe('authentication (spec 8.2, 8.3)', () => {
  test('[AC-AUTH-1] wrong credentials show the error and stay on /login', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Username').fill('admin');
    await page.getByLabel('Password').fill('wrong-password');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByText('Invalid username or password', { exact: true })).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate((k) => sessionStorage.getItem(k), TOKEN_KEY)).toBeNull();
  });

  test('[AC-AUTH-2] admin / admin123 signs in and lands on /groups @cross-browser', async ({
    page,
  }) => {
    await page.goto('/login');
    await page.getByLabel('Username').fill('admin');
    await page.getByLabel('Password').fill('admin123');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/groups$/);
    await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
    const token = await page.evaluate((k) => sessionStorage.getItem(k), TOKEN_KEY);
    expect(token?.split('.')).toHaveLength(3);
  });

  test('[AC-AUTH-3] without a token /groups redirects to /login', async ({ page }) => {
    await page.goto('/groups');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  });

  test('[AC-AUTH-4] an expired token leads to /login?expired=1 @cross-browser', async ({
    page,
  }) => {
    await page.goto('/login');
    await page.evaluate(([k, t]) => sessionStorage.setItem(k, t), [TOKEN_KEY, expiredAdminToken()]);
    await page.goto('/groups');
    await expect(page).toHaveURL(/\/login\?expired=1$/);
    await expect(page.getByText('Your session expired. Please sign in again.')).toBeVisible();
    expect(await page.evaluate((k) => sessionStorage.getItem(k), TOKEN_KEY)).toBeNull();
  });

  test('[AC-AUTH-4] a 401 from an admin call (token with a changed signature) leads to /login?expired=1', async ({
    page,
    api,
  }) => {
    await page.goto('/login');
    await page.evaluate(
      ([k, t]) => sessionStorage.setItem(k, t),
      [TOKEN_KEY, tamperedToken(api.token)],
    );
    const unauthorized = page.waitForResponse(
      (r) => r.url().includes('/api/v1/admin/') && r.status() === 401,
    );
    await page.goto('/groups');
    await unauthorized;
    await expect(page).toHaveURL(/\/login\?expired=1$/);
    expect(await page.evaluate((k) => sessionStorage.getItem(k), TOKEN_KEY)).toBeNull();
  });

  test('[AC-AUTH-6] sign out clears the token; Back does not show protected data', async ({
    signedIn: page,
    api,
  }, info) => {
    const key = keyFor(info, 'secret');
    const group = await api.createGroup(key, 'Secret group');
    await page.goto(`/groups/${group.id}`);
    await expect(page.getByRole('heading', { name: 'Secret group' })).toBeVisible();

    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate((k) => sessionStorage.getItem(k), TOKEN_KEY)).toBeNull();

    const adminRequests: string[] = [];
    page.on('request', (r) => {
      if (r.url().includes('/api/v1/admin/')) adminRequests.push(r.url());
    });
    await page.goBack();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await expect(page.getByText('Secret group')).toHaveCount(0);
    expect(adminRequests).toEqual([]);
  });

  test('[AC-AUTH-6] sign in, sign out at once, Back shows the login page again (8.3)', async ({
    page,
  }) => {
    // Spec 8.3: "Going Back afterwards shows the login page again", also when the app has no
    // other history entry (login replaces /login with /groups).
    await page.goto('/login');
    await page.getByLabel('Username').fill('admin');
    await page.getByLabel('Password').fill('admin123');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/groups$/);

    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login$/);

    const adminRequests: string[] = [];
    page.on('request', (r) => {
      if (r.url().includes('/api/v1/admin/')) adminRequests.push(r.url());
    });
    await page.goBack();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign out' })).toHaveCount(0);
    expect(adminRequests).toEqual([]);
  });
});
