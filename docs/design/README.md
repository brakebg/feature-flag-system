# Designs

Approved screen designs. Spec decides behaviour; the design decides appearance
(`docs/SPEC.md` section 8). Names and values shown are sample data.

| Files | Status |
| --- | --- |
| `N · <screen>.html` + `N · <screen>.png` (5 screens) | **Current design.** Read exact values (colours, sizes, fonts, texts) from the `.html`; the `.png` shows how it looks (1440×900 at 2×) |
| `Feature Flags Admin UI.html` | Original bundle of the first version. Kept for history; it does not include the updates below. Do not use it |

## Updates

| Date | Change | Why |
| --- | --- | --- |
| 2026-10-03 | Sign in: "Sessions last 8 hours." removed | Decision 0004, Q-062 |
| 2026-10-03 | Below 860 px: flags table keeps Description and Edit/Delete (hides only Created by, Updated); audit table keeps Details (hides only Time, Actor) | Decision 0004, Q-036 |
| 2026-10-03 | Action controls are `<button>` (nav links Flags / Audit log stay links) | Decision 0004, Q-034 |
| 2026-10-03 | Flags and audit tables have table roles (`table`, `row`, `columnheader`, `cell`) | Decision 0004, Q-040 |

PNGs were re-rendered from the `.html` files with Playwright (Chromium), 1440×900, device
scale 2.
