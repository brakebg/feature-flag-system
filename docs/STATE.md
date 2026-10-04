# State

Keep under 150 lines. Older detail: `docs/PROGRESS.md`.

## Current

- Milestone: M6 UI foundation — in progress
- `scripts/current-milestone`: 6

## Last green commit

- 0a935f3 (M5 audit fixes; full `make verify` PASS incl. smoke). `M5: complete` follows it.

## Last full `make verify`

- 2026-10-04 on 0a935f3: PASS. Active and green: 1-8, 10, 11, 14, 15. Inactive: 9 (M6), 12, 13 (M8).

## Chunks done in M5

- a66a808 evaluation cache + API; schema.d.ts regenerated; 9db854d PIT kept out of tag log.
- 0a935f3 audit fixes: reconcile under lock, all-flags race, revision in finally, gauges, tests.

## Next 3 steps

1. M6: set `scripts/current-milestone` to 6. UI foundation per spec 8.1-8.3, 8.5 (toasts), 8.7:
   `src/api/apiClient.ts` (bearer, ApiError from problem details, 401 -> clear token + /login?expired=1),
   `src/auth/token.ts` (sessionStorage `ff.accessToken`, decode exp), `RequireAuth`, routes, app shell
   (top bar, Sign out, footer `v<version>`), login page (8.2 errors incl. 429 Retry-After),
   components Modal/ConfirmDialog/Toggle/Toast/Button/TextField/EmptyState, tokens.css from designs.
2. M6 tests (Vitest + RTL + MSW), tagged [AC-AUTH-1..4, 6]: login errors, success -> /groups,
   no token -> /login, expired -> /login?expired=1, 401 -> redirect, sign out clears token.
3. M6 end: gate 9 + gate 6 (70 % lines on src/features + src/api), `make verify`, audit (spec, test,
   ecc:react-reviewer).

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
