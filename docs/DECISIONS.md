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

## D-023 · 2026-10-04 · 7.2, 9.3 · Evaluation cache details

- `EvaluationQueries` (native SQL, one query per cache load) is the only database access of
  the Evaluation API; it is called only by `FlagCacheService` loaders, `reloadAll` and the
  reconciliation snapshot (gate 3 rule).
- Negative entries expire after 30 s by a Caffeine expiry policy whose ticker is the injected
  `Clock` (testable without sleeping); positive entries never expire.
- A write for a group that is not cached (or cached as unknown) drops that group entry instead
  of loading it; the next read loads it once.
- The listener updates the cache first and then increments the revision, and the controller
  reads the revision before the data, so a body is never older than its ETag.
- `If-None-Match` accepts a list and weak tags (RFC 9110).
- Readiness maps "refusing traffic" to `DOWN` (Spring Boot says `OUT_OF_SERVICE`), so the
  probe returns 503 `{"status":"DOWN"}` exactly as 9.3 says. `ff_readiness_up` exports it.
- The request log line gets `client=<sub>` on evaluation requests (9.3).

## D-024 · 2026-10-04 · 11.3 gates 7, 14 · PIT runs are not test results

The JUnit tag listener (gate 14 input) is switched off inside PIT's mutant runs
(`-Dff.tags.off=true` in the PIT `jvmArgs`); a killed mutant is a failing test run by design and
must not show as a failing acceptance test. Normal test runs are recorded as before.

## D-025 · 2026-10-04 · 7.2, 9.3, 11.3 gate 11 · M5 audit fixes

- Reconciliation loads the database snapshot inside the cache writer lock, so a write that
  commits meanwhile is never undone by an older snapshot.
- A write while the all-flags entry is not cached drops any in-flight load of it.
- The revision moves on in a `finally` block, also when the cache update and its fallback fail.
- `ff_cache_reconcile_last_success_seconds` starts at the startup time (warm-up is a full load
  from the database), so `FFReconcileStale` does not fire after every restart.
- Drift is counted per cache entry (flag, group and all-flags entries), so one changed flag can
  give up to three WARN lines and +3 on `ff_cache_reconcile_drift_total`.
- Evaluation 404 details are fixed texts ("Unknown group", "Unknown flag").
- Readiness changes are logged ("Readiness state: ..."). Gate 11 passes the readiness step when it
  sees a 503, or when the backend's own transitions are REFUSING_TRAFFIC → warm-up finished →
  ACCEPTING_TRAFFIC and the probe ends UP; the DOWN window is short (spec 11.4). Raised in ESC-006.
- The OpenAPI document lists the 304/401/403/404 answers and the ETag / Cache-Control headers.

## D-026 · 2026-10-04 · 8.1, 8.3, 8.7 · M6 review fixes

- The API client sends no Admin API request without a token (spec 8.3); it fails at once with a
  401 `ApiError`. A late 401 only ends the session whose token it carried. A 2xx body that is not
  JSON is an `ApiError` (`malformed-response`).
- Test change (no AC; `apiClient.test.ts`): old expectation "a request without a stored token is
  sent"; spec 8.3 ("no Admin API request is sent without a token") makes it wrong. Fix: those
  tests store a token first; a new test proves that no request is sent without one.
- The focus trap listens on the document (Escape and Tab keep working when focus falls to the
  page body), uses the latest close handler, and dialogs can take focus (`tabIndex=-1`). A
  confirm dialog ignores Escape while its request runs.
- `RequireAuth` clears an expired token in an effect, not during render (StrictMode safe).
- (Withdrawn after the M6 test audit: an ESLint `ignoreRestSiblings` option was added and then
  removed again; `TextField` now picks its own props without unused names. Lint is as strict as
  before.)

## D-027 · 2026-10-05 · 8.2, 12.2 M6 · Test change: App placeholder test

