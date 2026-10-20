/// <reference lib="dom" />
import type { Page } from '@playwright/test';
import { keyFor } from '../support/api';
import { expect, test } from '../support/fixtures';
import { openScreen, SCREENS, screenData } from '../support/screens';

// Spec 005 4.6: layout from 768 px to 1440 px (AC-UI-7) and long data (AC-UI-8).

/** Elements that scroll sideways: the page, the tables and their parents. */
async function sideways(page: Page) {
  return page.evaluate(() => {
    const out: string[] = [];
    const de = document.documentElement;
    if (de.scrollWidth > window.innerWidth)
      out.push(`page ${de.scrollWidth} > ${window.innerWidth}`);
    for (const t of Array.from(document.querySelectorAll('[role="table"]'))) {
      for (const el of [t, t.parentElement].filter((e): e is HTMLElement => !!e)) {
        if (el.scrollWidth > el.clientWidth)
          out.push(
            `${el.getAttribute('aria-label') ?? el.tagName} ${el.scrollWidth} > ${el.clientWidth}`,
          );
      }
    }
    return out;
  });
}

const WIDTHS = [768, 800, 1024, 1440];

/** Screens that show the Edit and Delete buttons of the screen data flags. */
const ROW_BUTTONS = new Set<string>([
  'flags workspace',
  'new flag dialog',
  'delete group confirmation',
  'new group dialog',
  'edit group dialog',
  'edit flag dialog',
  'delete flag dialog',
]);

test.describe('[AC-UI-7] 768 to 1440 px: no sideways scroll, row actions visible', () => {
  for (const width of WIDTHS) {
    for (const screen of SCREENS) {
      test(`[AC-UI-7] ${width} px ${screen}`, async ({ page, api }, info) => {
        await page.setViewportSize({ width, height: 900 });
        const key = keyFor(info, `ui7-${width}-${SCREENS.indexOf(screen)}`);
        const data = await screenData(api, key);
        await openScreen(page, screen, data);
        expect(await sideways(page)).toEqual([]);

        if (screen === 'sign in') {
          const art = await page.evaluate(
            () =>
              Array.from(document.querySelectorAll('[aria-hidden="true"]')).filter(
                (h) => h.getBoundingClientRect().width > 300,
              ).length,
          );
          expect(art).toBe(width < 860 ? 0 : 1);
          return;
        }
        if (screen === 'audit log') {
          const table = page.getByRole('table', { name: 'Audit events' });
          for (const name of ['Action', 'Target', 'Details'])
            await expect(table.getByRole('columnheader', { name })).toBeVisible();
          for (const name of ['Time', 'Actor']) {
            const head = table.getByRole('columnheader', { name });
            if (width < 860) await expect(head).toBeHidden();
            else await expect(head).toBeVisible();
          }
          // Hidden cells leave the accessibility tree, so they are found by their role attribute.
          const cells = table.getByRole('row').nth(1).locator('[role="cell"]');
          for (const i of [0, 1]) {
            if (width < 860) await expect(cells.nth(i)).toBeHidden();
            else await expect(cells.nth(i)).toBeVisible();
          }
          return;
        }
        if (screen === 'flags workspace' || screen === 'group without flags') {
          const panel = await page.getByRole('complementary', { name: 'Groups' }).boundingBox();
          const right = await page.getByRole('main').boundingBox();
          expect(panel, 'Groups panel').not.toBeNull();
          expect(right, 'main area').not.toBeNull();
          if (panel && right && width <= 1024) {
            expect(panel.y + panel.height).toBeLessThanOrEqual(right.y);
          }
        }
        if (screen === 'flags workspace') {
          const table = page.getByRole('table', { name: 'Flags' });
          for (const name of ['Key', 'Description', 'Status', 'Actions'])
            await expect(table.getByRole('columnheader', { name })).toBeVisible();
          for (const name of ['Created by', 'Updated']) {
            const head = table.getByRole('columnheader', { name });
            if (width < 860) await expect(head).toBeHidden();
            else await expect(head).toBeVisible();
          }
          // Cells 3 and 4 of each row are Created by and Updated (TA-3); hidden cells leave the
          // accessibility tree, so they are found by their role attribute.
          const rows = table.locator('[role="row"]');
          await expect(rows).toHaveCount(3);
          for (const r of [1, 2]) {
            const cells = rows.nth(r).locator('[role="cell"]');
            await expect(cells).toHaveCount(6);
            for (const i of [0, 1, 2, 5]) await expect(cells.nth(i)).toBeVisible();
            for (const i of [3, 4]) {
              if (width < 860) await expect(cells.nth(i)).toBeHidden();
              else await expect(cells.nth(i)).toBeVisible();
            }
          }
          const tableBox = await table.boundingBox();
          const panel = await page.getByRole('complementary', { name: 'Groups' }).boundingBox();
          expect(tableBox, 'flags table').not.toBeNull();
          if (panel && tableBox && width <= 1024) {
            expect(panel.y + panel.height).toBeLessThanOrEqual(tableBox.y);
          }
        }
        // Every Edit and Delete row button lies inside the viewport, also behind a dialog.
        if (ROW_BUTTONS.has(screen)) {
          for (const flag of ['first-flag', 'second-flag']) {
            for (const action of ['Edit', 'Delete']) {
              const box = await page
                .locator(`button[aria-label="${action} ${key}.${flag}"]`)
                .boundingBox();
              expect(box, `${action} ${flag}`).not.toBeNull();
              if (box) {
                expect(box.x).toBeGreaterThanOrEqual(0);
                expect(box.x + box.width).toBeLessThanOrEqual(width);
              }
            }
          }
        }
      });
    }
  }
});

