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
4. Screens (shell, sign-in, workspace, dialogs, audit); pie; tests AC-UI-4, 5, 6.

## Next 3 steps

1. Phase C: e2e tests AC-UI-7 (768/800/1024/1440 widths, no sideways scroll, row actions visible,
   panel above table at <= 1024, art panel hidden < 860) and AC-UI-8 (long data at 1440/1120/800,
   header text vs shapes, 1280 x 600 delete dialog with 30 flags); fix layout until green.
2. Re-record the five screenshot baselines (`FF_UPDATE_BASELINES=1`; one DECISIONS entry per screen),
   regenerate `docs/design-compare/` (`scripts/design-compare.sh`, fix its path to
   `docs/product/design/`).
3. Release: next free minor on main (VERSION, CHANGELOG); milestone audit; merge origin/main;
   verify-all; PR ready.

## Open escalations and blockers

None.

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
- Local e2e: Docker builds cannot reach package repos here (D-004). Build host artifacts and
  package them; run Playwright in `mcr.microsoft.com/playwright:v1.63.0-noble` against a local
  stack seeded with `scripts/lib/seed-shots.sh`. Gates 11-13 run in CI only.