AC: none (`frontend/src/App.test.tsx`, M1). Old expectation: the app renders an `<h1>Feature
Flags</h1>` placeholder (M1 "placeholder UI"). Spec 8.1/8.2 and 12.2 M6 replace the placeholder
with the router and the login page, so the old expectation no longer holds. Fix: the file was
deleted in 7d76ad0 without this entry (found by the M6 test audit, TA-1); it is now re-created with
tests of the real `<App/>` wiring (`/login` shows the sign-in page, `/groups` without a token goes
to `/login`). The login page tests cover the "Feature Flags" title.

## D-028 · 2026-10-05 · 11.5, 11.6 · Playwright runs in its Docker image

`scripts/e2e.sh` runs Playwright in `mcr.microsoft.com/playwright:v<same version>-noble`, so
browsers, fonts and the screenshot baselines (`*-chromium-desktop-linux.png`) are the same on
every machine and in CI. The browsers reach the stacks through `host.docker.internal`. The
screenshot stack is seeded once, in a fixed order, by `scripts/lib/seed-shots.sh` (design sample
data), so the audit-log screen is deterministic. Test keys are `e2e-<test id>-r<repeat>-...`
(deterministic, no random data).

## D-029 · 2026-10-05 · 8.4, 8.6 · Load and error states of the data screens

Spec is silent on loading and failed reads. Level 1, simplest option: while a list loads, no
empty state is shown ("No groups yet" only after `200 []`). A failed read shows one line with
`role="alert"`: `Could not load groups`, `Could not load the group`, `Could not load audit
events`; a network failure shows `Cannot reach server` (the 8.2 text). Only 404/400 on a group
id show the "Select a group or create one" placeholder. The audit page has no "No audit events"
text (removed, not in the spec). The copy-key button shows the error toast `Could not copy the
group key` when the browser refuses clipboard access (spec silent; a silent failure would hide it).

## D-030 · 2026-10-05 · 11.6 · Design fixes and the screenshot baselines

Design check (M7 audit) fixes, appearance only:
- Button font weights follow what the design renders. In the design HTML, a `font-weight` set
  before `font: inherit` is reset to 400. So primary, Cancel, Delete group (outline), Sign in and
  Sign out are 400; Edit group and Load more are 500; New group and the delete confirm button are 600.
- Delete group dialog icon: the trash icon of the design (stroke 1.8).
- Dialog backdrop `rgba(59, 66, 76, 0.75)`: blends to about #6B717A over #F4F5F7.
- Load more button padding `0 20px`.
- New flag dialog: the Initial state help text follows the switch (Level 1; the design shows only
  the Off text): `On: services read true as soon as the flag is created.`

Screenshot baselines (chromium-desktop, `frontend/e2e/screenshots.spec.ts-snapshots/`), first
added in 1f8b482 and re-recorded for the fixes above:
- `1-sign-in-chromium-desktop-linux.png` (screen 1 Sign in: button weight)
- `2-flags-workspace-chromium-desktop-linux.png` (screen 2 Flags workspace: button weights)
- `3-new-flag-dialog-chromium-desktop-linux.png` (screen 3 New flag dialog: weights, backdrop)
- `4-delete-group-confirmation-chromium-desktop-linux.png` (screen 4 Delete group confirmation:
  icon, weights, backdrop)
- `5-audit-log-chromium-desktop-linux.png` (screen 5 Audit log: Load more weight and padding)

`scripts/e2e.sh` takes no arguments, so no caller can change the Playwright settings; baselines
are recorded only with `FF_UPDATE_BASELINES=1` (test audit TA-12).

## D-031 · 2026-10-05 · 10.2, 11.5, 11.6 · M8 end-to-end suite and two bugs it found

Bugs found by the new e2e suite (fixed, production code):
- nginx passed `Host: $host` (no port) to the backend. A browser login through the UI on any
  port other than 80 sent `Origin: http://host:3000` with `Host: host`, so Spring CORS saw a
  cross-origin request and answered 403. nginx now passes `$http_host` (with the port).
- Zod 4 probes `new Function("")` once; browsers report this as a CSP violation (`script-src
  'self'`, no `unsafe-eval`), which fails gate 12. `z.config({ jitless: true })` in
  `src/schemas/forms.ts` stops the probe.

