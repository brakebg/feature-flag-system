# State

Keep under 150 lines. Older detail: `docs/PROGRESS.md`.

## Current

- Milestone: M8 Hardening — next (M7 complete)
- `scripts/current-milestone`: 7

## Last green commit

- e411091 (M7 audit fixes; full `make verify` PASS). `M7: complete` follows it.

## Last full `make verify`

- 2026-10-05 on e411091: PASS. Active and green: 1-11, 14, 15. Inactive: 12, 13 (M8).
  e2e (scripts/e2e.sh, gate 12 from M8) 18/18 x2 on the audit-fix commit.

## Chunks done in M7

- 7b44d8a UI features; 1f8b482 Playwright style checks + 5 baselines (D-028).
- c6b3d3f load/error states (D-029), toggle rollback/toast at mutation level.
- Audit fixes: tests (TA-1..13), design (D-030, baselines re-recorded), 400 fallback toast.

## Next 3 steps

1. M8: set `scripts/current-milestone` to 8. Playwright e2e for every 11.2 scenario (gate 12,
   `--repeat-each=2`, limits stack, cross-browser tag), AC-OPS-1/2 tests, k6 perf (gate 13).
2. M8: README (setup, env vars, single-instance rule, curl, client polling, default false on
   404/network), CHANGELOG 1.0.0, Dockerfiles final; then the M8 audit.
3. Final review (builder-agents 6) into docs/reviews/final-review.md; commit docs/verify-report.md.

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
- ESC-006 (open): PIT runs kept out of the tag log (D-024); smoke readiness step (D-025).
- ESC-005 (open): PIT unit tests only (D-020); duplicate-key before limit-reached (D-021).
- ESC-004 (open): token endpoint error order (D-018); FF_AUTH_CLIENTS_n replace the yml list.
- ESC-003 (open): NimbusJwtEncoder writes one-element aud as string (5.1 vs 5.2); option A (HmacJwtEncoder) in place.
- ESC-002 (open): D-011 PATCH `enabled: null` -> 400 validation; D-012 unknown method -> 404. Not blocking.

## wip/ branches

- None.

## Last processed PR comment

- 5979791924 (2026-10-04T12:12:17Z, owner: ESC-001: A).

## Notes for the next session

- Owner and builder share the GitHub login `brakebg`. Owner answers look like `ESC-NNN: X`.
- Commit only after `make verify-fast > build/last.log 2>&1 && git commit ...` (a0e498c went in red).
- Java 21 via `scripts/lib/java-env.mjs` (default java here is 25; Enforcer requires 21).
  Manual: `JAVA_HOME=$(node scripts/lib/java-env.mjs) ./mvnw ...` in `backend/`.
- Gate tools in `build/tools/` (`make tools`). Node 26 locally, tests need >= 22 (D-006). npm 11 (D-010).
- Design markup: `docs/design/N · *.html`, inside `<script type="__bundler/template">` (JSON string).
