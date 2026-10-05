import { expect, test } from './support/fixtures';
import { keyFor } from './support/api';

// Spec 11.6 "Matching the design" 1: key design values, taken from docs/design (not tokens.css).
const PRIMARY = 'rgb(35, 80, 200)'; // #2350C8
const TOP_BAR = 'rgb(21, 24, 29)'; // #15181D

test.describe('design values (spec 11.6)', () => {
  test('sign-in page: primary button, fonts and 44 px inputs', async ({ page }) => {
    await page.goto('/login');
    const button = page.getByRole('button', { name: 'Sign in' });
    await expect(button).toHaveCSS('background-color', PRIMARY);
    await expect(page.locator('body')).toHaveCSS('font-family', /^"IBM Plex Sans"/);
    await expect(page.getByLabel('Username')).toHaveCSS('height', '44px');
    await expect(page.getByLabel('Password')).toHaveCSS('height', '44px');
    await expect(page.getByLabel('Username')).toHaveCSS('border-radius', '6px');
  });

  test('workspace: top bar, monospace keys and the switch colour when on', async ({
    signedIn: page,
    api,
  }, info) => {
    const group = await api.createGroup(keyFor(info, 'style'), 'Style check');
    const flag = await api.createFlag(group.id, 'on-flag', true);
    await page.goto(`/groups/${group.id}`);
    await expect(page.locator('header').first()).toHaveCSS('background-color', TOP_BAR);
    await expect(page.locator('header').first()).toHaveCSS('height', '60px');
    await expect(page.getByText(flag.fullKey)).toHaveCSS('font-family', /^"IBM Plex Mono"/);
    const sw = page.getByRole('switch', { name: `Toggle ${flag.fullKey}` });
    await expect(sw).toHaveCSS('background-color', PRIMARY);
    await expect(page.getByRole('button', { name: 'New flag' })).toHaveCSS(
      'background-color',
      PRIMARY,
    );
  });
});
