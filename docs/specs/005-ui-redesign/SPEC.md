# Spec 005 — UI redesign

Owner-authored, locked. Facts (status, milestone, branch, release): `spec.json`. Flow:
`docs/process/spec-flow.md`. Spec folder (`<spec>` below): `docs/specs/005-ui-redesign/`.

This is a change spec, executed by an autonomous builder session. Every "MUST" is a hard
requirement. Section 10 records the owner's decisions.

## 1. Relation to the current docs

`docs/product/` and `docs/architecture/` describe the system. This spec's own commits change
them, so on its branch they already describe the system after the spec. Everything they say that
this spec does not change still applies.

| File | Change |
| --- | --- |
| `docs/product/design/1 · Sign in.html`, `docs/product/design/1 · Sign in.png` | Replaced by the spec 005 design (4.1) |
| `docs/product/design/2 · Flags workspace.html`, `docs/product/design/2 · Flags workspace.png` | Replaced by the spec 005 design |
| `docs/product/design/3 · New flag dialog.html`, `docs/product/design/3 · New flag dialog.png` | Replaced by the spec 005 design |
| `docs/product/design/4 · Delete group confirmation.html`, `docs/product/design/4 · Delete group confirmation.png` | Replaced by the spec 005 design |
| `docs/product/design/5 · Audit log.html`, `docs/product/design/5 · Audit log.png` | Replaced by the spec 005 design |
| `docs/product/design/6 · States.html`, `docs/product/design/6 · States.png` | New: reference sheet of states (4.1) |
| `docs/product/design/README.md` | Current design is spec 005; update row |
| `docs/product/ui.md` 8, 8.2, 8.3, 8.6 | "Visual reference": six designs, IBM Plex Sans Condensed for titles, decorative shapes. Appearance only: sign-in art panel instead of a centered card, the top bar as a white pill, audit labels with a leading shape. Every text, rule and state stays |
| `docs/architecture/deployment.md` 10.2 | "Security headers": fonts come from three `@fontsource` packages, adding `@fontsource/ibm-plex-sans-condensed` (4.7). Headers and CSP unchanged |
| `docs/architecture/testing.md` 11.6 | "Matching the design" item 1: the style checks (AC-STYLE-1) use the spec 005 values. Items 2 and 3 unchanged: the five baselines are re-recorded with a `<spec>/builder/DECISIONS.md` entry per screen, and the gallery compares with the new designs |

Not changed: the backend and `backend/openapi.json`, every route, text, role, accessible name and
behaviour of `docs/product/ui.md` section 8, gate 4 banned dependencies, the tech stack (CSS
Modules plus CSS variables, no component library).

The builder never edits these files, the criteria or this spec. A spec change during the build
is an owner amendment (`docs/process/spec-flow.md` section 2).

## 2. Goal

Give the admin UI a calmer, more modern look with one consistent shape language, without changing
what it does. Colours are balanced: mostly neutrals, navy and slate blue for structure and action,
sage and apricot only as small shapes, brick only for delete. Shapes carry meaning: a filled circle
is "on" or "created", a ring is "off" or "updated", a half circle is "toggled", a square is
"deleted". The owner chose this direction ("Redesign C · balanced colours") from three variants.

Not in scope: dark mode, themes, new screens or features, new animations, i18n, any backend
change, accessibility tooling (axe, still parked in spec 001).

## 3. MUST stay the same

1. Every acceptance criterion in `docs/project-acceptance-criteria.md` passes at its current
   version, with the changes of section 5.
2. Every route, text, role, accessible name, ARIA state, focus order and keyboard behaviour of
   `docs/product/ui.md` section 8. Landmarks, their names and heading levels stay as in the
   current code. The design markup shows appearance only; where it differs from
   `docs/architecture/frontend.md` 8.7 or the current code's semantics, those decide (AC-UI-6).
3. Security rules of spec 001 section 5 (`docs/architecture/security.md`) and the headers and CSP
   of `docs/architecture/deployment.md` 10.2. The UI loads nothing from other origins: fonts stay
   self-hosted. The Google Fonts `<link>` in the design files is only for viewing them and MUST
   NOT be copied.
