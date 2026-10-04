# Decisions

Level 1 decisions of the builder (spec 12.5) and answers to escalations.
Owner decisions live in `decisions/` (not here).

Format: ID · date · spec section · decision · why.

## ESC-001 · 2026-10-04 · answer: A

Owner (PR comment 5979791924): "ESC-001: A. Accepted all three choices (gate 14 due
milestones, ERR ID format, compose FF_DB_URL default)." Condition: the due milestones in
`scripts/lib/trace-registry.mjs` (`acDue`, `errIds`) may only move earlier, never later,
without a new escalation. D-001, D-003 and D-004 are accepted under this answer.

## D-001 · 2026-10-04 · 10.3, 9.4 · Compose default for `FF_DB_URL`

Status: accepted by the owner (ESC-001: A).

In `docker-compose.yml` the default for `FF_DB_URL` is
`jdbc:postgresql://postgres:5432/featureflags`, not the 9.4 default (`localhost`).
Inside the compose network the database host is the `postgres` service; `localhost`
would point at the backend container itself. The application default in
`application.yml` stays the 9.4 value.

## D-002 · 2026-10-04 · 11.3 gates 1, 10, 13 · Gate tools are downloaded

`gitleaks`, `trivy`, `promtool` and `k6` are not npm or Maven packages. `scripts/install-tools.sh`
downloads pinned release binaries into `build/tools/` and checks each against the
release checksum file. `make verify*` calls it when a gate needs the tool.

## D-003 · 2026-10-04 · 11.3 gate 14, 11.4 · Gate 14 before M8

Status: accepted by the owner (ESC-001: A).

Gate 14 is active from M1, but most acceptance criteria can only have tests later
(spec 12.2). `scripts/lib/trace-registry.mjs` gives every AC and ERR ID the milestone
whose "Done when" first needs it. Gate 14 always fails on an unknown ID or a failing
tagged test. A missing test fails from its due milestone; before that it shows as
`pending`. From M8 every ID is required, exactly as spec 11.4 says. Due milestones:
auth/token IDs M3, admin IDs and AC-AUD-1/3 M4, evaluation and cache IDs M5, UI auth
M6, UI groups/flags/audit M7, AC-OPS-1/2 and AC-CACHE-9 M8, AC-OPS-3 M2.
This is listed in the M1 PR comment so the owner can object.

## D-004 · 2026-10-04 · 11.4 · ERR ID format and the 9.1 rows

Status: accepted by the owner (ESC-001: A).

ERR IDs use the path relative to `/api/v1`, for example `ERR-POST-/admin/groups-409`.
The list is in `scripts/lib/trace-registry.mjs`: every status in the 6.1 Errors column,
plus `POST /auth/login` (400, 401, 403), `POST /auth/token` (400, 401, 403) and the
evaluation endpoints (401, 403, 404). Each 9.1 row is mapped to one ERR ID whose test
asserts that row's status and problem `type` (for example `409 limit-reached` →
`ERR-POST-/admin/groups/{groupId}/flags-409`). The 500 row uses `ERR-GET-/admin/groups-500`.

## D-005 · removed

D-005 (gate 6 UI step from M6) was dropped after the M1 audit: the step runs from M2.

## D-006 · 2026-10-04 · 2, 10.2 · Node versions

The UI image builds with `node:20-alpine` (spec 10.2); it only runs `vite build`.
Vitest 5, MSW 3 and jsdom need Node 22 or newer, so tests run on the Node of the
machine (CI: Node 22).

## D-007 · 2026-10-04 · 11.3 gate 10 · Patched library versions

The Spring Boot 3.5.16 BOM pins versions with HIGH/CRITICAL CVEs. `backend/pom.xml`
overrides `jackson-bom.version`, `tomcat.version` and `postgresql.version` with the
patched releases.

## D-008 · 2026-10-04 · 9.6 · Commit id in the backend image

The backend Docker build has no `.git`. `make up` and CI pass `GIT_COMMIT` as a build
argument; the Dockerfile writes `git.properties` from it, so `/actuator/info` shows
`git.commit.id`.

