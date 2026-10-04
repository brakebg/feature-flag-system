# State

Keep under 150 lines. Older detail: `docs/PROGRESS.md`.

## Current

- Milestone: M1 Scaffolding — in progress
- Chunk: scaffold (backend, UI placeholder, compose, verify gates, CI, docs)
- `scripts/current-milestone`: 1

## Last green commit

- 1f? see git log (scaffold pushed)

## Last full `make verify`

- Not run yet.

## Chunks done in M1

- Scaffold: repo layout, Spring Boot app with health, Vite React TS placeholder, docker-compose,
  Makefile, `make verify*` with 15 gates, CI, escalation-notify workflow, AC registry.

## Next 3 steps

1. Push, open the draft PR `feature/feature-flag-service` → `main`.
2. Run full `make verify` (gate 10 image scan), fix findings, then M1 audit (`spec-auditor`).
3. Commit `M1: complete`, post the PR summary; start M2 (Flyway V1/V2, entities, `GlobalExceptionHandler`).

## Open escalations and blockers

- ESC-001 (open): confirm gate 14 due milestones (D-003), ERR ID format (D-004), compose FF_DB_URL default (D-001). Not blocking.

## wip/ branches

- None.

## Last processed PR comment

- None.

## Notes for the next session

- Java 21 is found by `scripts/lib/java-env.mjs` (here: `~/.sdkman/candidates/java/21.0.12-amzn`).
  The default `java` on this machine is 25; Maven Enforcer requires 21.
- Gate tools (gitleaks, trivy, promtool, k6) live in `build/tools/` (`make tools`).
- Node 26 locally; tests need Node >= 22 (D-006).
- For M5: alert `FFNotReady` uses a gauge `ff_readiness_up` (export it); `FFSlowEvaluation`
  needs the http server request histogram for `/api/v1/evaluate/**`.
- For M5: `/actuator/health` shows extra components (diskSpace, ping, ssl); spec 9.3 shows only `db`.