4. No backend file changes; `backend/openapi.json` unchanged.
5. All 16 gates, thresholds, banned lists and test-integrity rules unchanged.
6. Styling stays CSS Modules plus CSS variables. No UI component library, no CSS framework, no
   CSS-in-JS. The only new dependency is the one in section 4.7. No Flyway migration.

## 4. Design

### 4.1 Visual reference

The designs are in `docs/product/design/` (on this branch; section 10 item 3). Each screen has `N · <screen>.html` (plain HTML and CSS: read the exact values
there) and `N · <screen>.png` (how it looks, 1440 px wide at 2×).

| File | Screen |
| --- | --- |
| `1 · Sign in` | `/login?expired=1` |
| `2 · Flags workspace` | `/groups/<id>`, group selected |
| `3 · New flag dialog` | New flag modal; New group, Edit group and Edit flag use the same styles |
| `4 · Delete group confirmation` | Delete group confirm dialog |
| `5 · Audit log` | `/audit` |
| `6 · States` | Reference sheet, not a screen: empty states, toasts, sign-in error and busy, field error, switch in flight, focus ring, Edit flag (read-only Key), Delete flag, 404 |

Names, keys and times in the designs are sample data. Spec 001 section 8 decides behaviour and
texts; these files decide appearance. Each file's token block is a subset of section 4.2.

### 4.2 Tokens

`frontend/src/styles/tokens.css` MUST define these custom properties with these values. Old
tokens that no longer have a use are removed. Colours in components use tokens, not raw values,
except `#ffffff` (text on filled buttons and toasts, focus ring on ink) and the logo fills (4.5).
Sizes and radii without a token come from the design `.html`.

| Token | Value | Use |
| --- | --- | --- |
| `--color-bg` | `#f1f2f4` | Page ground, chips, inner boxes |
| `--color-surface` | `#ffffff` | Cards, rows, fields, top bar |
| `--color-text` | `#1c2433` | Body text |
| `--color-text-soft` | `#3d4556` | Descriptions, details, Created by, badges |
| `--color-text-muted` | `#5b6475` | Labels, meta, placeholders, column heads |
| `--color-border` | `#dde0e6` | Field and secondary-button borders, pie "off" part |
| `--color-primary` | `#3d59a8` | Primary actions, switch on, created shapes, links, focus ring |
| `--color-primary-dark` | `#2d4486` | Text on `--color-primary-soft`, link hover |
| `--color-primary-soft` | `#dfe5f3` | Info banner, New group button, created label |
| `--color-ink` | `#1c2433` | Dark surfaces: selected group, active nav, active filter, dark buttons, audit header |
| `--color-ink-raised` | `#2c3547` | Badge on a selected group |
| `--color-ink-line` | `#3a4457` | Pie "off" part on ink, dot grid |
| `--color-ink-text-muted` | `#b9c0ce` | Secondary text on ink |
| `--color-sage` | `#8fb3a6` | Decorative shapes, avatar, pie "on" part on ink, success toast shape |
| `--color-sage-soft` | `#e3eeea` | Large decorative circle in the group header, empty-state shape |
| `--color-apricot` | `#e8a87c` | Decorative shapes, logo corner, sign-in arrow circle |
| `--color-apricot-soft` | `#f8e6da` | Toggled label |
| `--color-apricot-text` | `#8a4b26` | Text on the toggled label |
| `--color-neutral-soft` | `#eceef1` | Updated label |
| `--color-danger` | `#b5473a` | Delete text and fill, error toast, invalid field border |
| `--color-danger-text` | `#9a3a2f` | Text on `--color-danger-soft`, field error text |
| `--color-danger-soft` | `#f5e0dc` | Deleted label, sign-in error, delete icon button |
| `--color-danger-border` | `#e6bdb6` | Delete group outline, disabled danger button |
| `--color-switch-off` | `#cdd2db` | Switch track when off |
| `--color-overlay` | `rgba(28, 36, 51, 0.62)` | Modal backdrop |
| `--font-sans` | `'IBM Plex Sans', 'Segoe UI', system-ui, sans-serif` | Body |
| `--font-display` | `'IBM Plex Sans Condensed', 'IBM Plex Sans', sans-serif` | `h1`, dialog titles, Groups heading |
| `--font-mono` | `'IBM Plex Mono', ui-monospace, monospace` | Keys, full keys, audit targets |
| `--radius-chip` | `11px` | Key prefix chip |
| `--radius-field` | `16px` | Text fields, textarea, prefixed Key field |
| `--radius-banner` | `18px` | Info banner, sign-in error, toasts |
| `--radius-row` | `22px` | Table rows, inner boxes |
| `--radius-panel` | `28px` | Groups panel, cards on the states sheet, 404 card |
| `--radius-hero` | `32px` | Group header card, audit header card |
| `--radius-dialog` | `36px` | Dialogs |
| `--radius-art` | `40px` | Sign-in art panel |
| `--radius-pill` | `999px` | Buttons, search fields, nav, chips, badges, shapes |
| `--shadow-dialog` | `0 24px 60px rgba(28, 36, 51, 0.35)` | Dialogs |
| `--shadow-toast` | `0 12px 32px rgba(28, 36, 51, 0.22)` | Toasts |
| `--shadow-knob` | `0 1px 3px rgba(28, 36, 51, 0.25)` | Switch knob when off |

