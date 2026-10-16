# 0013 · Spec 005: redesign the admin UI with balanced colours and a shape language

Status: **Accepted** (2026-10-15). Proposed 2026-10-13. Owner-authored.

## Decision

Give the admin UI a new look and keep its behaviour. The palette is mostly neutrals, with navy
and slate blue for structure and actions, sage and apricot only as small decorative shapes, and
brick only for delete. Shapes carry meaning: filled circle for on and created, ring for off and
updated, half circle for toggled, square for deleted. Controls are pills; cards and dialogs have
large radii. Fonts stay IBM Plex, with IBM Plex Sans Condensed for titles. The approved designs
are the six files in `docs/specs/005-ui-redesign/design/`.

## Why

The first UI was built to a plain, functional design. The owner explored three directions on a
design canvas (2026-10-13): a calm one (cobalt and coral), a bold one (strong colour blocks) and a
balanced one (muted, related colours). The bold one was too loud for an admin tool used all day;
the calm one had a single accent and little structure. The balanced one keeps the shape language
of both and passes WCAG AA for every text pair (spec 005 section 4.2).

Behaviour is not touched. Nine new criteria (AC-UI-1 to AC-UI-9) make the look testable; the one
changed criterion is AC-STYLE-1, the old style check, which now asserts the new values.

Two-reviewer challenge (2026-10-13, simplicity and robustness bias): 20 findings, most of them
shared. Fixed in the draft:

- The closed colour-pair list became a 4.5:1 rule with the measured pairs; three missing pairs were added.
- The flag dots were dropped, because they repeated "n of m flags on".
- The pie rounding is now integer maths with examples.
- The table no longer has a fixed minimum width, and the groups panel wraps earlier. No table scrolls sideways from 768 px up, checked in the designs at 768, 800, 1024, 1100 and 1440 px.
- All user text wraps. Header shapes stay out of the text column and are hidden below 1100 px.
- The confirm dialog scrolls on short windows.
- The focus ring was added, white on ink.
- The fonts must load from the UI origin.
- AC-UI-6 got an ARIA snapshot from before the redesign.
- Cross-references were corrected, and design 6 had an undefined token.

Rejected with a reason: the light off-switch track. The `On` / `Off` text next to every switch carries the state.

Kept for the owner: section 10 items 2 to 5. Decided 2026-10-15: item 2 AC-STYLE-1 (owner PR #40);
item 3 (a), designs copied over `docs/design/`; item 4 (a), the Condensed font package; item 5 the
next free minor version, the same rule for every change spec (`docs/SPEC.md` 9.6).

## What changes

- `docs/SPEC.md` section 8 "Visual reference", 8.3 to 8.6 appearance, 10.2 font packages, 11.6
  style checks and baselines (spec 005 section 1).
- AC-STYLE-1 is re-expressed (spec 005 section 5). It is the spec 001 style check of 11.6 item 1,
  which had no ID until the owner PR of 2026-10-15 gave it one, so that gate 16 can allow the
  rewrite of `frontend/e2e/style.spec.ts`.
- New spec folder `docs/specs/005-ui-redesign/` (milestone M12), with the design files.

## Consequences

- One new frontend package (`@fontsource/ibm-plex-sans-condensed`, same publisher as the Plex
  packages already used).
- The five screenshot baselines (gate 15) and the style test (AC-STYLE-1, gate 16) are updated by
  the builder.
- `docs/design/` is replaced by the new designs (owner step, spec 005 section 10 item 3, done
  2026-10-15). Until M12 is merged, `main` shows the new designs with the old UI.
