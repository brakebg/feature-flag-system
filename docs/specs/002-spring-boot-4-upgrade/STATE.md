# State — spec 002 (Spring Boot 4 upgrade)

Keep under 150 lines.

## Current

- Milestone: M9. Phases A and B done. Phase C next (green chunks on `feature/spring-boot-4`).
- Draft PR #13. Last processed PR comment: none (no owner comments yet).
- Platform now: Spring Boot 4.1.1, Spring Framework 7.0.9, Spring Security 7.1.1, Jackson 3.1.7,
  Tomcat 11.0.26, Hibernate 7.4, Flyway 12.4, Testcontainers 2.0.5, springdoc 3.1.1.

## Last green commit

- B4 squash commit (see PROGRESS.md). `make verify-fast` PASS.

## wip/ branches

- `wip/spring-boot-4`: merged by squash into `feature/spring-boot-4` (B4). Not deleted (deleting
  a branch is Level 3).

## Next 3 steps

1. C3 done (D-8). Next: `make verify` (gates 7, 8, 11-13, Docker images).
2. C4: milestone audit (spec-auditor, test-auditor, security-reviewer, final-reviewer backend),
   `MILESTONE` = `9 complete`, merge origin/main, `make verify-all`, PR ready.

## Open escalations

- none