Suite layout (`frontend/playwright.config.ts`): `api` (no browser, once), `limits` (own stack with
`FF_REQUIRE_HTTPS=true`, 1 worker: group limit and AC-OPS-4 https-required), the four browser
projects of 11.6 (`e2e/ui`, style, screenshots), and `serial` (1 worker, after all others: ETag /
revision, AC-EVAL-6). Keys: `e2e-<test id>-<project><repeat>-<name>` (the test id is the same in
every project). AC-OPS-1 is measured on the e2e main stack (`docker compose build` first, then
`up` until the UI answers; host port 33000, not 3000, so a running dev stack does not collide).
AC-OPS-2 is `scripts/ops-standalone.sh` (10.2 standalone check). Both write script results for
gate 14 and fail gate 12. `scripts/e2e.sh` warms the backends (30 admin reads) before the tests
and keeps each stack's backend log in `build/reports/`.

Known limit: admin writes into one group serialize on its row lock and each waiting request holds
a database connection (Hikari default pool, 10). Many parallel writes into one group can delay
other requests until the pool frees up. The 9.2 flag-limit e2e test writes in batches of 4.

## D-032 · 2026-10-05 · 9.2, 11.3 gate 13 · Perf load starts when the backend is idle

One `make verify` run failed gate 13 with p95 130 ms (hit rate 100 %, no errors); standalone runs
gave p95 3 to 9 ms. A per-request profile showed the slow requests in the first 2 seconds of the
load and in short bursts: the JVM was still compiling startup code on its single vCPU when the
load began. `scripts/perf.sh` now waits after readiness until the backend container uses less than
5 % CPU in 3 samples in a row (at most 60 s) and only then reads the counters and starts k6. No
requests are sent or skipped in that time; the load, its length, the thresholds and the hit-rate
window are unchanged. After the change, gate 13 right after gate 12 gave p95 2.8 ms.

## D-033 · 2026-10-05 · 7.2 · Cache changes carry the audit id (final review BF-1, JR-1)

After-commit listeners of two writes to the same key can run in the opposite order of their
commits (the row lock is released at the database commit, before the listener runs). Each
`FlagsChangedEvent` now carries `seq`, the id of the audit event written in the same
transaction. Writes to one group take its row lock before the audit insert, so for one key a
later commit has a higher id. The cache keeps the newest applied id per group key and per full
flag key; a change older than one already applied (or older than the data loaded by warm-up)
only invalidates its entries, so the next read loads the committed value. Per key: the full
flag key, group create/delete, and any change in the group; changes to different flags of one
group may apply in any order. The revision still
moves on for every committed change. Reconciliation keeps the ids (it fixes values only).

## D-034 · 2026-10-05 · 9.1, 6.1, 7.2 · Final review backend fixes

- BF-3: a request rejected by Spring Security's firewall (`//`, `;` in the path) gets 400
  `malformed-request` as a problem detail (a `RequestRejectedHandler`).
- BF-4: group PATCH locks the group row like the other writes; a delete that commits first gives
  404, not 409.
