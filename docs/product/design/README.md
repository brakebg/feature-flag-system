# Designs

Approved screen designs. Spec decides behaviour; the design decides appearance
(`docs/product/ui.md` section 8). Names and values shown are sample data.

| Files | Status |
| --- | --- |
| `N · <screen>.html` + `N · <screen>.png` (5 screens) | **Current design.** Plain HTML and CSS: read exact values (colours, sizes, fonts, texts) from the `.html`. The `.png` shows how it looks (1440×900 at 2×) |
| `Feature Flags Admin UI.html` | Original bundle of the first version. Kept for history; it does not include the updates below. Do not use it |

The `.html` files use the same token names and values as `frontend/src/styles/tokens.css`.

## Updates

| Date | Change | Why |
| --- | --- | --- |
| 2026-10-03 | Sign in: "Sessions last 8 hours." removed | Decision 0004, Q-062 |
| 2026-10-03 | Below 860 px: flags table keeps Description and Edit/Delete (hides only Created by, Updated); audit table keeps Details (hides only Time, Actor) | Decision 0004, Q-036 |
| 2026-10-03 | Action controls are `<button>` (nav links Flags / Audit log stay links) | Decision 0004, Q-034 |
| 2026-10-03 | Flags and audit tables have table roles (`table`, `row`, `columnheader`, `cell`) | Decision 0004, Q-040 |
| 2026-10-13 | All five screens redrawn from the built UI (`frontend/src`, v1.1.0). The `.html` files are now plain HTML and CSS, not a bundle | Owner: the code is the source of truth for appearance |

Colours, fonts, sizes, radii and spacing were already the same as the code. The redraw
changed only these points (checked with a pixel diff of the old and new PNGs):

| Screen | Change |
| --- | --- |
| 1 · Sign in | None. The PNG is identical |
| 2 · Flags workspace | Groups and flags are sorted by key, as the app shows them |
| 2 · Flags workspace, 5 · Audit log | Footer with the app version, bottom right, 12 px, `#59616C` |
| 2 · Flags workspace | Plus, copy, edit and delete icons use the paths from the code |
| 3 · New flag dialog | The Key field with the `orders.` prefix is 44 px high (was 46 px) |
| 4 · Delete group confirmation | Flag list sorted by key; trash icon uses the path from the code |
| 3, 4 | Backdrop written as `rgba(59, 66, 76, 0.75)`, the code value. Over the page it looks the same as the old flat `#6B717A` |

PNGs were rendered from the `.html` files with Playwright (Chromium), 1440×900, device
scale 2.