**Contrast rule.** Every text on its background MUST reach 4.5:1 (3:1 for text of 24 px and
more, or 18.66 px bold). Disabled controls are exempt (WCAG 1.4.3). The pairs the designs use,
measured with the WCAG formula:

| Text on background | Ratio |
| --- | --- |
| `--color-text` on `--color-surface` / `--color-bg` | 15.56 / 13.89 |
| `--color-text-soft` on `--color-surface` / `--color-bg` / `--color-neutral-soft` | 9.62 / 8.58 / 8.27 |
| `--color-text-muted` on `--color-surface` / `--color-bg` / `--color-primary-soft` | 5.96 / 5.32 / 4.72 |
| `--color-primary` on `--color-surface` (links); hover `--color-primary-dark` | 6.58 / 9.20 |
| `#ffffff` on `--color-primary` / `--color-ink` / `--color-ink-raised` / `--color-danger` | 6.58 / 15.56 / 12.30 / 5.35 |
| `--color-ink-text-muted` on `--color-ink` | 8.52 |
| `--color-primary-dark` on `--color-primary-soft` | 7.29 |
| `--color-apricot-text` on `--color-apricot-soft` | 5.56 |
| `--color-danger-text` on `--color-danger-soft` / `--color-surface` | 5.49 / 6.95 |
| `--color-danger` on `--color-surface` | 5.35 |
| `--color-ink` on `--color-sage` / `--color-apricot` | 6.79 / 7.65 |

Non-text: the focus ring is `--color-primary` on light surfaces and `#ffffff` inside ink surfaces
(4.4). The switch track off (`--color-switch-off`) is light on white; the state is also given by
the `On` / `Off` text next to every switch (`docs/product/ui.md` 8.4) and by the knob position.

### 4.3 Typography

All IBM Plex, self-hosted (section 4.7). Body 14 px (sign-in page 15 px), weight 400.

| Element | Font | Size / weight |
| --- | --- | --- |
| `h1` on pages (`Sign in`, group name, `Audit log`, `Page not found`) | `--font-display` | Sign in 48 / 600; group name, `Audit log`, 404: 56 / 600; line height 1 |
| Dialog titles (`h2`) | `--font-display` | 32 / 600, line height 1.1; Delete flag 28 / 600 (design 6) |
| `Groups` heading | `--font-display` | 22 / 600 |
| Labels, buttons | `--font-sans` | 14–15 / 500; primary and danger buttons 600 |
| Column heads | `--font-sans` | 12 / 600, uppercase, letter spacing 0.06em, `--color-text-muted` |
| Flag key / full key | `--font-mono` | 15 / 500 and 12 / 400 |
| Audit target, group key chip | `--font-mono` | 14 / 400 |
| Meta, help, counter, error text | `--font-sans` | 13 / 400 |

