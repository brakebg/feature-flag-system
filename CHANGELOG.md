# Changelog

All notable changes are listed here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versions: [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-10-10

### Changed

- Platform upgrade, no change in behaviour: Spring Boot 3.5 to 4.1 (Spring Framework 7.0,
  Spring Security 7.1, Tomcat 11, Hibernate ORM 7, Flyway 12), Jackson 2 to Jackson 3
  (`tools.jackson`; annotations stay `com.fasterxml.jackson.annotation`), springdoc-openapi 2.8
  to 3.1, JUnit 5 to 6, Testcontainers 1.21 to 2.0. API, OpenAPI document, database schema and
  gates are the same as in 1.0.0.

### Security

- Fixes CVE-2026-47884 and CVE-2026-47890 (spring-webmvc, CRITICAL) by moving to Spring
  Framework 7.0.9 or later. Both entries are removed from `.trivyignore`.

## [1.0.0] - 2026-10-05

First release.

### Added

- Flag groups and boolean flags with keys `group.flag`, stored in PostgreSQL (Flyway migrations,
  `dev` seed group `orders`).
- Admin login (`POST /api/v1/auth/login`, HS256 JWT, 8 hours) and the OAuth 2.0 client
  credentials token endpoint for client services (`POST /api/v1/auth/token`, 15 minutes).
- Admin API under `/api/v1/admin`: groups, flags, toggle, optimistic locking with `version`,
  cascade delete of a group, limits (1,000 groups, 500 flags per group, 64 KB bodies), RFC 9457
  problem details.
- Audit log of every change with actor and details, `GET /api/v1/admin/audit` with paging and
  target key filter, and a daily purge job (`FF_AUDIT_RETENTION`).
- Evaluation API under `/api/v1/evaluate`: all flags, one group, one flag; ETag / `304`; served
  from an in-memory Caffeine cache with warm-up, write-through after commit, negative entries and
  a daily reconciliation job.
- Admin UI (React): sign in, groups list with search and badges, flags table with search, status
  filter and optimistic toggle, create / edit / delete dialogs, typed delete-group confirmation,
  audit log page, session-expired handling.
- Operations: health, readiness (DOWN until the cache is warm), info with version and commit,
  Prometheus metrics, JSON logs with `X-Request-Id`, Prometheus alert rules, optional HTTPS-only
  login and token endpoints (`FF_REQUIRE_HTTPS`), security headers on every UI response.
- Docker images for backend and UI (non-root, health checks) and a docker-compose stack
  (`make up`).
- `make verify` with 15 quality gates, the same command in CI.

### Fixed

- UI login through nginx on a port other than 80 was rejected by CORS (nginx now passes the
  host with its port).
- The UI no longer triggers a Content-Security-Policy report (Zod runs without its eval-based JIT).
