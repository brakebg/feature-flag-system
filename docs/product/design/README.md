# Designs

Approved screen designs. Spec decides behaviour; the design decides appearance
(`docs/product/ui.md` section 8). Names and values shown are sample data.

| Files | Status |
| --- | --- |
| `N · <screen>.html` + `N · <screen>.png` (5 screens and the reference sheet `6 · States`, which shows empty states, toasts, errors, busy and focus states, Edit flag, Delete flag and the 404 page) | **Current design: spec 005 "Redesign C · balanced colours"**. Plain HTML and CSS: read exact values (colours, sizes, fonts, texts) from the `.html`. The `.png` shows how it looks (1440 px wide at 2×) |
| `Feature Flags Admin UI.html` | Original bundle of the first version, in the old look. Kept for history. Do not use it |

## The screens

"The screens" in the acceptance criteria (AC-UI-1, AC-UI-3, AC-UI-6, AC-UI-7, AC-UI-9) are
defined in `docs/project-acceptance-criteria.md`, in the note above the spec 005 criteria: 12
states (pages, empty states, the 404 page and each dialog open). The five designs below show
how they look; `6 · States` shows the empty states, the 404 page and the other dialogs.

| Design file | Shows |
| --- | --- |
| `1 · Sign in` | `/login?expired=1` |
| `2 · Flags workspace` | `/groups/<id>`, group selected |
| `3 · New flag dialog` | New flag modal; New group, Edit group and Edit flag use the same styles |
| `4 · Delete group confirmation` | Delete group confirm dialog |
| `5 · Audit log` | `/audit` |

The `.html` files use the token names and values of spec 005 section 4
(`docs/specs/005-ui-redesign/SPEC.md`). `frontend/src/styles/tokens.css` matches them from
milestone M12 (spec 005).

## Updates

| Date | Change | Why |
| --- | --- | --- |
| 2026-10-03 | Sign in: "Sessions last 8 hours." removed | Decision 0004, Q-062 |
| 2026-10-03 | Below 860 px: flags table keeps Description and Edit/Delete (hides only Created by, Updated); audit table keeps Details (hides only Time, Actor) | Decision 0004, Q-036 |
| 2026-10-03 | Action controls are `<button>` (nav links Flags / Audit log stay links) | Decision 0004, Q-034 |
| 2026-10-03 | Flags and audit tables have table roles (`table`, `row`, `columnheader`, `cell`) | Decision 0004, Q-040 |
| 2026-10-13 | All five screens redrawn from the built UI (`frontend/src`, v1.1.0). The `.html` files are now plain HTML and CSS, not a bundle | Owner: the code is the source of truth for appearance |
| 2026-10-15 | Screens 1 to 5 replaced by the spec 005 designs; `6 · States` added. The previous designs are in git history (`c78a827:docs/product/design/`) | Spec 005, section 10 items 1 and 3 |

The table below is history: what the 2026-10-13 redraw changed. Colours, fonts, sizes, radii
and spacing were then the same as the code. The redraw changed only these points (checked with
a pixel diff of the old and new PNGs):

| Screen | Change |
| --- | --- |
| 1 · Sign in | None. The PNG is identical |
| 2 · Flags workspace | Groups and flags are sorted by key, as the app shows them |
| 2 · Flags workspace, 5 · Audit log | Footer with the app version, bottom right, 12 px, `#59616C` |
| 2 · Flags workspace | Plus, copy, edit and delete icons use the paths from the code |
| 3 · New flag dialog | The Key field with the `orders.` prefix is 44 px high (was 46 px) |
| 4 · Delete group confirmation | Flag list sorted by key; trash icon uses the path from the code |
| 3, 4 | Backdrop written as `rgba(59, 66, 76, 0.75)`, the code value. Over the page it looks the same as the old flat `#6B717A` |

PNGs were rendered from the `.html` files with Playwright (Chromium), 1440 px wide (full page height), device
scale 2.
