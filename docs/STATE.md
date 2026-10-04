# State

Keep under 150 lines. Older detail: `docs/PROGRESS.md`.

## Current

- Milestone: M5 Evaluation API — next (M4 complete)
- `scripts/current-milestone`: 4 (set to 5 at the start of M5)

## Last green commit

- a486511 (M4 audit fixes; full `make verify` PASS). `M4: complete` follows it.

## Last full `make verify`

- 2026-10-04 on a486511: PASS. Active and green: 1-8, 10, 14, 15. Inactive: 9 (M6), 11 (M5), 12, 13 (M8).

## Chunks done in M4

- 400b82a Admin API (groups, flags, audit, purge, events, springdoc, openapi.json, schema.d.ts).
- a486511 audit fixes: OpenAPI optionality, audit paging edge, flag row locks, purge log, tests.

## Next 3 steps

1. M5: set `scripts/current-milestone` to 5. Drafts of `FlagCacheService`, `EvaluationQueries`,
   `EvaluationController`, `CacheReconciliationJob`, `CacheWarmUp` exist only in the old session's
   scratchpad; rebuild them from spec 7 if missing. Tests first: unit (cache hit/miss/load,
   negative 30 s with a Clock-based ticker, concurrent miss = one query, write-through per change
   type, failure -> invalidate, revision), ITs tagged AC-EVAL-3/5/6/7, AC-CACHE-1..8 with a
   `@MockitoSpyBean` on `EvaluationQueries` counting queries.
2. M5: `DatabaseCleaner` also calls `FlagCacheService.reloadAll()`; `ff_evaluations_total`, client id
   in the request log, `ff_readiness_up`, reconcile metrics, `featureflags.cache.reconcile-cron`
   (`FF_CACHE_RECONCILE_CRON`, default `0 0 3 * * *`), percentiles histogram for http requests.
3. M5 end: gate 11 smoke (`scripts/smoke.sh`) must pass; `make verify`; audit (spec, test,
   security, java reviewers).

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