## D-009 · 2026-10-04 · 9.3 · Metric names used by the alert rules

Spec 9.3 names the custom metrics `ff_evaluations_total` and `ff_admin_writes_total`; spec 7.2
names `ff_cache_reconcile_drift_total` and `ff_cache_reconcile_last_success_seconds`. The
`FFNotReady` rule also needs the readiness state as a metric, so the backend exports a gauge
`ff_readiness_up` (1 = UP, 0 = DOWN). `FFNotReady` fires after 5 min of DOWN or when the
metric is absent (service gone). Error rate and latency use Spring Boot's
`http_server_requests_seconds` metrics.

## D-010 · 2026-10-04 · 10.2, 10.3 · Small build and compose details

- `deploy.replicas: 1` on the backend service writes down the single-instance rule (spec 7.2).
- The UI build stage installs npm 11 (`npm install -g npm@11.19.1`): the lockfile was made by
  npm 11, and `npm ci` from npm 10 (shipped with Node 20) rejects it. CI does the same.
- The nginx stage runs `apk upgrade` and `make up` builds with `--pull`, so the image scan of
  gate 10 sees current OS packages.

## D-011 · 2026-10-04 · 4.2, 6.1 · PATCH with `"enabled": null`

`PATCH /flags/{flagId}` with `"enabled": null` returns 400 `validation` (`field` `enabled`):
a boolean flag cannot be null, the same rule as `name: null` (6.1). An omitted `enabled`
stays unchanged. Raised as ESC-002 (touches the API).

## D-012 · 2026-10-04 · 9.1 · Unknown route or method

A request that passes security but matches no endpoint or method (for example `PUT` on an
admin path) returns 404 `not-found`. Spec 9.1 has no 405 row. Raised as ESC-002.

## D-013 · 2026-10-04 · 9.3 · Exact `/actuator/health` body

Spring Boot also lists the probe groups and the liveness / readiness contributors in the root
health body. `HealthBodyFilter` keeps only `status` and `components.db`, so the body is
exactly the 9.3 example. The disk-space, ping and SSL indicators are switched off.

## D-014 · 2026-10-04 · 4.1, 4.3, 6.1 · Migration folders and one extra index

- V1 lives in `db/migration/common`, V2 in `db/migration/dev`. Flyway scans sub-folders, so
  V2 cannot sit next to V1 if only `dev` may run it. `application.yml` uses `common`;
  `application-dev.yml` adds `dev` (spec 4.3 "via `spring.flyway.locations`").
- Extra index `idx_audit_event_target_key (target_key varchar_pattern_ops)` for the `targetKey`
  prefix filter of `GET /audit` (6.1, p95 < 300 ms in 9.2). The `occurred_at DESC` index also
  holds `id DESC`, the tie-break of the 6.1 sort.

## D-015 · 2026-10-04 · 4.1 · Security context before M3

`SecurityAuditor` (the `AuditorAware` of spec 4.1) reads Spring Security's context, so
`spring-security-core` was a dependency from M2. Replaced in M3 by the security and
oauth2-resource-server starters (spec 2).

## D-016 · 2026-10-04 · 9.4, 10.3 · `FF_REQUIRE_HTTPS` in docker-compose

Compose passes `FF_REQUIRE_HTTPS: ${FF_REQUIRE_HTTPS:-false}` exactly as spec 10.3 says
(9.4 dev default). A compose stack started with `SPRING_PROFILES_ACTIVE=prod` therefore keeps
`false` unless `FF_REQUIRE_HTTPS` is set. Outside compose the `prod` profile defaults to
`true` (`application-prod.yml`). Compose is a local dev tool (10.3). (M1 audit SA-11.)

## D-017 · 2026-10-04 · 5.4 · Test change: HealthIT unknown path

