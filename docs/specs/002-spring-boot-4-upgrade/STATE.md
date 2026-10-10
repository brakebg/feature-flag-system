# State — spec 002 (Spring Boot 4 upgrade)

Keep under 150 lines.

## Current

- Milestone: M9, phase B on `wip/spring-boot-4` (this branch). Continue here, never restart the
  switch from `feature/spring-boot-4`.
- Working branch `feature/spring-boot-4` is green at d024a2d. Draft PR #13.
- Last processed PR comment: none (no owner comments yet)

## wip checkpoint log (newest last)

1. B1 step 1 done: pom on Boot 4.1.1, starters renamed, springdoc 3.1.1, Testcontainers 2 ids,
   version overrides removed. `mvnw test-compile` OK (main still on Jackson 2 classes through
   compat). Gates not run yet.

## Red gates

- none. `make verify-fast` PASS on wip (all of B1 done).

## wip/ branches

- `wip/spring-boot-4`: this branch. Not merged yet.

## Next 3 steps

1. B4: squash wip into `feature/spring-boot-4` (`git merge --squash`), verify-fast, push.
2. Phase C: gates 6-8 (full `make verify`), Docker images, gates 10-13.
3. C3: AC-UPG-1/2 tests, `.trivyignore`, VERSION 1.1.0, CHANGELOG, README.

## Open escalations

- none