### 4.4 Components

Exact sizes and spacing are in the design `.html` files; where a summary in 4.3, 4.4 or 4.6
differs from them, the design file decides appearance. This table is the summary the builder implements once in
`src/components/` (`docs/architecture/frontend.md` 8.7) and reuses.

| Component | Look |
| --- | --- |
| Button `primary` | Pill, height 48, `--color-primary`, white text, weight 600; `New flag` carries a decorative plus in a 32 px white circle |
| Button `soft` | Pill, height 40, `--color-primary-soft`, `--color-primary-dark`, weight 600; `New group` carries a plus in a 28 px `--color-primary` circle |
| Button `secondary` | Pill, height 44 in the top bar and group header, 1.5 px `--color-border`, white, `--color-text`, weight 500 |
| Button `danger-outline` | Pill, 1.5 px `--color-danger-border`, white, `--color-danger` |
| Button `danger` | Pill, `--color-danger`, white, weight 600; disabled: `--color-danger-border` |
| Button `dark` | Pill, `--color-ink`, white (`Sign in` 56 px high with an apricot arrow circle, `Load more`) |
| Decorative icons | Plus, arrow and info circles are outside the accessible name (`docs/product/ui.md` 8.4); the decorative `svg` itself keeps `aria-hidden="true"`, also when a wrapper has it |
| Icon buttons | 40 px circles: edit on `--color-bg`, delete on `--color-danger-soft` with `--color-danger` icon; Close 44 px white circle; Copy group key 32 px white circle |
| Text field | Height 52, 1.5 px `--color-border`, radius `--radius-field`; invalid: border `--color-danger`, error text 13 px `--color-danger-text`; read-only: `--color-bg`, `--color-text-muted` |
| Prefixed Key field | Same frame; the `<groupKey>.` prefix is a chip on `--color-bg`, radius `--radius-chip`, mono 13 |
| Search fields | Borderless pills: 44 px on `--color-bg` in the groups panel, 48 px white on the page ground and in the audit header |
| Switch | Track 54 × 30 pill, knob 24 px white; on: `--color-primary`, knob moved 24 px, no shadow; off: `--color-switch-off`, `--shadow-knob`; in flight: opacity 0.6 |
| Status filter | White pill group (4 px padding); pressed segment `--color-ink` with white text |
| Nav (top bar) | Pill group on `--color-bg`; current link `--color-ink` with white text |
| Badge (`enabled/total`) | Pill 12 / 600: `--color-bg` / `--color-text-soft`; on a selected group `--color-ink-raised` / white |
| Toast | Radius `--radius-banner`, `--shadow-toast`, weight 500, white text, max width 420 with wrapping, a leading shape: success on `--color-ink` with a sage circle, error on `--color-danger` with a white square; bottom right, 24 px from the edges, 3 s (`docs/product/ui.md` 8.5) |
| Info banner / sign-in error | Radius `--radius-banner`: info on `--color-primary-soft` with a 26 px `--color-primary` circle; error on `--color-danger-soft`, `--color-danger-text`, a small square |
| Empty state | Centered muted text 15 px; with an action: a decorative ring plus half circle above it and a primary button |
| Modal | Radius `--radius-dialog`, `--shadow-dialog`, backdrop `--color-overlay`, max width 560, `max-height: calc(100vh - 48px)` and `overflow: auto`; a sage quarter circle in the top-right corner behind the Close button |
| Confirm dialog | Max width 540, the same `max-height` and `overflow` as the modal (AC-UI-8); a cluster of a 56 px `--color-danger` square with the trash icon, an apricot half circle and an ink dot; buttons `secondary` and `danger` |
| Focus | Every interactive element: `:focus-visible` outline 2 px solid, offset 2 px, `--color-primary`; inside ink surfaces (audit header) `#ffffff` (AC-UI-9) |

