# State

Keep under 150 lines. Older detail: `docs/PROGRESS.md`.

## Current

- Milestone: M3 Auth — in progress
- `scripts/current-milestone`: 3

## Last green commit

- ba19b68 (M2 audit fixes; full `make verify` PASS). `M2: complete` follows it.

## Last full `make verify`

- 2026-10-04 on ba19b68: PASS. Active and green: 1, 2, 3, 4, 5, 6, 10 (incl. image scan), 14, 15.
  Inactive: 7, 8 (M4), 9 (M6), 11 (M5), 12, 13 (M8).

## Chunks done in M2

- ac86ad1 Flyway V1 (`db/migration/common`) + V2 seed (`db/migration/dev`), MigrationIT, health body, ArchitectureTest.
- 01f65fc common: strict JSON types, UUIDv7, Clock, exceptions, GlobalExceptionHandler (9.1).
- a0e498c / 2b3a6b7 entities, repositories, JPA auditing (a0e498c was pushed red on format; fixed next).
- 2620c39 DTO records (6.2) and request validation (4.2).
- ba19b68 audit fixes: SecurityAuditor, RequestIdFilter, framework errors, profile seed IT, ArchUnit fixtures.

## Next 3 steps

1. M3: set `scripts/current-milestone` to 3. Add `spring-boot-starter-security` and
   `-oauth2-resource-server`; `ClientRegistrationProperties` + auth config (5.1, fail fast on short
   secret / duplicate client id, prod WARN on defaults); `TokenIssuer` (NimbusJwtEncoder, HS256).
2. M3: `AuthController` (login 5.2), `TokenController` (client credentials 5.5, OAuth errors,
   Basic auth, `Cache-Control: no-store`), `SecurityConfig` (5.4 rules, decoder with issuer +
   audience validators, skew 0, 401/403 problem bodies with WWW-Authenticate), CORS, https-required
   filter (10.2, FF_REQUIRE_HTTPS default true in prod — audit SA-11 for compose), Cache-Control
   no-store on admin responses. Tests tagged AC-EVAL-1/2/4 and ERR-POST-/auth/login-*, -/auth/token-*.
3. M3 end: `make verify`, audit (spec-auditor, test-auditor, security-reviewer, ecc:java-reviewer).
   `HealthIT.otherJsonResponsesPassTheHealthFilterUnchanged` uses an unknown path: it becomes 401
   in M3 (5.4 "everything else") — adjust with a DECISIONS note.

## Notes for M4/M5 (from the M2 audit)

- Packages: common <- audit <- group <- flag? No: direction is group -> flag. `flag` defines a
  `GroupLookup` port implemented in `group`; GroupDetail uses the `Flag` DTO.
- Limits (9.2): lock the group row when counting flags; group count under a lock too (DB-5).
- Audit `targetKey` prefix: escaped LIKE so the varchar_pattern_ops index is used (DB-2).
- Audit `details`: build with LinkedHashMap (JSON nulls), never Map.of.
- ArchUnit: remove `allowEmptyShould(true)` once controllers / evaluation classes exist (TA-10).
- AC-GRP-3 needs MockMvc tests (POST /groups and /flags with Orders, 1abc, a, has space) + UI tests.
- M5: `DatabaseCleaner` must also call `FlagCacheService.reloadAll()` (11.5, TA-14).
- M5: gauge `ff_readiness_up` (D-009); histogram for `/api/v1/evaluate/**`; smoke checks readiness
  DOWN via a 503 or the log lines `readiness DOWN until cache warm-up` / `cache warm-up finished`.

## Open escalations and blockers

- ESC-001 resolved: A (owner comment 5979791924). Due milestones may only move earlier.
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