AC: none (gate support test `HealthIT.otherJsonResponsesPassTheHealthFilterUnchanged`).
Old expectation: `GET /no/such/path` returns 404 `not-found`. Spec 5.4 ("Everything else:
Denied", 401 without a valid token) makes it 401 `unauthorized` once security exists (M3).
Fix: the test now expects 401 `unauthorized`; it still checks that the health filter leaves
other problem bodies whole (type, title, detail, instance).

## D-018 · 2026-10-04 · 5.2, 5.5 · Token endpoint details

- Client authentication runs first; then `grant_type`, then `scope`. A body that is not
  `application/x-www-form-urlencoded` gets `invalid_request` before anything else.
- Client id and secret from the Basic header are checked as sent, then form-url-decoded
  (RFC 6749 2.3.1), so plain and encoded secrets both work.
- A 401 `invalid_client` carries `WWW-Authenticate: Basic realm="feature-flag-service"`.
- Login responses also carry `Cache-Control: no-store` (they hold a token).
- The error order and the env-variable client semantics are raised as ESC-004 (M3 audit SA-2, SA-4).

## D-019 · 2026-10-04 · 5.4, 9.2, 10.2 · M3 audit fixes

- Filters match the decoded path (`RequestPaths`), like Spring Security and MVC, so a
  percent-encoded path cannot skip the HTTPS check or the admin `Cache-Control`.
- Bearer tokens are read only on protected paths; a stale `Authorization: Bearer` header on
  login, token, health or info is ignored (5.4 "Public", 5.5 errors).
- Spring Security's default `Cache-Control` stays on other paths; the Admin API sets
  `no-store` first and the default writer then leaves the header alone (10.2).
- `BodySizeLimitFilter` answers 413 `payload-too-large` for a body above 65,536 bytes on every
  endpoint, login and token included (9.1, 9.2). Tomcat's form limit is also 64 KB.
- Startup fails for a blank admin password, a blank client secret, or a TTL with fractions of a
  second (5.1 TTL is "1 second or more"; tokens carry whole seconds).
- `ClientRegistration.toString()` leaves out the secret.
- `spring-boot-configuration-processor` removed (not in the spec stack).

## D-020 · 2026-10-04 · 11.3 gate 7 · PIT runs the unit tests

PIT mutates the `auth`, `group`, `flag` and `evaluation` packages (spec) and runs the unit
tests (`targetTests` = `com.example.featureflags.*Test`). The Testcontainers integration tests
(`*IT`) start a database and a Spring context and would run for hours per mutant. The services
and controllers have fast unit tests (mocked repositories, standalone MockMvc) for this. The
threshold stays 60 %. Not an exclusion of production code.

## D-021 · 2026-10-04 · 6.1, 6.2 · Admin API details

- `GET /audit` returns Spring Data's `PagedModel` (the VIA_DTO page shape of 6.2).
- `GET /groups?sort=` (empty value) is an unknown sort value: 400 `validation`, field `sort`.
  An omitted `sort` is `key`.
- POST /groups checks the duplicate key before the 1,000-group limit; POST flags checks the
  duplicate key before the 500-flag limit. Group creation takes a PostgreSQL advisory lock and
  flag creation locks the group row, so the limits hold under concurrent requests.
- `ff_admin_writes_total{action}` is incremented in `AuditService.record` (spec 9.3).

## D-022 · 2026-10-04 · 3, 6.1, 6.2 · M4 audit fixes

- The OpenAPI document carries the 6.2 optionality (`OpenApiConfig`): response fields required
  except `description` and `details`; request fields required as in 6.2; request
  `description` may be `null` (OpenAPI 3.1 type `["string","null"]`). `schema.d.ts` follows.
- `GET /audit`: a page far past the end (offset above 2^31) returns 200 with empty `content`
  (6.1); an empty or non-integer `page` / `size` is 400 `malformed-request` (6.1 "non-numeric").
- Flag update, toggle and delete lock the group row, then the flag row (same order as flag
  create and group delete). Concurrent toggles to one value all get 200 with one audit event;
  a write that races a group delete gets 404, not 409.
- The audit purge logs the removed count even when a batch fails.
- D-020 (PIT test set) and D-021's duplicate-before-limit order are raised as ESC-005.
