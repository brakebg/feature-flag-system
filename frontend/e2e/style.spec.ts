/// <reference lib="dom" />
import { expect, test } from './support/fixtures';
import { keyFor } from './support/api';

// Spec 11.6 "Matching the design" 1 with the spec 005 values (AC-STYLE-1 v2), taken from
// docs/product/design (not tokens.css).
const PRIMARY = 'rgb(61, 89, 168)'; // #3D59A8
const INK = 'rgb(28, 36, 51)'; // #1C2433
const WHITE = 'rgb(255, 255, 255)'; // #FFFFFF

test.describe('[AC-STYLE-1] design values (spec 11.6, spec 005)', () => {
  test('sign-in page: dark Sign in button, fonts and 52 px inputs with a 16 px radius', async ({
    page,
  }) => {
    await page.goto('/login');
    const button = page.getByRole('button', { name: 'Sign in' });
    await expect(button).toHaveCSS('background-color', INK);
    await expect(page.locator('body')).toHaveCSS('font-family', /^"IBM Plex Sans"/);
    await expect(page.getByLabel('Username')).toHaveCSS('height', '52px');
    await expect(page.getByLabel('Password')).toHaveCSS('height', '52px');
    await expect(page.getByLabel('Username')).toHaveCSS('border-radius', '16px');
    await expect(page.getByLabel('Password')).toHaveCSS('border-radius', '16px');
  });

  test('workspace: white pill top bar, monospace keys and the primary colour', async ({
    signedIn: page,
    api,
  }, info) => {
    const group = await api.createGroup(keyFor(info, 'style'), 'Style check');
    const flag = await api.createFlag(group.id, 'on-flag', true);
    await page.goto(`/groups/${group.id}`);
    const bar = page.locator('header').first();
    await expect(bar).toHaveCSS('background-color', WHITE);
    const height = (await bar.boundingBox())?.height ?? 0;
    expect(height).toBeGreaterThanOrEqual(64);
    const radius = await bar.evaluate((el) => {
      const s = getComputedStyle(el);
      const corners = [
        s.borderTopLeftRadius,
        s.borderTopRightRadius,
        s.borderBottomRightRadius,
        s.borderBottomLeftRadius,
      ];
      return Math.min(...corners.map((c) => parseFloat(c)));
    });
    expect(radius).toBeGreaterThanOrEqual(height / 2);
    await expect(page.getByText(flag.fullKey)).toHaveCSS('font-family', /^"IBM Plex Mono"/);
    const sw = page.getByRole('switch', { name: `Toggle ${flag.fullKey}` });
    await expect(sw).toHaveCSS('background-color', PRIMARY);
    await expect(page.getByRole('button', { name: 'New flag' })).toHaveCSS(
      'background-color',
      PRIMARY,
    );
  });
});
