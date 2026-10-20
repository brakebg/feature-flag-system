/// <reference lib="dom" />
import type { Page } from '@playwright/test';
import { keyFor } from '../support/api';
import { expect, test } from '../support/fixtures';
import { openScreen, SCREENS, screenData } from '../support/screens';

// Spec 005 sections 4.2 to 4.5: the look of the redesign. Expected values are the ones of the
// criteria (docs/project-acceptance-criteria.md, AC-UI-*), not of tokens.css.
const PRIMARY = 'rgb(61, 89, 168)';
const INK = 'rgb(28, 36, 51)';
const DANGER = 'rgb(181, 71, 58)';
const SWITCH_OFF = 'rgb(205, 210, 219)';
const WHITE = 'rgb(255, 255, 255)';

/** Every request of the page, by origin. */
function origins(page: Page): Set<string> {
  const seen = new Set<string>();
  page.on('request', (r) => seen.add(new URL(r.url()).origin));
  return seen;
}

test.describe('[AC-UI-1] ground, text colour, the three Plex fonts, no other origin', () => {
  for (const screen of SCREENS) {
    test(`[AC-UI-1] ${screen}`, async ({ page, api }, info) => {
      const seen = origins(page);
      const data = await screenData(api, keyFor(info, `ui1-${SCREENS.indexOf(screen)}`));
      await openScreen(page, screen, data);

      const body = page.locator('body');
      await expect(body).toHaveCSS('background-color', 'rgb(241, 242, 244)');
      await expect(body).toHaveCSS('color', INK);
      await expect(body).toHaveCSS('font-family', /^"IBM Plex Sans"/);
      const h1s = page.locator('h1');
      expect(await h1s.count()).toBeGreaterThan(0);
      for (const h1 of await h1s.all()) {
        await expect(h1).toHaveCSS('font-family', /^"IBM Plex Sans Condensed"/);
        await expect(h1).toHaveCSS('font-weight', '600');
      }
      if (screen !== 'sign in' && screen !== 'audit log') {
        for (const key of ['first-flag', 'second-flag']) {
          const table = page.getByRole('table', { name: 'Flags' });
          await expect(table.getByText(key, { exact: true })).toHaveCSS(
            'font-family',
            /^"IBM Plex Mono"/,
          );
          await expect(
            table.getByText(`${keyFor(info, `ui1-${SCREENS.indexOf(screen)}`)}.${key}`, {
              exact: true,
            }),
          ).toHaveCSS('font-family', /^"IBM Plex Mono"/);
        }
      }
      const loaded = await page.evaluate(async () => {
        const faces = [
          '600 56px "IBM Plex Sans Condensed"',
          '400 14px "IBM Plex Sans"',
          '500 15px "IBM Plex Mono"',
        ];
        for (const f of faces) await document.fonts.load(f);
        return faces.map((f) => document.fonts.check(f));
      });
      expect(loaded).toEqual([true, true, true]);
      const ui = new URL(page.url()).origin;
      expect([...seen].filter((o) => o !== ui)).toEqual([]);
    });
  }
});

