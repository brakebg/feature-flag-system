# Tech stack

From spec 001 (`docs/SPEC.md`) section 2. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

## 2. Tech stack

The agent MUST use these choices; it SHOULD use the latest patch release of each line at build time and pin exact versions in the build files.

| Layer | Choice | Notes |
| --- | --- | --- |
| Language (backend) | Java 21 (LTS) | Use records, sealed types and pattern matching where natural. Virtual threads enabled (`spring.threads.virtual.enabled=true`). |
| Framework | Spring Boot 4.1.x (spec 002; was 3.x in 1.0.0) | Starters: webmvc, validation, data-jpa, security, security-oauth2-resource-server, actuator, flyway. |
| Build (backend) | Maven with wrapper (`mvnw`) | Single module. |
| Database | PostgreSQL 16 | Schema managed by Flyway migrations. No `ddl-auto` in any profile except tests. |
| Auth tokens | JWT (HS256) via `Spring Security NimbusJwtEncoder / NimbusJwtDecoder` | Stateless; validated by OAuth2 Resource Server. Admin login + OAuth 2.0 client credentials; see section 5. |
| API docs | springdoc-openapi 3.x | Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`. |
| JSON | Jackson 3 (`tools.jackson.*`) | Annotations stay `com.fasterxml.jackson.annotation` (spec 002). |
| Cache | Caffeine (in-process) | For the evaluation API. |
| Backend tests | JUnit 6, AssertJ, Spring Boot Test, Testcontainers 2 (PostgreSQL), MockMvc | Versions as managed by Spring Boot 4.1 (spec 002). |
| Language (UI) | TypeScript 5 (strict mode) | `"strict": true`, no `any` without a comment. |
| UI framework | React 18 + Vite | SPA. |
| Routing / data | React Router 6, TanStack Query 5 | Query handles caching, refetch and mutations. |
| Forms | React Hook Form + Zod | Zod schemas mirror backend validation. |
| Styling | CSS Modules + a small set of CSS variables | No component library; keep UI simple. |
| UI tests | Vitest + React Testing Library; Playwright for end-to-end | MSW for API mocking in unit tests. |
| Lint / format | ESLint + Prettier (UI); Spotless with Google Java Format (backend) | Enforced in CI. |
| Packaging | Docker (multi-stage), docker-compose for local | Backend image on Eclipse Temurin 21 JRE; UI image on nginx (alpine). |
