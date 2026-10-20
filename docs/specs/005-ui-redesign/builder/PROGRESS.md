# Progress log

Append-only. One line per commit: date · commit · milestone · chunk · verify result.
The SHA of a row is filled in by the next commit (a commit cannot contain its own SHA).

| Date | Commit | M | Chunk | Verify |
| --- | --- | --- | --- | --- |
| 2026-10-20 | 034bd19 | M12 | memory files | verify-fast: all gates pass except gate 14 (AC-CACHE-9, AC-OPS-1, AC-OPS-2: e2e-only, no local Playwright report yet) |
| 2026-10-20 | 8f4a50a | M12 | AC-UI-6 ARIA snapshots recorded from the pre-redesign code (D-003) | verify-fast: as baseline (gate 14 only AC-CACHE-9, AC-OPS-1/2: e2e-only); aria.spec 10/10 on a local stack |
| 2026-10-20 | d95afcd | M12 | font package, tokens, global styles, shared components; style.spec to AC-STYLE-1 v2; e2e tests AC-UI-1, 2, 3, 9 | verify-fast: as baseline; local e2e chromium-desktop + narrow 90/91 (only the old screenshot baselines fail, re-recorded in phase C) |
| 2026-10-20 | 4a662ee | M12 | screens: shell, sign-in art panel, workspace (pie, header card, row cards), dialogs, audit labels; tests AC-UI-4, 5, 6 | verify-fast: as baseline; local e2e design.spec 46/46 (desktop + narrow); AC-UI-4/5 seen failing on the pre-redesign UI |
| 2026-10-20 | 39b0c99 | M12 | header shapes in a clipping layer; e2e tests AC-UI-7, AC-UI-8 | verify-fast: as baseline; local e2e chromium-desktop + narrow, repeat-each 2: all pass except the old screenshot baselines |
| 2026-10-20 | e0d3482 | M12 | five screenshot baselines re-recorded (D-005), design-compare path and gallery (D-006), search icon colour from a token | verify-fast: as baseline (gates 15, 16 pass with the new baselines); screenshots + aria 20/20 with repeat-each 2 |
| 2026-10-20 | 15497d2 | M12 | merge origin/main (owner contract, flow changes) | verify-fast: as baseline |
| 2026-10-20 | cb7c7e9 | M12 | CHANGELOG Unreleased entry; ESC-001 raised (release version vs "no backend file changes") | verify-fast: as baseline |
| 2026-10-20 | 88e014a | M12 | milestone audit report (before fixes) | docs only |
| 2026-10-20 | 5a829fa | M12 | ARIA snapshots of the other screens recorded from spec-005-ready, strict compare (D-007) | verify-fast: as baseline; aria.spec 48/48 on the spec-005-ready UI |
| 2026-10-20 | bd38324 | M12 | audit fixes: 11 screens in the tests, exact long data, fonts loaded, cells below 860 px, null boxes, every switch; Key field 52 px, busy Sign in, Button dark, tokens for white fills; baseline 3 (D-008) | verify-fast: as baseline; local e2e chromium-desktop + narrow repeat-each 2 all green; planted bugs TA-2, TA-3 caught |
| 2026-10-20 | bc11f60 | M12 | owner amendment: ESC-001 answered A | (owner) |
| 2026-10-20 | (this) | M12 | release 1.2.0 (ESC-001 A), footer version in the ARIA compare (D-011), MILESTONE = 12 complete | verify-fast see below; local e2e 562/566 in one run, the 4 fails were reused-stack audit counts, 36/36 on a fresh stack |