/** Long data of docs/project-acceptance-criteria.md ("UI redesign"). */
async function longData(api: import('../support/api').AdminApi, base: string) {
  const key = (base + 'k'.repeat(50)).slice(0, 50);
  // The criteria's "long data": name 100 characters without spaces, descriptions 500 characters,
  // flag key 50 characters without hyphens (full key 101 characters).
  const name = `Long${'n'.repeat(96)}`;
  const description = 'd'.repeat(500);
  const group = await api.createGroup(key, name, description);
  const flagKey = `f${'l'.repeat(49)}`;
  const flag = await api.createFlag(group.id, flagKey, true, `F${'e'.repeat(499)}`);
  return { group, key, name, description, flagKey, flag, flagDescription: `F${'e'.repeat(499)}` };
}

/** Elements (and their block ancestors) showing one of the texts that are wider than their box. */
async function clipped(page: Page, texts: string[]) {
  return page.evaluate((texts) => {
    const out: string[] = [];
    const all = Array.from(document.querySelectorAll<HTMLElement>('body *'));
    for (const text of texts) {
      const holders = all.filter(
        (el) =>
          el.textContent?.includes(text) &&
          !Array.from(el.children).some((c) => c.textContent?.includes(text)),
      );
      for (const holder of holders) {
        for (
          let el: HTMLElement | null = holder;
          el && el !== document.body;
          el = el.parentElement
        ) {
          if (getComputedStyle(el).display === 'inline') continue;
          if (el.getBoundingClientRect().width === 0) continue;
          if (el.scrollWidth > el.clientWidth)
            out.push(
              `${text.slice(0, 20)}… in ${el.tagName}.${el.className} ${el.scrollWidth} > ${el.clientWidth}`,
            );
        }
      }
    }
    return out;
  }, texts);
}