- BF-6: a name or description with U+0000 is 400 `validation` ("must not contain the character
  U+0000"); PostgreSQL cannot store it, so it was a 500.
- BF-5 (part): the cache writer lock is a `ReentrantLock`, so warm-up and reconciliation JDBC do
  not pin virtual-thread carriers. Still open (owner): after-commit listeners wait for the lock
  while their transaction's connection is held; with many parallel admin writes during the
  nightly reconciliation the pool can run out until the connection timeout.
- SF-M1 (known limit, comment fixed): a write that commits while reconciliation loads its
  snapshot, and whose cache update still waits for the lock, shows as one drift WARN; the value
  set is the committed one either way.

## D-035 · 2026-10-05 · 8.5, 8.6 · Final review frontend fixes

- FF-1: after a toggle, the group is refetched only when it was the last toggle running in that
  group, so an earlier refetch cannot flip a toggle that is still in flight.
- FF-2: "Load more" skips events already shown (offset pages shift when new events arrive).
- FF-3, FF-5: a dialog cannot be closed (Close, Cancel, Escape) while its request runs, so a
  field error or toast is never lost.
- FF-4: delete refreshes the group list after an error too. A 404 on delete means someone else
  deleted it: the dialog closes with the error toast `Flag <fullKey> was already deleted` /
  `Group <key> was already deleted` (spec silent on this text, Level 1); a group delete then goes
  to `/groups` like a success.
- TA-2: the AC-FLAG-6 tests now check that a new GET of the group follows the 409.

## D-036 · 2026-10-05 · 9.1, 9.4, 9.6, 10.2 · Final review infra fixes

- IF-2 (replaces part of D-031): nginx sends the upstream's own `Host` and the browser host with
  its port in `X-Forwarded-Host` (the backend uses `forward-headers-strategy=framework`), so CORS
  still sees the same origin and a `BACKEND_URL` behind a host-routing ingress works; SNI on.
- IF-3: nginx limits `/api/` bodies to 64 KB and answers above that with the 9.1 problem detail
  `payload-too-large` (same type as the backend), with the security headers.
- IF-5: compose `BACKEND_URL` and the backend `HEALTHCHECK` follow `SERVER_PORT`.
- IF-6: `make up` / `make build-images` skip the `sha-` tags with a warning outside a git checkout.
- IF-4, SR-1: README states the ingress rules for `X-Forwarded-*`, that ports 8080/80 are reached
  only through the ingress, and the unprivileged-port sysctl for the UI image on Kubernetes.

## D-037 · 2026-10-05 · 11 · Coverage scope and small review items

- TA-4: spec 11 asks for line coverage >= 80 % "on `service` and `controller` packages". The code
  is packaged by feature (9.5), so there are no such packages; the JaCoCo check counts classes
  named `*Service` and `*Controller` (since M1, threshold 0.80 unchanged).
- FF-6: `RequireAuth` re-checks the token on every navigation; an expired token also clears the
  cached data, like a 401 and Sign out.
- DC-2, DC-3: "(optional)" muted in all dialogs; no empty list box for a group with 0 flags.
- IF-9: gate 8 compares the generated files with HEAD, not with the index.

## D-038 · 2026-10-05 · 5.5, 6.1, 7.2 · Final review round 2 fixes

- A chunked form body is parsed like Tomcat parses one with a Content-Length: a pair with a bad
  percent escape is skipped, an unknown charset gives no parameters (SF-N1).
- `GET /audit?targetKey=` with U+0000 is 400 `validation`, field `targetKey` (BF-FR-8).
- After each reconciliation the cache forgets the per-key change ids and treats changes up to the
  newest audit id seen after its snapshot as already loaded (bounded memory, BF-FR-9).

## D-039 · 2026-10-05 · 5.1, 5.2 · ESC-007 answer A applied

- A token TTL with fractions of a second is accepted; tokens use the whole seconds (floor), and
  startup fails only when the floored TTL is below 1 s. `exp - iat` equals the floored TTL.
- Test change (contradicted the owner's reading of 5.1): `AuthPropertiesTest`
  `ttlBelowOneSecondOrWithFractionsFails` expected startup to fail for `PT1.5S`; 5.1 "any ISO-8601
  duration of 1 second or more is accepted" with ESC-007 A makes it valid. The test is now
  `ttlBelowOneSecondFailsAndFractionsAreAccepted` (PT0.5S and PT0.999S fail, PT1.5S starts), and
  `TokenIssuerTest.aTtlWithFractionsUsesTheWholeSeconds` checks `exp - iat` = 1 for PT1.9S/PT1.5S.
- D-018, D-019 (blank secrets) and D-023 stay as built (owner accepted).
- Reconciliation reads the max audit id before its snapshot, so a failure there leaves the cache
  untouched (round 3 MINOR).

## D-040 · 2026-10-05 · 5.1, 5.5, 6.1, 9.1, 11.3 · ESC-002 to ESC-006 answers applied

The owner answered these on the PR on 2026-10-04 and 2026-10-05; the builder had missed the
answers (owner and builder post as the same GitHub login).

- ESC-002 A: D-011 and D-012 stay as built.
- ESC-003 A: `HmacJwtEncoder` stays; 5.2 (`aud` array) wins over the class named in 5.1.
- ESC-004 item 1 A: the token error order in D-018 stays.
- ESC-004 item 2 (C, modified): `FF_AUTH_CLIENTS_<n>_*` clients are now appended after the
  configured clients instead of replacing them. Env index 0 is the first env client. The
  duplicate-id and blank-secret checks run on the merged list.
- Test change (contradicted the owner's answer and 5.1 "added"): `AuthPropertiesTest`
  `clientsCanComeFromFfAuthClientsEnvironmentVariables` expected only the env client
  (`billing`). Now `envClientsAreAddedAfterTheConfiguredClients` expects `order-service`, then
  the env clients. New: `envClientsWithoutConfiguredClientsStartAtIndexZero`,
  `envClientWithAConfiguredClientIdFailsStartup`, `envClientWithABlankSecretFailsStartup`, and
  `EnvClientsIT` (env client 0 and `order-service` both get tokens). Both new tests failed on the
  old code first.
- `TokenSecretEncodingIT` now sets its `billing` client through `FF_AUTH_CLIENTS_0_*` instead of
  `featureflags.auth.clients[1]` (assertions unchanged). `EnvClientsIT` uses the same properties,
  so both share one Spring context. A separate context was one database pool too many for the
  test PostgreSQL ("too many clients already" in other ITs).
- ESC-005 A: D-020 and D-021 stay as built.
- ESC-006 A: D-024 and D-025 stay as built.

## D-041 · 2026-10-05 · 9.2, 11.3 gate 13 · Decision 0007: p95 is report-only

The owner merged decision 0007 into `main` (spec 9.2, 11.3, 12.5 changed); it is merged into
this branch on the owner's request.
- `perf/evaluate.js` keeps the `p(95)<50` threshold and `rate: 200` (the 9.2 target;
  `check-integrity.mjs` and `owner-review.sh` read them).
- k6 exits 99 when any threshold is crossed, so `scripts/perf.sh` decides from the k6 summary
  (`--summary-export`): the gate fails on a k6 exit other than 0 or 99, no requests, an error
  rate above 0, a failed check, or a cache hit rate below 0.99. A p95 at or above 50 ms only
  shows as "NOT met" in the report.
- The measurements (requests, error rate, failed checks, hit rate, p95 against 50 ms) go to
  `build/reports/perf.md`; `verify.mjs` adds them to `verify-report.md` when gate 13 ran.
- The pass/fail logic was checked with fake summaries: p95 120 ms passes; error rate 0.01, 2
  failed checks, or k6 exit 1 fail.

## D-042 · 2026-10-07 · 9.3, 9.6 · Owner bug: /actuator/info had no git.commit.id

Owner comment 6021814988. Cause: in the Docker build there is no `.git`, so
`git-commit-id-maven-plugin` wrote a `git.properties` with only a header comment into
`target/classes`. The resources step does not overwrite a newer file, so the file the Dockerfile
writes from `GIT_COMMIT` (D-008) never reached the jar. Fix: the Dockerfile runs Maven with
`-Dmaven.gitcommitid.skip=true`. Local builds still use the plugin (they have `.git`).
Test: e2e `api/ops.spec.ts` checks `git.commit.id` against the Docker stack. Checked by hand on
a fresh stack: `{"git":{"commit":{"id":"<7 hex>"}}, "build":{"version":"1.0.0",...}}`.
A plain `docker compose build` without `GIT_COMMIT` still has no commit id (no `.git` in the
build context); `make up`, `make build-images` and the gate scripts pass it.

## D-043 · 2026-10-07 · 10.2 · Owner bug: 413 had no Cache-Control: no-store

Owner comment 6021815278. Cause: `BodySizeLimitFilter` (order +5) answered 413 before
`AdminCacheControlFilter` (order +10) ran. Fix: `AdminCacheControlFilter` now runs at order +1,
right after the request id filter. Test first, seen failing:
`ChunkedBodyIT.adminBodyAbove65536BytesIs413WithNoStore` (POST /groups and PATCH /flags/{id},
with Content-Length and chunked; exactly one `Cache-Control: no-store`). The e2e 413 test also
checks the header now.