### 4.5 Shapes

Every shape is decorative: inside an element with `aria-hidden="true"`, not focusable, and
removing it changes no behaviour (AC-UI-6). A shape contains no text node: the `i` of the info
circle in design 1 is drawn with SVG or CSS, so `textContent` of banners, toasts, alerts and labels
stays exactly the spec 001 text (existing tests assert it, for example `[AC-AUTH-4]`).

| Shape | Where | Rule |
| --- | --- | --- |
| Logo mark | Top bar, sign-in | Inline SVG, `viewBox="0 0 28 28"`: `circle cx=12 cy=16 r=12` `#3D59A8` and `path d="M16 0a12 12 0 0 1 12 12H16z"` `#E8A87C`. Replaces the flag icon |
| Group pie | Each group button | 30 px circle, `conic-gradient(on 0 var(--share), off 0)`. `--share` = `floor((200 × enabled + total) / (2 × total))%` (half up, integer maths), `0%` when total is 0 (AC-UI-4). Colours: on `--color-primary`, off `--color-border`; on a selected group `--color-sage` and `--color-ink-line` |
| Audit label shape | Start of each Action label | Created: filled circle `--color-primary`; updated: 2 px ring `--color-text-soft`; toggled: half circle apricot / ink; deleted: 11 px square `--color-danger` (AC-UI-5) |
| Initial state shape | New flag dialog, left of the `Initial state` text | 36 px ring (3 px `--color-ink`) when off, filled `--color-primary` circle when on |
| Header shapes | Group header card, audit header card | Positions as in designs 2 and 5. The text column never enters the shape zone (`max-width: calc(100% - 380px)` in the group header, `calc(100% - 640px)` in the audit header). Below 1100 px the shapes are hidden and the text takes the full width (AC-UI-8) |
| Sign-in art panel | Left of the form | Ink panel with circle, half circle, square, ring, two switch shapes, dot grid and quarter circle, as design 1. Hidden below 860 px |
| Dialog shapes | Modal corner, confirm cluster, flag-list squares | As section 4.4 and designs 3, 4 |
| Toast, banner, empty-state and 404 shapes | As design 6 | Small circles, squares, rings, a quarter circle |

### 4.6 Screens and layout

- **App shell.** The top bar is a white pill (min height 64, 20 px from the top and 24 px from the
  sides of the window): logo and name, nav pill group, avatar circle (sage, initial of the username,
  `aria-hidden`) with the username, `Sign out` (`secondary`). The version footer stays (12 px,
  `--color-text-muted`, right).
- **Workspace.** Groups panel (white card, `--radius-panel`, flex basis 280 px) and the main area
  (flex basis 720 px) in one wrapping row with 20 px gaps; below about 1068 px the panel moves
  above the main area. The group header is a white hero card; below it the toolbar (search,
  status filter, `New flag`) sits on the page ground; each flag row is a white card
  (`--radius-row`, 8 px gap) with the same table roles and column heads as today. Columns
  `minmax(160px, 1.2fr) minmax(0, 1.5fr) 120px 90px 110px 96px`; no fixed minimum table width.
- **Audit log.** Max width 1280 px. The header is an ink hero card with the title, subtitle and
  the filter field; rows are white cards like the flags table, columns
  `170px 100px 170px minmax(0, 1.1fr) minmax(0, 1.5fr)`, no fixed minimum table width.
- **Below 860 px.** The sign-in art panel is hidden; the flags table hides only `Created by` and
  `Updated`, the audit table only `Time` and `Actor` (`docs/product/ui.md` 8.4, 8.6). No screen and no table
  scrolls sideways at 768 px and wider (AC-UI-7).
- **Long text.** Every user text (group name and key, descriptions, flag keys, full keys, audit
  targets and details, dialog titles, toasts) wraps with `overflow-wrap: anywhere`; nothing is
  clipped or overlaps (AC-UI-8).