test('[AC-UI-2] primary, dark and danger buttons and the switch colours', async ({
  page,
  api,
}, info) => {
  const key = keyFor(info, 'ui2');
  const data = await screenData(api, key);
  const empty = await api.createGroup(`${key}-e`.slice(0, 50), 'Empty group');

  await openScreen(page, 'sign in', data);
  await expect(page.getByRole('button', { name: 'Sign in' })).toHaveCSS('background-color', INK);

  await openScreen(page, 'flags workspace', data);
  await expect(page.getByRole('button', { name: 'New flag' })).toHaveCSS(
    'background-color',
    PRIMARY,
  );
  await expect(page.getByRole('switch', { name: `Toggle ${key}.first-flag` })).toHaveAttribute(
    'aria-checked',
    'true',
  );
  await expect(page.getByRole('switch', { name: `Toggle ${key}.first-flag` })).toHaveCSS(
    'background-color',
    PRIMARY,
  );
  await expect(page.getByRole('switch', { name: `Toggle ${key}.second-flag` })).toHaveCSS(
    'background-color',
    SWITCH_OFF,
  );

  await page.getByRole('button', { name: 'New flag' }).click();
  await expect(page.getByRole('button', { name: 'Create flag' })).toHaveCSS(
    'background-color',
    PRIMARY,
  );
  await page.getByRole('button', { name: 'Cancel' }).click();

  await page.getByRole('button', { name: 'New group' }).click();
  await expect(page.getByRole('button', { name: 'Create group' })).toHaveCSS(
    'background-color',
    PRIMARY,
  );
  await page.getByRole('button', { name: 'Cancel' }).click();

  await page.getByRole('button', { name: 'Edit group' }).click();
  await expect(page.getByRole('button', { name: 'Save changes' })).toHaveCSS(
    'background-color',
    PRIMARY,
  );
  await page.getByRole('button', { name: 'Cancel' }).click();

  await page.getByRole('button', { name: `Delete ${key}.first-flag` }).click();
  await expect(
    page.getByRole('alertdialog').getByRole('button', { name: 'Delete', exact: true }),
  ).toHaveCSS('background-color', DANGER);
  await page.getByRole('button', { name: 'Cancel' }).click();

  await page.getByRole('button', { name: 'Delete group' }).click();
  await page.getByLabel(`Type ${key} to confirm`).fill(key);
  await expect(page.getByRole('button', { name: 'Delete group and 2 flags' })).toHaveCSS(
    'background-color',
    DANGER,
  );
  await page.getByRole('button', { name: 'Cancel' }).click();

  await page.goto(`/groups/${empty.id}`);
  await expect(page.getByRole('button', { name: 'Add flag' })).toHaveCSS(
    'background-color',
    PRIMARY,
  );

  // 51 audit events of one target key prefix, so `Load more` is shown for that filter.
  const many = await api.createGroup(`${key}-m`.slice(0, 50), 'Many events');
  for (let i = 0; i < 25; i++) {
    const flag = await api.createFlag(many.id, `f${i}`, false);
    await api.toggle(flag.id, true);
  }
  await page.goto('/audit');
  await page.getByLabel('Filter by target key').fill(`${key}-m`.slice(0, 50));
  await expect(page.getByRole('button', { name: 'Load more' })).toHaveCSS('background-color', INK);

  await page.route('**/api/v1/admin/groups', (route) =>
    route.request().method() === 'GET' ? route.fulfill({ json: [] }) : route.fallback(),
  );
  await page.goto('/groups');
  await expect(page.getByRole('button', { name: 'Create your first group' })).toHaveCSS(
    'background-color',
    PRIMARY,
  );
});

test.describe('[AC-UI-3] buttons and search fields are pills or circles', () => {
  for (const screen of SCREENS) {
    test(`[AC-UI-3] ${screen}`, async ({ page, api }, info) => {
      const data = await screenData(api, keyFor(info, `ui3-${SCREENS.indexOf(screen)}`));
      await openScreen(page, screen, data);
      const bad = await page.evaluate(() => {
        const groupList = document.querySelector('nav[aria-label="Flag groups"]');
        const fields = ['Search groups', 'Search flags', 'Filter by target key'];
        const inputs = Array.from(document.querySelectorAll('input')).filter((i) =>
          fields.some((f) => i.labels?.[0]?.textContent === f || i.ariaLabel === f),
        );
        const buttons = Array.from(document.querySelectorAll('button')).filter(
          (b) => !groupList?.contains(b),
        );
        const problems: string[] = [];
        for (const el of [...buttons, ...inputs]) {
          const box = el.getBoundingClientRect();
          if (box.width === 0 || box.height === 0) continue;
          const s = getComputedStyle(el);
          const corners = [
            s.borderTopLeftRadius,
            s.borderTopRightRadius,
            s.borderBottomRightRadius,
            s.borderBottomLeftRadius,
          ];
          const ok = corners.every((c) =>
            c === '50%'
              ? Math.abs(box.width - box.height) < 0.5
              : parseFloat(c) >= box.height / 2 - 0.01,
          );
          if (!ok) problems.push(`${el.tagName} "${el.ariaLabel ?? el.textContent}" ${corners}`);
        }
        return { problems, inputs: inputs.length };
      });
      expect(bad.problems).toEqual([]);
      const expected = { 'flags workspace': 2, 'audit log': 1 } as Record<string, number>;
      if (screen in expected) expect(bad.inputs).toBe(expected[screen]);
    });
  }
});

