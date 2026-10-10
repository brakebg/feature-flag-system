# State — spec 002 (Spring Boot 4 upgrade)

Keep under 150 lines.

## Current

- Milestone: M9, phase A (prepare, green on Spring Boot 3.5)
- Branch: `feature/spring-boot-4`
- Last processed PR comment: none (no PR yet)

## Last green commit

- Baseline: `make verify-fast` PASS on main (f51ca83 + 2 owner docs commits).

## wip/ branches

- none yet (phase B will use `wip/spring-boot-4`)

## Next 3 steps

1. A2: put all Jackson use of main code behind one config class, tests behind one helper.
2. A1: compile with deprecation warnings, remove APIs deleted in Boot 4 / Security 7.
3. A3: other section 9 items that already work on Boot 3.5; then B0.

## Open escalations

- none