- **Motion.** Only what exists today: the switch knob transition (0.15 s) and the spinner
  rotation. No new motion rule.

### 4.7 Dependency

Add `@fontsource/ibm-plex-sans-condensed` `5.3.0` (OFL-1.1, same publisher and version as the
two Plex packages already used) and import weight 600 in `src/main.tsx`. No other new package.

### 4.8 Files the change touches (guide)

`frontend/src/styles/tokens.css`, `global.css`; the CSS modules and markup of `components/`
(`Button`, `TextField`, `Toggle`, `Modal`, `ConfirmDialog`, `Toast`, `EmptyState`, `FlagLogo`,
`Spinner`) and of `features/` (`shell`, `auth`, `groups`, `flags`, `audit`); `src/main.tsx`;
`frontend/package.json` and lock file; `frontend/e2e/style.spec.ts` (AC-STYLE-1); new
tests for AC-UI-1 to AC-UI-9; the five screenshot baselines; `docs/design-compare/` and
`scripts/design-compare.sh` (its image path still points to the old `docs/design/` folder; it
must read `docs/product/design/`).

Existing tests depend on these DOM hooks; they MUST stay (the tests may change only by plumbing):
the plus `svg` keeps `aria-hidden="true"` (`GroupsPage.test.tsx`); the spinner keeps
`data-spinner` (`LoginPage.test.tsx`); the group meta is a `<p>` that starts with `Created by`;
the badge is its own element inside the group button with text `<enabled>/<total>`; flag rows keep
`[role="row"] > [role="cell"]:first-child` and relative times end in `ago` or `just now` (the masks
of `screenshots.spec.ts`).