test.describe('[AC-UI-9] visible 2 px focus ring, white inside the audit header card', () => {
  for (const screen of SCREENS) {
    test(`[AC-UI-9] ${screen}`, async ({ page, api }, info) => {
      const data = await screenData(api, keyFor(info, `ui9-${SCREENS.indexOf(screen)}`));
      await openScreen(page, screen, data);
      const seen: string[] = [];
      for (let i = 0; i < 60; i++) {
        await page.keyboard.press('Tab');
        const focus = await page.evaluate(() => {
          const el = document.activeElement as HTMLElement | null;
          if (!el || el === document.body) return null;
          const label =
            el.getAttribute('aria-label') ??
            (el as HTMLInputElement).labels?.[0]?.textContent ??
            el.textContent ??
            '';
          const name = `${el.tagName}#${el.id} ${label}`;
          const s = getComputedStyle(el);
          // Inside an ink surface: the nearest ancestor with a background is ink (audit header).
          let p = el.parentElement;
          let ground = '';
          while (p) {
            const bg = getComputedStyle(p).backgroundColor;
            if (bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
              ground = bg;
              break;
            }
            p = p.parentElement;
          }
          return {
            name,
            style: s.outlineStyle,
            width: s.outlineWidth,
            offset: s.outlineOffset,
            color: s.outlineColor,
            onInk: ground === 'rgb(28, 36, 51)',
          };
        });
        if (!focus) break;
        if (seen.includes(focus.name)) break;
        seen.push(focus.name);
        expect(focus, focus.name).toMatchObject({
          style: 'solid',
          width: '2px',
          offset: '2px',
          color: focus.onInk ? WHITE : PRIMARY,
        });
      }
      expect(seen.length).toBeGreaterThan(1);
      if (screen === 'audit log') {
        await page.getByLabel('Filter by target key').focus();
        await expect(page.getByLabel('Filter by target key')).toHaveCSS('outline-color', WHITE);
      }
    });
  }
});

/** floor((200 × enabled + total) / (2 × total))%, 0% with no flags (AC-UI-4). */
function expectedShare(enabled: number, total: number): string {
  return total === 0 ? '0%' : `${Math.floor((200 * enabled + total) / (2 * total))}%`;
}

/** Per group button: the badge text and the --share of each aria-hidden element in it. */
async function pies(page: Page) {
  return page
    .getByRole('navigation', { name: 'Flag groups' })
    .getByRole('button')
    .evaluateAll((buttons) =>
      buttons.map((b) => ({
        badge: /^(\d+)\/(\d+)$/.exec(b.lastElementChild?.textContent ?? '')?.slice(1, 3) ?? [],
        shares: Array.from(b.querySelectorAll('[aria-hidden="true"]')).map((h) =>
          getComputedStyle(h).getPropertyValue('--share').trim(),
        ),
      })),
    );
}

test('[AC-UI-4] each group has a hidden pie with the half-up share, also after a toggle', async ({
  signedIn: page,
  api,
}, info) => {
  const key = keyFor(info, 'ui4');
  const group = await api.createGroup(key, 'Pie group');
  const on = await api.createFlag(group.id, 'one', true);
  await api.createFlag(group.id, 'two', true);
  await api.createFlag(group.id, 'three', false);
  await api.createGroup(`${key}-0`.slice(0, 50), 'Pie empty');

  await page.goto(`/groups/${group.id}`);
  await expect(page.getByRole('switch')).toHaveCount(3);
  const before = await pies(page);
  expect(before.length).toBeGreaterThan(1);
  for (const p of before) {
    expect(p.shares).toEqual([expectedShare(Number(p.badge[0]), Number(p.badge[1]))]);
  }
  const mine = page.getByRole('button', { name: new RegExp(`^Pie group ${key} 2/3$`) });
  await expect(mine).toBeVisible();
  const empty = page.getByRole('button', { name: new RegExp(`^Pie empty ${key}-0 0/0$`) });
  await expect(empty.locator('[aria-hidden="true"]')).toHaveCount(1);
  const share = (b: typeof mine) =>
    b
      .locator('[aria-hidden="true"]')
      .evaluate((h) => getComputedStyle(h).getPropertyValue('--share').trim());
  expect(await share(mine)).toBe('67%');
  expect(await share(empty)).toBe('0%');

  await page.getByRole('switch', { name: `Toggle ${on.fullKey}` }).click();
  const after = page.getByRole('button', { name: new RegExp(`^Pie group ${key} 1/3$`) });
  await expect(after).toBeVisible();
  await expect.poll(() => share(after)).toBe('33%');
});

