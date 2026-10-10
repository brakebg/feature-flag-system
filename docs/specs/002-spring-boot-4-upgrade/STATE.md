# State — spec 002 (Spring Boot 4 upgrade)

Keep under 150 lines.

## Current

- Milestone: M9, phase A (prepare, green on Spring Boot 3.5)
- Branch: `feature/spring-boot-4`
- Last processed PR comment: none (no PR yet)

## Last green commit

- 1bbf019 (A2). `make verify-fast` PASS.

## wip/ branches

- none yet (phase B will use `wip/spring-boot-4`)

## Next 3 steps

1. A1: compile with -Xlint:deprecation, remove APIs deleted in Boot 4 / Spring 7 / Security 7.
2. A1: compile with deprecation warnings, remove APIs deleted in Boot 4 / Security 7.
3. B0: docs-only commit naming wip/spring-boot-4, then phase B.

## Open escalations

- none
