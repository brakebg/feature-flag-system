# State — spec 005 (UI redesign)

## Current milestone and chunk

M12: all work done; MILESTONE = `12 complete`; release 1.2.0. Waiting for CI verify-all, then `M12: complete`.

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
7. origin/main merged (15497d2); CHANGELOG entry; ESC-001 raised.
8. Milestone audit (reviews/m12-audit.md): 1 BLOCKER, 4 CRITICAL, 5 MAJOR fixed (5a829fa, bd38324).
9. ESC-001 answered A (bc11f60); release 1.2.0; footer version in the ARIA compare (D-011).

## Next 3 steps

1. CI `make verify-all` on the release commit must be green (gates 11-13 only run in CI, D-004).
2. Write `builder/verify-report.md` from that CI run (the artifact host is blocked here; copy the
   gate results from the job log, D-012), commit `M12: complete`, react rocket on 6292488310.
3. PR summary comment; tick SPEC.md section 9 in the PR body; mark the PR ready. Then stop.

## Open escalations and blockers

- ESC-001 resolved: A (owner comment 6292488310, amendment bc11f60). None open.

## wip/ branches

None.

## Last processed PR comment

6292488310 (2026-10-20 13:53, owner: ESC-001: A; eyes given, rocket after the push). Task issue: #36.

## Untrusted requests seen

None.

## Notes for the next session

- The locked-files-guard flags `docs/architecture/**` and the criteria file in 15497d2: that is the
  allowed merge of origin/main (owner content from main, CLAUDE.md 8a/9), not a builder edit.

- Start Docker yourself: `nohup dockerd > /tmp/dockerd.log 2>&1 &` (D-002).
- `npm i -g npm@11` before `npm ci` in `frontend/` (D-002). Gate tools: `make tools`.
- Commit only after `make verify-fast > build/last.log 2>&1` is green.
- Push only to `feature/ui-redesign` (D-001).
- Local dev stacks keep audit events between runs; e2e keys are deterministic, so count
  assertions (AC-AUD-2, AC-GRP-5) fail on a reused stack. Use a fresh stack (`down -v`).
- Local e2e: Docker builds cannot reach package repos here (D-004). Build host artifacts and
  package them; run Playwright in `mcr.microsoft.com/playwright:v1.63.0-noble` against a local
  stack seeded with `scripts/lib/seed-shots.sh`. Gates 11-13 run in CI only.