const LABELS: [string, string, string][] = [
  ['Group created', 'rgb(223, 229, 243)', 'rgb(45, 68, 134)'],
  ['Flag created', 'rgb(223, 229, 243)', 'rgb(45, 68, 134)'],
  ['Group updated', 'rgb(236, 238, 241)', 'rgb(61, 69, 86)'],
  ['Flag updated', 'rgb(236, 238, 241)', 'rgb(61, 69, 86)'],
  ['Flag toggled', 'rgb(248, 230, 218)', 'rgb(138, 75, 38)'],
  ['Group deleted', 'rgb(245, 224, 220)', 'rgb(154, 58, 47)'],
  ['Flag deleted', 'rgb(245, 224, 220)', 'rgb(154, 58, 47)'],
];

test('[AC-UI-5] audit labels keep their text, start with a hidden shape and have their colours', async ({
  signedIn: page,
  api,
}, info) => {
  const key = keyFor(info, 'ui5');
  const group = await api.createGroup(key, 'Audit labels');
  const flag = await api.createFlag(group.id, 'labelled', false);
  await api.toggle(flag.id, true);
  const detail = await api.group(group.id);
  await api.patchFlag(flag.id, { description: 'changed', version: detail.flags[0].version });
  await api.patchGroup(group.id, { name: 'Audit labels 2', version: detail.version });
  const gone = await api.createFlag(group.id, 'gone', false);
  await api.deleteFlag(gone.id);
  await api.deleteGroup(group.id);

  await page.goto('/audit');
  await page.getByLabel('Filter by target key').fill(key);
  const rows = page.getByRole('table', { name: 'Audit events' }).getByRole('row');
  // The 8 events of this test; the target filter shows only this key (newest first).
  await expect(rows.filter({ hasText: 'Group deleted' }).first()).toBeVisible();
  await expect(rows.filter({ hasText: 'Group created' }).first()).toBeVisible();
  const labels = await rows.evaluateAll((all) =>
    all.slice(1).map((row) => {
      const label = row.querySelectorAll('[role="cell"]')[2].firstElementChild as HTMLElement;
      const first = label.firstElementChild;
      const s = getComputedStyle(label);
      return {
        text: label.textContent,
        startsWithShape:
          label.firstChild === first &&
          first?.getAttribute('aria-hidden') === 'true' &&
          first.textContent === '',
        bg: s.backgroundColor,
        color: s.color,
      };
    }),
  );
  const seen = new Set(labels.map((l) => l.text));
  expect(seen).toEqual(new Set(LABELS.map(([t]) => t)));
  for (const l of labels) {
    const [, bg, color] = LABELS.find(([t]) => t === l.text) ?? [];
    expect(l).toEqual({ text: l.text, startsWithShape: true, bg, color });
  }
});

test.describe('[AC-UI-6] hidden shapes are never focusable', () => {
  for (const screen of SCREENS) {
    test(`[AC-UI-6] ${screen}`, async ({ page, api }, info) => {
      const data = await screenData(api, keyFor(info, `ui6-${SCREENS.indexOf(screen)}`));
      await openScreen(page, screen, data);
      const result = await page.evaluate(() => {
        const focusable =
          'a[href], button, input, select, textarea, iframe, summary, [tabindex], [contenteditable]';
        const hidden = Array.from(document.querySelectorAll('[aria-hidden="true"]'));
        return {
          count: hidden.length,
          bad: hidden
            .filter(
              (h) =>
                h.matches(focusable) || h.hasAttribute('tabindex') || !!h.querySelector(focusable),
            )
            .map((h) => h.outerHTML.slice(0, 120)),
        };
      });
      expect(result.count).toBeGreaterThan(0);
      expect(result.bad).toEqual([]);
    });
  }
});
