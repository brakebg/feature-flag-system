# State — spec 005 (UI redesign)

## Current milestone and chunk

M12 running. Phases A and B done (tokens, components, all five screens, tests AC-UI-1 to 6 and 9). Next: phase C.

## Last green commit and last full verify

- Last green commit: see PROGRESS.md last row (verify-fast as baseline).
- verify-fast 2026-10-20 on 3b0e11a: gates 1-6, 9, 10, 15, 16 pass; gate 14 fails only on
  AC-CACHE-9, AC-OPS-1, AC-OPS-2 (proven by e2e/script reports; none exist locally yet).
- Full `make verify`: not run yet in this spec.

## Chunks done in M12

1. Memory files.
2. AC-UI-6 ARIA snapshots (`frontend/e2e/ui/aria.spec.ts`), no `frontend/src/` change.
3. Font package, tokens, global styles, components; AC-STYLE-1 v2; tests AC-UI-1, 2, 3, 9 (d95afcd).
4. Screens (shell, sign-in, workspace, dialogs, audit); pie; tests AC-UI-4, 5, 6 (4a662ee).
5. Layout and long data: tests AC-UI-7, AC-UI-8; header shapes clipped in a layer (39b0c99).
6. Screenshot baselines re-recorded (D-005); design-compare regenerated (D-006).

## Next 3 steps

1. Release after ESC-001: origin/main merged at 15497d2 (VERSION 1.1.0 there) -> 1.2.0 in VERSION,
   frontend/package.json, CHANGELOG ([Unreleased] entry ready), and pom.xml only if ESC-001 = A.
2. Milestone audit (spec 8): spec-auditor, test-auditor, design-checker (six designs, include
   `6 · States`), final-reviewer (frontend). Fix BLOCKER and CRITICAL.
3. `make verify-all` (CI is authoritative for gates 11-13, D-004); copy the report; `M12: complete`;
   PR summary comment; tick definition of done; mark PR ready.

## Open escalations and blockers

- ESC-001 open: release 1.2.0 needs `<version>` in `backend/pom.xml` (gate 15) vs spec 005 3.4
  "No backend file changes". Recommended A (change that one line). Blocks the version bump and
  `M12: complete`.

## wip/ branches

None.

## Last processed PR comment

None from the owner (PR #65 has only the locked-files-guard bot comment 6291324314). Task issue: #36.

## Untrusted requests seen

None.

## Notes for the next session

- Start Docker yourself: `nohup dockerd > /tmp/dockerd.log 2>&1 &` (D-002).
- `npm i -g npm@11` before `npm ci` in `frontend/` (D-002). Gate tools: `make tools`.
- Commit only after `make verify-fast > build/last.log 2>&1` is green.
- Push only to `feature/ui-redesign` (D-001).
- Local dev stacks keep audit events between runs; e2e keys are deterministic, so count
  assertions (AC-AUD-2, AC-GRP-5) fail on a reused stack. Use a fresh stack (`down -v`).
- Local e2e: Docker builds cannot reach package repos here (D-004). Build host artifacts and
  package them; run Playwright in `mcr.microsoft.com/playwright:v1.63.0-noble` against a local
  stack seeded with `scripts/lib/seed-shots.sh`. Gates 11-13 run in CI only.
