# Acceptance-criteria traceability

From spec 001 (`docs/SPEC.md`) section 11.4. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

### 11.4 Acceptance-criteria traceability

Every checkbox in 11.2 gets a stable ID by group and position: `AC-AUTH-1…6`, `AC-GRP-1…5`, `AC-FLAG-1…6`, `AC-EVAL-1…7`, `AC-CACHE-1…9`, `AC-AUD-1…3`, `AC-OPS-1…4`, `AC-STYLE-1` (for example, the first Groups checkbox is `AC-GRP-1`).

A criterion ending `· retired by <decision or spec>` keeps its ID so later IDs do not shift. It needs no test, and `check-traceability.mjs` skips it. Currently removed: `AC-AUTH-5` (decision 0002).

- All criteria live in `docs/project-acceptance-criteria.md`, one line each: `**<ID>** v<N> · <text> · spec <NNN>[ · changed by spec <MMM>] · due M<n>`. The version starts at `v1` and goes up by one whenever the text changes; a spec 001 line at `v1` equals its text in 11.2 (gate 15). Black-box acceptance tests name the version they check (`[AC-STYLE-1 v2]`). The builder never edits the file.
- Each test names the IDs it proves: JUnit `@Tag("AC-FLAG-3")`; Vitest and Playwright titles contain `[AC-FLAG-3]`. One test may cover several IDs; one ID may need several tests.
- `check-traceability.mjs` reads the registry plus the JUnit XML, Vitest JUnit and Playwright JSON reports, and writes an ID → tests → pass/fail matrix into the verify report. Every ID needs at least one test, and all its tests must pass.
- In addition, every status code listed in the Errors column of 6.1, and every row of the 9.1 error table, must have an integration test asserting status and problem-detail `type`; the script checks this from tags of the form `ERR-<METHOD>-<path>-<status>`.
- Two items are covered only by this repository's tests, not by the black-box acceptance tests that run outside it: the 500 row of 9.1 (backend integration tests only), and the DOWN state before warm-up in AC-CACHE-6 (backend tests and gate 11). The DOWN period is short; the black-box tests check only that readiness becomes UP and is still UP at the end of the test run (checked once before the first test and once after the last).
- In AC-AUD-1, the black-box tests check `createdBy` / `updatedBy` = the signed-in user and that body fields `createdBy` / `updatedBy` are ignored; that `created_by` stays unchanged when a different user updates a row is covered only by this repository's tests (one admin user exists per stack).
