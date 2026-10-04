# State

Keep under 150 lines. Older detail: `docs/PROGRESS.md`.

## Current

- Milestone: M2 Schema and domain — in progress
- `scripts/current-milestone`: 2

## Last green commit

- 0c77c99 (M1 audit fixes; `make verify` PASS). `M1: complete` follows it.

## Last full `make verify`

- 2026-10-04 on 0c77c99 tree: PASS. Gates 1, 2, 4, 10 (gitleaks, trivy fs, trivy images), 14, 15 ran
  green; 3, 5-9, 11-13 inactive (M1).

## Chunks done in M2

- Flyway V1 (`db/migration/common`) and V2 seed (`db/migration/dev`), `MigrationIT`; `HealthBodyFilter`
  + `HealthIT` (AC-OPS-3, audit SA-7); `ArchitectureTest` (gate 3).
- common: `JacksonConfig` (no scalar coercion), `UuidV7`, `ClockConfig`, exceptions, `Problems`,
  `GlobalExceptionHandler` with tests.
- Entities + repositories + JPA auditing (`JpaAuditingConfig`, Clock-based, micros); `RepositoryIT`
  uses a test `AuditorAware` (the real one, from the JWT `sub`, comes in M3).

## Next 3 steps

1. Package rule to avoid cycles: common <- audit <- group <- flag <- evaluation; group reads
   flags through a port interface in `group` implemented in `flag`.
2. M2: DTO records (6.2) with JSON tests (optional fields omitted) and request validation tests
   (trim name, code-point lengths, key regex; PATCH: Optional fields, null vs omitted).
3. M2 end: `make verify`, audit (spec-auditor, test-auditor, ecc:java-reviewer, ecc:database-reviewer).

## Open escalations and blockers

- ESC-001 (open, PR comment 5979739294): confirm gate 14 due milestones (D-003), ERR ID format
  (D-004), compose FF_DB_URL default (D-001). Not blocking; current behaviour kept.

## wip/ branches

- None.

## Last processed PR comment

- 5979739294 (2026-10-04T12:05:31Z, my ESC-001). No owner comments yet.

## Notes for the next session

- Java 21 is found by `scripts/lib/java-env.mjs` (here `~/.sdkman/candidates/java/21.0.12-amzn`);
  default `java` is 25 and Maven Enforcer requires 21. For manual Maven runs:
  `JAVA_HOME=$(node scripts/lib/java-env.mjs) ./mvnw ...`.
- Gate tools (gitleaks, trivy, promtool, k6) live in `build/tools/` (`make tools`).
- Node 26 locally; tests need Node >= 22 (D-006). npm 11 lockfile (D-010).
- M1 audit open items: SA-11 `FF_REQUIRE_HTTPS` prod default in compose (M3).
- M5: export gauge `ff_readiness_up` (D-009); enable http server request histogram for
  `/api/v1/evaluate/**` (FFSlowEvaluation). Smoke checks readiness DOWN via a 503 or the
  log lines `readiness DOWN until cache warm-up` / `cache warm-up finished`.
- Design templates: extract with the python snippet in PROGRESS notes, or read `docs/design/N · *.html`
  (the markup is in `<script type="__bundler/template">`, JSON string).
