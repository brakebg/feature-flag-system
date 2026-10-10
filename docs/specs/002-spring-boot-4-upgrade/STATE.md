# State — spec 002 (Spring Boot 4 upgrade)

Keep under 150 lines.

## Current

- Milestone: M9, phase A done after this commit (A1, A2 done). Next: A3 then B0.
- Branch: `feature/spring-boot-4`; draft PR #13
- Last processed PR comment: none (no owner comments yet)

## Last green commit

- A1 commit (see PROGRESS.md). `make verify-fast` PASS.

## wip/ branches

- none yet (phase B will use `wip/spring-boot-4`)

## Next 3 steps

1. A3: check section 9 items that already work on 3.5 (Testcontainers 1.21 API is not in 2.x
   so cannot move yet; look at `spring.jackson`/Flyway/Tomcat settings). If nothing, go on.
2. B0: docs-only commit "phase B runs on wip/spring-boot-4", push. Create `wip/spring-boot-4`.
3. B1 step 1: pom to Boot 4.1 + starters; compile; checkpoint after each step.

## Open escalations

- none
