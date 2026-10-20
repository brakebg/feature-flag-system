# State — spec 005 (UI redesign)

## Current milestone and chunk

M12 running. Phase A, chunk 1: memory files. Status: done.

## Last green commit and last full verify

- Last green commit: 3b0e11a (owner, spec-005-amend-1). Builder commits follow.
- verify-fast 2026-10-20 on 3b0e11a: gates 1-6, 9, 10, 15, 16 pass; gate 14 fails only on
  AC-CACHE-9, AC-OPS-1, AC-OPS-2 (proven by e2e/script reports; none exist locally yet).
- Full `make verify`: not run yet in this spec.

## Chunks done in M12

1. Memory files.

## Next 3 steps

1. Phase A, AC-UI-6: add `frontend/e2e/ui/aria.spec.ts` (toMatchAriaSnapshot of the five screens on
   the seeded shots stack, 1440 x 900), record the snapshots from the current code with
   `FF_UPDATE_BASELINES=1 scripts/e2e.sh`, turn relative times into patterns, commit with no
   `frontend/src/` change, name the commit in DECISIONS.md.
2. Open the draft PR `feature/ui-redesign` -> `main` (body: `Closes #36`).
3. Phase A styling: `@fontsource/ibm-plex-sans-condensed` 5.3.0, tokens.css (4.2), global.css,
   components (4.4); style.spec.ts to AC-STYLE-1 v2; tests AC-UI-1, 2, 3, 9.

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
