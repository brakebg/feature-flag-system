# State

Keep under 150 lines. Older detail: `docs/PROGRESS.md`.

## Current

- Milestone: M2 Schema and domain — next (M1 complete)
- `scripts/current-milestone`: 2 (set at the start of M2)

## Last green commit

- 0c77c99 (M1 audit fixes; `make verify` PASS). `M1: complete` follows it.

## Last full `make verify`

- 2026-10-04 on 0c77c99 tree: PASS. Gates 1, 2, 4, 10 (gitleaks, trivy fs, trivy images), 14, 15 ran
  green; 3, 5-9, 11-13 inactive (M1).

## Chunks done in M1

- 8490e7b scaffold: layout, backend health, UI placeholder, compose, Makefile, 15 gates, CI, AC registry.
- 2592573 UI image patched OS packages (gate 10 image scan).
- 42ba3f6 CI uses npm 11.
- 0c77c99 audit fixes: all gate plumbing wired (JaCoCo, Failsafe, PIT, contract, smoke, e2e, perf).

## Next 3 steps

1. M2: set `scripts/current-milestone` to 2. Test first: `MigrationIT` (Testcontainers
   `postgres:16.15-alpine`): V1 applies on an empty DB, V2 seeds only in `dev`, cascade delete,
   unique keys. Draft SQL is described in spec 4.1/4.3 (V1 in `db/migration/common`, V2 in
   `db/migration/dev`; Flyway scans sub-folders, so keep them apart).
2. M2: entities (`AuditableEntity`, `FlagGroup`, `FeatureFlag` with `groupId` only, `AuditEvent`),
   repositories, DTO records (6.2), `GlobalExceptionHandler` (9.1), `ArchitectureTest` (gate 3).
   Package rule to avoid cycles: common <- audit <- group <- flag <- evaluation; group reads
   flags through a port interface in `group` implemented in `flag`.
3. M2: AC-OPS-3 test for `/actuator/health` body (audit SA-7: only `db` should be shown).

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
- M1 audit open items: SA-7 health body (M2), SA-11 `FF_REQUIRE_HTTPS` prod default in compose (M3).
- M5: export gauge `ff_readiness_up` (D-009); enable http server request histogram for
  `/api/v1/evaluate/**` (FFSlowEvaluation). Smoke checks readiness DOWN via a 503 or the
  log lines `readiness DOWN until cache warm-up` / `cache warm-up finished`.
- Design templates: extract with the python snippet in PROGRESS notes, or read `docs/design/N · *.html`
  (the markup is in `<script type="__bundler/template">`, JSON string).