**ARIA snapshots (AC-UI-6).** In phase A, before any change under `frontend/src/`, the builder
records the snapshots of the screens with `toMatchAriaSnapshot` at 1440 × 900 on the seeded
screenshot stack, relative times as patterns, in one commit that touches no `frontend/src/` file,
named in `<spec>/builder/DECISIONS.md`. Recording them from the running pre-redesign code is the
point of the criterion ("unchanged"), so CLAUDE.md section 4 ("expected values never from running
the code") does not apply to them. They never change after that commit; `test-auditor` checks it.
The black-box suite records its own baseline from the `spec-005-ready` build.

## 5. Acceptance criteria

In `docs/project-acceptance-criteria.md` (File 4), due M12. New: AC-UI-1 to AC-UI-9.

- **AC-UI-1** · Ground, text colour, the three Plex fonts declared and loaded, no other origin
- **AC-UI-2** · Colours of primary, dark and danger buttons and of the switch on and off
- **AC-UI-3** · Every button (except group buttons) and the three search fields are pills or circles
- **AC-UI-4** · Each group has a hidden pie whose `--share` is the half-up percentage of `enabled / total`
- **AC-UI-5** · Audit labels keep their text, start with a hidden shape, and have the listed colours
- **AC-UI-6** · Hidden shapes are never focusable; the ARIA snapshot of each screen is unchanged
- **AC-UI-7** · At 768 to 1440 px no page or table scrolls sideways and row actions stay visible
- **AC-UI-8** · Long data wraps, header text never meets a shape, the delete dialog scrolls on a short window
- **AC-UI-9** · Visible 2 px focus ring on every focused element, white inside ink surfaces

**Criteria that change** (same ID, same position, version + 1, `changed by spec 005`):

| ID | Now | Becomes |
| --- | --- | --- |
| AC-STYLE-1 | v1: The style checks of 11.6 item 1: on `/login` the `Sign in` button background is `#2350C8`, the body font is IBM Plex Sans, and the Username and Password inputs are 44 px high with a 6 px border radius; on a group page the top bar background is `#15181D` and its height 60 px, full keys use IBM Plex Mono, and a switch that is on and the `New flag` button have the background `#2350C8` | v2: The style checks of 11.6 item 1, with the spec 005 values: on `/login` the `Sign in` button background is `#1C2433`, the body font is IBM Plex Sans, and the Username and Password inputs are 52 px high with a 16 px border radius; on a group page the top bar background is `#FFFFFF`, its border radius at least half its height and its height at least 64 px, full keys use IBM Plex Mono, and a switch that is on and the `New flag` button have the background `#3D59A8` |

AC-AUTH, AC-GRP, AC-FLAG, AC-EVAL, AC-CACHE, AC-AUD and AC-OPS unchanged.

## 6. Test changes

The tests that may change are listed in `<spec>/test-changes.md`.
Allowed behaviour changes: AC-STYLE-1.
Every other test may change only by the plumbing clause in that file. A change to any other
test is a Level 3 escalation (section 8).

`frontend/e2e/style.spec.ts` carries `[AC-STYLE-1]` on `main`, and AC-STYLE-1 goes to v2, so
gate 16 lets the builder rewrite it to the new values. The five screenshot baselines change
under `docs/architecture/testing.md` 11.6 item 2 (a `<spec>/builder/DECISIONS.md` entry per
screen and recording, checked by gate 15); that is not a test-code change.

## 7. Acceptance delta

For the black-box acceptance suite (`docs/process/validation.md`); the tests name criterion and
version (`[AC-UI-1 v1]`):

- Added: AC-UI-1, AC-UI-2, AC-UI-3, AC-UI-4, AC-UI-5, AC-UI-6, AC-UI-7, AC-UI-8, AC-UI-9
- Changed: AC-STYLE-1: the suite's style check asserts the spec 005 values
- Retired: none

## 8. Process

- `docs/process/` and `CLAUDE.md` apply. Commit messages `M12 <area>: <what>`, trailers `AC:`
  and `Spec: 005 §<n>`.
- The builder works on `feature/ui-redesign`, which already holds this spec (tag
  `spec-005-ready`). First commit: the memory files in `<spec>/builder/` (`STATE.md`,
  `PROGRESS.md`, `DECISIONS.md`, `MILESTONE` = `12`). After the first green chunk, one draft PR
  `feature/ui-redesign` → `main`; its body contains `Closes #36`.
- Phases, each chunk green: **A** before any visual change, record the ARIA snapshots of the
  screens from the current code (AC-UI-6); then the font package, tokens, global styles and the
  shared components of 4.4, the style checks (AC-STYLE-1), tests for AC-UI-1 to AC-UI-3
  and AC-UI-9; **B** screens: shell, sign-in, workspace (pie, row cards), dialogs, audit
  (labels), tests for AC-UI-4 to AC-UI-6; **C** states of design 6, layout and long data
  (AC-UI-7, AC-UI-8), the five baselines re-recorded with `<spec>/builder/DECISIONS.md` entries,
  `docs/design-compare/` regenerated, release: the next free minor version (`VERSION`,
  `CHANGELOG.md`).
- Milestone audit (`docs/process/builder-agents.md` section 5): `spec-auditor`, `test-auditor`,
  `design-checker` (against the six designs of section 4.1; tell it to include `6 · States`, its
  prompt says five screens), `final-reviewer` (scope frontend).

**Level 3 triggers for M12** (in addition to `docs/process/escalation.md`)

- A change would break an item of section 3.
- A test outside `<spec>/test-changes.md` would need more than a plumbing change.
- The only way to make a gate green is a new exception, exclusion or ignore entry.
- A design element needs a text, role, accessible name or behaviour change.
- A text would need a colour pair below the contrast rule of section 4.2.

## 9. Definition of done

- [ ] `make verify-all` passes, all 16 gates; report committed as `<spec>/builder/verify-report.md`.
- [ ] AC-UI-1 to AC-UI-9 and every other criterion (with section 5 changes) pass.
- [ ] Only the tests in `<spec>/test-changes.md` changed beyond plumbing.
- [ ] `VERSION` and `CHANGELOG.md` for the next free minor version; the five screenshot baselines re-recorded, each recording with a `<spec>/builder/DECISIONS.md` entry; `docs/design-compare/` shows the built screens next to the spec 005 designs.
- [ ] No backend change; `backend/openapi.json` unchanged; one new package only (section 4.7).
- [ ] Milestone audit done, no open BLOCKER or CRITICAL; no open escalation; PR ready with summary.

## 10. Owner decisions

1. ~~Direction~~ **Decided: "Redesign C · balanced colours"**, chosen by the owner from three
   variants (calm, bold, balanced) on 2026-10-13. Shapes as in all three variants. The bold one
   was too loud for an admin tool used all day; the calm one had a single accent and little
   structure. The balanced one passes WCAG AA for every text pair (4.2). Before the new flow
   this was decision record 0013.
2. ~~`frontend/e2e/style.spec.ts` has no AC ID and asserts the old values~~ **Decided: the style
   checks have their own ID, AC-STYLE-1** (owner PR #40, 2026-10-15). `style.spec.ts` carries
   `[AC-STYLE-1]` on `main`; this spec raises AC-STYLE-1 to v2 (section 5), so gate 16 lets its
   test change (section 6).
3. ~~Where the designs live~~ **Decided: in `docs/product/design/`, changed by this spec's own
   commit** (2026-10-19, spec flow: a spec changes `docs/product/` before its tag). The CLAUDE.md
   rules, the `design-checker` agent and `scripts/design-compare.sh` read that folder. The spec
   folder keeps no copy. Earlier (2026-10-15) the owner copied the designs there by hand.
4. ~~The new font package~~ **Decided: add `@fontsource/ibm-plex-sans-condensed` 5.3.0**
   (owner, 2026-10-15; 4.7).
5. ~~Release number~~ **Decided: the next free minor version** (owner, 2026-10-15). The builder
   reads `VERSION` on `main` at its final merge of `origin/main` and adds one minor
   (`docs/architecture/deployment.md` 9.6). The order of specs 003 and 005 does not matter.
6. ~~Two-reviewer challenge~~ **Decided: run on 2026-10-13** (simplicity and robustness, 20
   findings): the colour pairs became a 4.5:1 rule with measured pairs; flag dots dropped; integer
   pie rounding; no fixed minimum table width; all user text wraps, header shapes stay out of the
   text column and hide below 1100 px; the confirm dialog scrolls; white focus ring on ink; fonts
   from the UI origin; AC-UI-6 snapshot from before the redesign. Rejected: a darker off-switch
   track (the `On` / `Off` text carries the state).
7. ~~Template~~ **Decided: spec re-shaped to the spec-flow template on 2026-10-19** (design
   unchanged; criteria in `docs/project-acceptance-criteria.md` with versions; designs and doc
   changes on this branch).
8. ~~Second two-reviewer challenge~~ **Decided: run on 2026-10-19** against the current repo (20
   findings). Fixed: AC-UI-1 loads the three faces before `check` (a face is loaded only when used);
   AC-UI-2 exact names and the two confirm buttons; AC-UI-5 names the label element; AC-UI-7
   "horizontally inside the viewport" and the `table` elements; AC-UI-8 adds 1120 px, the Delete
   flag title and toast, and only the header shapes; a 50-character group key in "long data";
   AC-UI-6 snapshot recording and protection (4.8); shapes hold no text node (4.5), because tagged
   tests assert exact `textContent`; the DOM hooks existing tests use (4.8);
   `scripts/design-compare.sh` path into scope; summaries yield to the design files (4.4), with
   the top bar side gap, the Delete flag title and the button heights corrected; the token rule
   covers colours only; the reduced-motion rule dropped (new behaviour without an AC, and the goal
   adds no motion); design README sizes and wording. Rejected: fold AC-UI-1 and AC-UI-2 into
   AC-STYLE-1 (the overlap is small, and the acceptance suite is built around the nine IDs); drop
   AC-UI-4 (it is the only check that the pie shows the right share, for one small test).

Accepted by the owner on 2026-10-15; re-shaped and re-checked on 2026-10-19.
