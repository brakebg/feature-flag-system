# State — spec 005 (UI redesign)

## Current milestone and chunk

M12 running. Phase A, chunk 2: AC-UI-6 ARIA snapshots recorded (D-003). Status: done.

## Last green commit and last full verify

- Last green commit: see PROGRESS.md last row (verify-fast as baseline).
- verify-fast 2026-10-20 on 3b0e11a: gates 1-6, 9, 10, 15, 16 pass; gate 14 fails only on
  AC-CACHE-9, AC-OPS-1, AC-OPS-2 (proven by e2e/script reports; none exist locally yet).
- Full `make verify`: not run yet in this spec.

## Chunks done in M12

1. Memory files.
2. AC-UI-6 ARIA snapshots (`frontend/e2e/ui/aria.spec.ts`), no `frontend/src/` change.

## Next 3 steps

1. Open the draft PR `feature/ui-redesign` -> `main` (body: `Closes #36`), if not open yet.
2. Phase A styling: `@fontsource/ibm-plex-sans-condensed` 5.3.0, tokens.css (4.2), global.css,
   components (4.4); style.spec.ts to AC-STYLE-1 v2; tests AC-UI-1, 2, 3, 9.
3. Phase B screens (4.5, 4.6), AC-UI-4, 5, 6 (aria-hidden not focusable).

## Open escalations and blockers

None.

## wip/ branches

None.

## Last processed PR comment

None (no PR yet). Task issue: #36.

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