/** The header card of the page h1 and the boxes of its h1 and p that meet one of its shapes. */
async function textMeetsShape(page: Page) {
  return page.evaluate(() => {
    const decorative = (root: Element) =>
      Array.from(root.querySelectorAll('[aria-hidden="true"]')).filter(
        (s) => !s.closest('button, input'),
      );
    const out: string[] = [];
    let shapes = 0;
    const h1 = document.querySelector('h1');
    let card: HTMLElement | null = h1?.parentElement ?? null;
    while (card && card !== document.body && decorative(card).length === 0)
      card = card.parentElement;
    if (!card || card === document.body) return { found: false, shapes, out };
    const boxes = decorative(card)
      .map((s) => s.getBoundingClientRect())
      .filter((r) => r.width > 0 && r.height > 0);
    shapes = boxes.length;
    for (const t of Array.from(card.querySelectorAll('h1, p'))) {
      const a = t.getBoundingClientRect();
      for (const b of boxes) {
        if (a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom)
          out.push(`${t.tagName} "${t.textContent?.slice(0, 20)}" meets a shape`);
      }
    }
    return { found: true, shapes, out };
  });
}

test.describe('[AC-UI-8] long data wraps, header text never meets a shape', () => {
  for (const width of [1440, 1120, 800]) {
    test(`[AC-UI-8] ${width} px`, async ({ signedIn: page, api }, info) => {
      await page.setViewportSize({ width, height: 900 });
      const d = await longData(api, keyFor(info, `ui8-${width}`));
      await page.goto(`/groups/${d.group.id}`);
      await expect(page.getByRole('heading', { name: d.name })).toBeVisible();
      await expect(page.getByRole('switch')).toHaveCount(1);
      expect([d.name.length, d.key.length, d.description.length, d.flagKey.length]).toEqual([
        100, 50, 500, 50,
      ]);
      expect([d.flag.fullKey.length, d.flagDescription.length]).toEqual([101, 500]);
      const texts = [d.name, d.key, d.description, d.flagKey, d.flag.fullKey, d.flagDescription];
      expect(await clipped(page, texts)).toEqual([]);
      const group = await textMeetsShape(page);
      expect(group).toEqual({ found: true, shapes: group.shapes, out: [] });
      // Above 1100 px the three shapes of the group header card show (spec 005 4.5).
      if (width > 1100) expect(group.shapes).toBe(3);

      await page.getByRole('button', { name: `Delete ${d.flag.fullKey}` }).click();
      const title = `Delete flag ${d.flag.fullKey}?`;
      await expect(page.getByRole('alertdialog', { name: title })).toBeVisible();
      expect(await clipped(page, [title])).toEqual([]);
      await page.getByRole('button', { name: 'Delete', exact: true }).click();
      const toast = `Flag ${d.flag.fullKey} deleted`;
      await expect(page.getByRole('status').filter({ hasText: toast })).toBeVisible();
      expect(await clipped(page, [toast])).toEqual([]);

      await page.goto('/audit');
      await page.getByLabel('Filter by target key').fill(d.key);
      await expect(page.getByRole('row').nth(1)).toBeVisible();
      expect(await clipped(page, [d.flag.fullKey, d.key])).toEqual([]);
      const audit = await textMeetsShape(page);
      expect(audit).toEqual({ found: true, shapes: audit.shapes, out: [] });
      if (width > 1100) expect(audit.shapes).toBeGreaterThanOrEqual(3);
    });
  }

  test('[AC-UI-8] 1280 x 600: the delete group button of 30 flags can be reached', async ({
    signedIn: page,
    api,
  }, info) => {
    await page.setViewportSize({ width: 1280, height: 600 });
    const key = keyFor(info, 'ui8-30');
    const group = await api.createGroup(key, 'Thirty flags');
    for (let i = 0; i < 30; i++) await api.createFlag(group.id, `flag-${i}`, i % 2 === 0);
    await page.goto(`/groups/${group.id}`);
    await expect(page.getByRole('switch')).toHaveCount(30);
    await page.getByRole('button', { name: 'Delete group' }).click();
    await page.getByLabel(`Type ${key} to confirm`).fill(key);
    const button = page.getByRole('button', { name: 'Delete group and 30 flags' });
    await button.scrollIntoViewIfNeeded();
    await button.click();
    await expect(
      page.getByRole('status').filter({ hasText: `Group ${key} and 30 flags deleted` }),
    ).toBeVisible();
  });
});
