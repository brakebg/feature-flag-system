# Decisions

Level 1 decisions of the builder for spec 005 (`docs/process/escalation.md` 12.5) and answers to
escalations. Owner decisions live in `SPEC.md` section 10 and `decisions/`.

Format: ID · date · spec section · decision · why.

## D-001 · 2026-10-20 · CLAUDE.md 9, kickoff prompt · Push only to `feature/ui-redesign`

The cloud session was started with a session branch (`claude/...`). The kickoff prompt
(`docs/process/builder-prompt.md` section 5) says: work only on the spec's working branch and
never push to a session branch. All work goes to `feature/ui-redesign`.

## D-002 · 2026-10-20 · environment · Docker daemon and npm 11 in the cloud container

The container has no running Docker daemon at start; the builder starts `dockerd` itself.
`npm ci` needs npm 11 (lock file v3, spec 001 D-010); the container has npm 10, so the builder
runs `npm i -g npm@11` first. No repository file changes.

## D-003 · 2026-10-20 · 4.8, AC-UI-6 · ARIA snapshots recorded before the redesign

The commit "M12 aria: record the ARIA snapshots of the five screens before the redesign"
(the first builder commit after 034bd19) adds `frontend/e2e/ui/aria.spec.ts` and its five
snapshots in `frontend/e2e/ui/aria.spec.ts-snapshots/` (`1-sign-in` … `5-audit-log` `.aria.yml`).
It touches no `frontend/src/` file. Recorded at 1440 x 900 on the seeded screenshot stack
(`scripts/lib/seed-shots.sh`) from the code at 034bd19 (pre-redesign), project `chromium-desktop`.
Edits after recording, all patterns only: relative times as
`(?:just now|\d+ (?:second|minute|hour|day|month|year)s? ago)`, the audit clock `[AP]M`, one
wrong auto-pattern turned back into the literal `notifications\.sms-shipping-updates`; then
`prettier --write` (indentation only, gate 1). The snapshot file names have no project suffix, so
`chromium-narrow` checks the same snapshots; the file sets the viewport to 1440 x 900.
These files never change after this commit.

## D-004 · 2026-10-20 · environment · Local Docker images built from host artifacts

In this cloud container, Docker builds cannot reach package repositories (Docker Hub rate limit,
alpine repositories blocked, no TLS trust for the egress proxy inside containers). Base images
come from `mirror.gcr.io`. For local e2e runs the builder builds the jar and the UI bundle on the
host and packages them in images that copy the runtime stages of the repo Dockerfiles without the
OS package upgrade (dev helper outside the repo). So locally gates 11, 12, 13 and the image scan
of gate 10 cannot run through `make verify`; CI (`make verify-all` in `.github/workflows/ci.yml`)
runs them on every push. No repository file changes for this.

## D-005 · 2026-10-20 · 4.1, testing.md 11.6 item 2 · Screenshot baselines re-recorded for the redesign

Reason for all five: spec 005 replaces the look of every screen (designs 1 to 5 of
`docs/product/design/`). Recorded with `--update-snapshots` on `chromium-desktop` in the official
Playwright image (`mcr.microsoft.com/playwright:v1.63.0-noble`) against a fresh stack seeded by
`scripts/lib/seed-shots.sh`, UI built from the redesign code; then checked with `--repeat-each=2`.

- Screen 1, sign in: `1-sign-in-chromium-desktop-linux.png` (art panel, dark Sign in button).
- Screen 2, flags workspace: `2-flags-workspace-chromium-desktop-linux.png` (pill top bar, pie,
  header card, row cards).
- Screen 3, new flag dialog: `3-new-flag-dialog-chromium-desktop-linux.png` (dialog shapes,
  52 px fields, initial state shape).
- Screen 4, delete group confirmation: `4-delete-group-confirmation-chromium-desktop-linux.png`
  (shape cluster, flag list marks).
- Screen 5, audit log: `5-audit-log-chromium-desktop-linux.png` (ink header card, row cards,
  labels with shapes).

## D-006 · 2026-10-20 · 4.8 · `scripts/design-compare.sh` reads `docs/product/design/`

The gallery linked the design PNGs at `../design/`, the folder before the docs split. It now links
`../product/design/` (spec 005 section 4.8 names this fix). `docs/design-compare/` regenerated
from the new baselines.

## D-007 · 2026-10-20 · 4.8, AC-UI-6, audit SA-1, TA-1, TA-5 · All screens of the criteria, strict ARIA comparison

The criteria file (`docs/project-acceptance-criteria.md`, "UI redesign") says "the screens" means
11 states: the five design screens plus `/groups` with no groups, a group with no flags, the 404
page and the New group, Edit group, Edit flag and Delete flag dialogs.
`docs/product/design/README.md` (amend-1) calls only the five design screens "the screens". The
builder tests the 11 states. That covers both texts, so no doc has to be chosen over the other.

Recording, in the commit "M12 aria: record the ARIA snapshots of the other screens from the
spec-005-ready build" (touches no `frontend/src/` file):
- The six new `*.aria.yml` files (`6-no-groups` … `12-delete-flag-dialog`) and the
  `*-states-<project>-linux.txt` files of all 12 screens were recorded with
  `--update-snapshots=missing`. The UI was built from the tag `spec-005-ready` (release 1.1.0 UI,
  the build the criterion names), at 1440 x 900. The five screens of D-003 and the dialogs ran on
  the seeded screenshot stack. "group without flags" ran on the main stack: its group list depends
  on other tests, so only its `main` landmark is compared.
- Only patterns were edited after recording: relative times as in D-003, and the group key of
  `7-group-without-flags` as `/e2e-[a-z0-9-]+/`. The five files of D-003 did not change.
- `aria.spec.ts` now compares each recorded file with `- /children: deep-equal`, so an extra or
  missing node fails ("equals", TA-1). It also compares a list of every `aria-checked`,
  `aria-pressed`, `aria-current` and `aria-invalid` value, including false values (TA-1, TA-5).
  All 12 screens passed twice in both chromium projects against the `spec-005-ready` build.
- Screenshot baselines are re-recorded only with `--update-snapshots` limited to
  `screenshots.spec.ts` (TA-7). These snapshot files never change after this commit.
