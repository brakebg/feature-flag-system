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
