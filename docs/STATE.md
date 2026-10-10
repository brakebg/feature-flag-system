# State

Keep under 150 lines. Older detail: `docs/PROGRESS.md`.

## Current

- Milestone: M8 complete; final review done (round 3); ESC-002..006 answers applied (D-040);
  main merged (decision 0007, gate 13 p95 report-only, D-041)
- `scripts/current-milestone`: 8

## Last green commit

- This commit (sign out Back fix D-046). `make verify-all` PASS.

## Last full `make verify`

- 2026-10-10 `make verify-all`: PASS, gates 1-15 (p95 2.9 ms). docs/verify-report.md.

## Chunks done in M7

- 7b44d8a UI features; 1f8b482 Playwright style checks + 5 baselines (D-028).
- c6b3d3f load/error states (D-029), toggle rollback/toast at mutation level.
- Audit fixes: tests (TA-1..13), design (D-030, baselines re-recorded), 400 fallback toast.

## Next 3 steps

0. Done: owner bugs 6021814988 (D-042) and 6021815278 (D-043) fixed and on the feature branch;
   ESC-008/009 resolved (D-044, D-045, spec 11.3 table, decisions/0008).
   Sign out adds a history entry, so Back shows /login again (8.3, D-046, owner request).
1. Done: ESC-002..006 applied (D-040), main merged + gate 13 report-only (D-041), verify-all PASS.
2. PR #3 marked ready; final summary posted. Wait for the owner review.
3. Optional (owner choice): fix the intermittent e2e host-port stall (see Open escalations).

## Final review status

- docs/reviews/final-review.md, round 3 done. CRITICAL: 8 fixed, 1 rejected (SA-4, accepted by
  the re-check). BLOCKER: SA-1 fixed; SA-2 fixed (D-040). No CRITICAL open.
- Last `make verify-all`: PASS on e670c3c (all 15 gates); copied to docs/verify-report.md.

## M8 notes

- e2e: D-031 (projects, keys, OPS-1/2 scripts, nginx Host and Zod CSP fixes). Perf: D-032.
- README and CHANGELOG 1.0.0 written; README curl examples were run against a fresh stack.

## M7 audit result

- spec-auditor: no BLOCKER/CRITICAL; SA-1 (baselines listed, D-030), SA-2/3 (D-029), SA-5 fixed.
  SA-4: tests for 8.5 texts with no AC stay untagged (no AC exists for them).
- test-auditor: TA-1 BLOCKER, TA-2/TA-3 CRITICAL and all MAJOR/MINOR fixed; planted bugs now fail.
- design-checker: DC-1 CRITICAL fixed (D-029); DC-2..8 fixed (D-030); DC-9 (one live region per
  toast) and DC-10 (footer under the left pane) left as they are: spec 8.5 roles are met, 9.6 footer.
- react-reviewer: 3 MAJOR fixed; MINOR 4, 5 fixed; 6 (focus after disabled switch), 7 (Escape while
  a dialog request runs), 8 (audit filter per keystroke) left: spec allows them.

## Notes for M4/M5 (from the M2 audit)

- Packages: common <- audit <- group <- flag? No: direction is group -> flag. `flag` defines a
  `GroupLookup` port implemented in `group`; GroupDetail uses the `Flag` DTO.
- Limits (9.2): lock the group row when counting flags; group count under a lock too (DB-5).
- Audit `targetKey` prefix: escaped LIKE so the varchar_pattern_ops index is used (DB-2).
- Audit `details`: build with LinkedHashMap (JSON nulls), never Map.of.
- ArchUnit: remove `allowEmptyShould(true)` once controllers / evaluation classes exist (TA-10).
- AC-OPS-4: backend tests cover https-required; the UI header half is covered by smoke (gate 11, M5).
- AC-GRP-3 needs MockMvc tests (POST /groups and /flags with Orders, 1abc, a, has space) + UI tests.
- M5: `DatabaseCleaner` must also call `FlagCacheService.reloadAll()` (11.5, TA-14).
- M5: gauge `ff_readiness_up` (D-009); histogram for `/api/v1/evaluate/**`; smoke checks readiness
  DOWN via a 503 or the log lines `readiness DOWN until cache warm-up` / `cache warm-up finished`.

## Open escalations and blockers

- ESC-001 resolved: A (owner comment 5979791924). Due milestones may only move earlier.
- ESC-007 resolved: A (owner comment 5992802206), applied in D-039.
- ESC-002..006 resolved (owner comments 5980536047, 5980536322, 5980648922, 5989504944,
  5989505154; D-040). ESC-004 item 2 changed the code: env clients are appended after yml ones.
- ESC-008, ESC-009 resolved (D-044, D-045). Open escalations: none. Blockers: none.
- Known intermittent e2e failure (not fixed): verify-all on 16ae98e, gate 12: two
  chromium-narrow tests (AC-AUD-2, AC-FLAG-5) timed out in the `api` fixture. Their
  `POST /auth/login` to host.docker.internal:38080 never reached the backend (backend log shows
  other requests served in the same seconds). The serial project (AC-EVAL-6) did not run.
  A re-run of `scripts/e2e.sh` alone passed 82/82. Likely the Docker Desktop host port
  forward. Possible fix: run the Playwright container in the stacks' networks.

## wip/ branches

- `wip/owner-bugs`: merged into the feature branch (fast-forward). Not deleted (Level 3).

## Last processed PR comment

- 6032965071 (2026-10-07, builder ESC-008). Owner bug reports 6021814988, 6021815278: rocket.
  ESC-009 answered by the owner through the spec change on main (decisions/0008).

## Notes for the next session

- Owner and builder share the GitHub login `brakebg`. Owner answers look like `ESC-NNN: X`.
  Read ALL comments after the last processed id; answers to ESC-002..006 were missed once.
- Commit only after `make verify-fast > build/last.log 2>&1 && git commit ...` (a0e498c went in red).
- Java 21 via `scripts/lib/java-env.mjs` (default java here is 25; Enforcer requires 21).
  Manual: `JAVA_HOME=$(node scripts/lib/java-env.mjs) ./mvnw ...` in `backend/`.
- Gate tools in `build/tools/` (`make tools`). Node 26 locally, tests need >= 22 (D-006). npm 11 (D-010).
- Design markup: `docs/design/N · *.html`, inside `<script type="__bundler/template">` (JSON string).
