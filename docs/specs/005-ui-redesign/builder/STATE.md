# State — spec 005 (UI redesign)

## Current milestone and chunk

M12 complete. Release 1.2.0. verify-all PASS in CI on 16f86ae. PR ready for the owner.

## Last green commit and last full verify

- Last green commit: 16f86ae (CI `make verify-all` PASS, all 16 gates, 2026-10-20); report in
  `builder/verify-report.md` (D-012).

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

1. Nothing left for the builder. Owner: push the tag `spec-005-amend-2` on bc11f60 (the
   locked-files guard lists bc11f60 until then), run `scripts/owner-review.sh`, review, merge.
2. If the owner comments: follow CLAUDE.md 8b (eyes, apply, rocket).
3. If main moves before the merge: merge origin/main, `make verify-all` (CI), push.

## Open escalations and blockers

- ESC-001 resolved: A (owner comment 6292488310, amendment bc11f60). None open.

## wip/ branches

None.

## Last processed PR comment

6292694903 (2026-10-20 14:02, builder: RESOLVED ESC-001). Owner 6292488310: eyes and rocket given. Task issue: #36.

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
