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

- not run yet at this checkpoint

## wip/ branches

- `wip/spring-boot-4`: this branch. Not merged yet.

## Next 3 steps

1. B1 step 2: Jackson 3 in main code (`JacksonConfig`, `Json`), then run main app tests.
2. B1 step 3: tests on Jackson 3 (`TestJson`, domain tests), Testcontainers 2 packages, JUnit 6.
3. B1 step 4: security/web config until gate 5 green; then B4 squash to feature branch.

## Open escalations

- none
