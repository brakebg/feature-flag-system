# Project acceptance criteria

The one current list of the product's acceptance criteria (master). The black-box acceptance
suite is built from it alone. Owner only: a spec adds and changes lines in its own commits,
before its `spec-NNN-ready` tag; the builder never edits this file.

Line format (gates 14, 15, 16 and `spec-tool` parse it):

```
- [ ] **<ID>** v<N> · <one testable sentence> · spec <NNN>[ · changed by spec <MMM>] · due M<n>
- [ ] **<ID>** v<N> · <text> · spec <NNN> · retired by <decision or spec>
```

- `v<N>`: version of the criterion. `v1` when created; +1 each time its text changes. Acceptance
  tests name the version they check: `[AC-STYLE-1 v2]`.
- `spec <NNN>`: the spec that created it. `changed by spec <MMM>`: the spec that last changed it.
- `due M<n>`: from the end of milestone n every test of it must pass (gate 14).
- A `v1` line of spec 001 equals its text in spec 001 section 11.2 (gate 15).

Groups are product areas. Spec 001 IDs were given by group and position (spec 001 section 11.4).

> Keys and names in the criteria (`orders`, `new-checkout`) are examples of a valid new key. The `dev` profile already contains the group `orders`; tests use their own unique keys.

## Authentication

- [ ] **AC-AUTH-1** v1 · Given wrong credentials, when I sign in, then I see "Invalid username or password" and stay on `/login`. · spec 001 · due M6
- [ ] **AC-AUTH-2** v1 · Given `admin` / `admin123`, when I sign in, then I land on `/groups`. · spec 001 · due M6
- [ ] **AC-AUTH-3** v1 · Given no token, when I open `/groups` directly, then I am redirected to `/login`. · spec 001 · due M6
- [ ] **AC-AUTH-4** v1 · Given an expired token, when any admin call returns 401, then I am sent to `/login?expired=1`. · spec 001 · due M6
- [ ] **AC-AUTH-5** v1 · [Removed — decision 0002] ~~11 failed logins within 5 minutes from one IP return 429.~~ Rate limiting is done at the edge. ID kept so the other IDs do not shift; no test required. · spec 001 · retired by decision 0002
- [ ] **AC-AUTH-6** v1 · Sign out clears the token; Back button does not show protected data. · spec 001 · due M6

## Groups

- [ ] **AC-GRP-1** v1 · I can create group `orders` (name "Orders"); it appears in the list with badge `0/0`. · spec 001 · due M7
- [ ] **AC-GRP-2** v1 · Creating a second group with key `orders` shows "Key already exists" on the Key field. · spec 001 · due M7
- [ ] **AC-GRP-3** v1 · Keys failing the regex (`Orders`, `1abc`, `a`, `has space`) are rejected in the UI and by the API. · spec 001 · due M7
- [ ] **AC-GRP-4** v1 · I can edit name and description; key is read-only in the edit modal. · spec 001 · due M7
- [ ] **AC-GRP-5** v1 · Deleting group `orders` with 2 flags requires typing `orders`; afterwards the group and both flags are gone from the UI, from the Admin API (`GET /groups/{groupId}` returns 404) and from the Evaluation API (404), and one GROUP\_DELETED audit event lists both flag keys. · spec 001 · due M7

## Flags

- [ ] **AC-FLAG-1** v1 · I can add flag `new-checkout` to `orders`, initial state off; full key `orders.new-checkout` is shown. · spec 001 · due M7
- [ ] **AC-FLAG-2** v1 · The same flag key may exist in two different groups. · spec 001 · due M7
- [ ] **AC-FLAG-3** v1 · Toggling updates the switch immediately, persists after page reload, and the badge updates. · spec 001 · due M7
- [ ] **AC-FLAG-4** v1 · If the toggle request fails, the switch reverts and an error toast appears. · spec 001 · due M7
- [ ] **AC-FLAG-5** v1 · Deleting one flag removes only that flag. · spec 001 · due M7
- [ ] **AC-FLAG-6** v1 · Editing with a stale `version` returns 409 and the UI refetches. · spec 001 · due M7

## Evaluation API

- [ ] **AC-EVAL-1** v1 · `POST /api/v1/auth/token` with valid client id and secret returns a bearer JWT with scope `flags:read` and `expires_in` 900. · spec 001 · due M3
- [ ] **AC-EVAL-2** v1 · Wrong secret → 401 `invalid_client`; unknown grant type → 400 `unsupported_grant_type`; requesting `admin` scope → 400 `invalid_scope`. · spec 001 · due M3
- [ ] **AC-EVAL-3** v1 · Evaluation call without a token or with an expired token → 401; with an admin token → 403; with a client token → 200. · spec 001 · due M5
- [ ] **AC-EVAL-4** v1 · A client token sent to any `/api/v1/admin/**` endpoint → 403. · spec 001 · due M3
- [ ] **AC-EVAL-5** v1 · After toggling a flag in the UI, the next evaluation call returns the new value (no restart). · spec 001 · due M5
- [ ] **AC-EVAL-6** v1 · Repeating a call with `If-None-Match` set to the returned ETag → 304 until the next admin write that changes data (a failed or no-op write keeps the ETag, 7.2). · spec 001 · due M5
- [ ] **AC-EVAL-7** v1 · Unknown group or flag → 404. · spec 001 · due M5

## Caching (verified with a spy on the repositories counting database queries)

- [ ] **AC-CACHE-1** v1 · After warm-up, 1,000 evaluation calls for existing flags run zero database queries. · spec 001 · due M5
- [ ] **AC-CACHE-2** v1 · Evicting one entry and reading it twice concurrently runs exactly one database query; the second read is a hit. · spec 001 · due M5
- [ ] **AC-CACHE-3** v1 · Reading an unknown key twice within 30 s runs one database query. · spec 001 · due M5
- [ ] **AC-CACHE-4** v2 · Toggle, create and delete of a flag, and delete of a group, are each visible on the next evaluation call with zero database queries for that call on the instance that made the change; on every other instance within `FF_CACHE_SYNC_INTERVAL` (p99 ≤ 2 s), with zero database queries for the evaluation call itself. · spec 001 · changed by spec 003 · due M11
- [ ] **AC-CACHE-5** v1 · A write whose transaction rolls back leaves the cached value unchanged. · spec 001 · due M5
- [ ] **AC-CACHE-6** v2 · The readiness probe reports DOWN until warm-up has finished, and stays UP when the database becomes unreachable after warm-up. · spec 001 · changed by spec 003 · due M11
- [ ] **AC-CACHE-7** v2 · A flag changed directly in the database keeps its old cached value until reconciliation runs; after a reconciliation run (triggered in the test by calling the job), the next evaluation call returns the database value, a WARN is logged and the ETag changes; every other instance returns the database value within 2 s. · spec 001 · changed by spec 003 · due M11
- [ ] **AC-CACHE-8** v1 · A reconciliation run with no differences logs no WARN and leaves the ETag unchanged. · spec 001 · due M5
- [ ] **AC-CACHE-9** v1 · Under the 9.2 load test the cache hit rate metric is ≥ 99 %. · spec 001 · due M8

## Audit

- [ ] **AC-AUD-1** v1 · A new group or flag stores created\_by = the signed-in user; every later update or toggle that changes a value sets updated\_by to the signed-in user while created\_by stays unchanged; extra createdBy / updatedBy fields in a request body are ignored. Every create, update, toggle and delete that changes data produces exactly one audit event with actor `admin`; a no-op update or toggle (6.1) produces none. · spec 001 · due M4
- [ ] **AC-AUD-2** v1 · Audit page lists events newest first and filters by target key prefix. · spec 001 · due M7
- [ ] **AC-AUD-3** v1 · Audit events older than the retention period are deleted by the purge job (verified with an injected `Clock`); newer events are kept. · spec 001 · due M4

## Operations

- [ ] **AC-OPS-1** v2 · `make up` on a clean machine with Docker starts every service of the compose file (`postgres`, each backend service, `frontend`); `postgres` and each backend report healthy; UI reachable on port 3000 within 90 s. · spec 001 · changed by spec 003 · due M11
- [ ] **AC-OPS-2** v1 · Backend image runs standalone with only env vars set (no UI needed); UI image runs standalone pointed at any `BACKEND_URL`. · spec 001 · due M8
- [ ] **AC-OPS-3** v1 · `/actuator/health` reports UP with DB status. · spec 001 · due M2
- [ ] **AC-OPS-4** v1 · With `FF_REQUIRE_HTTPS=true`, login and token requests carrying `X-Forwarded-Proto: http` are rejected with 403 `https-required`; the same requests with `https` succeed. Every UI response carries the security headers listed in 10.2 (HSTS only on requests with `X-Forwarded-Proto: https`). · spec 001 · due M5

## Design

- [ ] **AC-STYLE-1** v1 · The style checks of 11.6 item 1: on `/login` the `Sign in` button background is `#2350C8`, the body font is IBM Plex Sans, and the Username and Password inputs are 44 px high with a 6 px border radius; on a group page the top bar background is `#15181D` and its height 60 px, full keys use IBM Plex Mono, and a switch that is on and the `New flag` button have the background `#2350C8`. · spec 001 · due M8

## Platform upgrade

- [ ] **AC-UPG-1** v1 · The running backend reports Spring Boot 4.1.x, Spring Framework 7.0.9 or later, Spring Security 7.1.x and Jackson 3.x, read at runtime from the libraries (not from build files). · spec 002 · due M9
- [ ] **AC-UPG-2** v1 · No backend class imports Jackson 2 `com.fasterxml.jackson.core` or `com.fasterxml.jackson.databind` (ArchUnit, gate 3). · spec 002 · due M9

## High availability

"N" below is any number of instances ≥ 2. Nothing in the code may assume a fixed count. The
integration tests run with N = 3; the compose stack runs with N = 2.

- [ ] **AC-HA-1** v1 · N backend instances on one database serve the same flags from all three Evaluation endpoints, with the same `ETag`, for N = 3 in the test. · spec 003 · due M11
- [ ] **AC-HA-2** v1 · A change made through one instance is visible on every other instance within 2 s (p99), with zero database queries for their evaluation calls. · spec 003 · due M11
- [ ] **AC-HA-3** v1 · The revision in the database follows commit order with no gaps: two overlapping admin writes on different instances get consecutive revisions, and a rolled-back write leaves the revision unchanged. · spec 003 · due M11
- [ ] **AC-HA-4** v1 · Monotonic ETag: a client that got `ETag "<n>"` from one instance and sends `If-None-Match: "<n>"` to another instance that is still behind gets either `304` or a `200` with a revision ≥ n. It never gets older data. · spec 003 · due M11
- [ ] **AC-HA-5** v1 · With the database unreachable, every instance keeps answering every Evaluation call from its snapshot with the same `ETag` as before; readiness stays UP; admin writes fail as today. · spec 003 · due M11
- [ ] **AC-HA-6** v1 · An instance restarted after changes were made on other instances serves the new state and the current revision as soon as it is ready. A new instance added to a running set does the same. · spec 003 · due M11
- [ ] **AC-HA-7** v1 · An instance that lags by more than the retention of `flag_change` does a full reload and ends at the current revision with the current data. · spec 003 · due M11
- [ ] **AC-HA-8** v1 · During a rolling restart of all instances, one at a time, a client polling the Evaluation API through the UI proxy gets no 5xx and never a lower revision than it already has. · spec 003 · due M11
- [ ] **AC-HA-9** v1 · The revision of an instance never decreases, also across restart and full reload. · spec 003 · due M11
- [ ] **AC-HA-10** v1 · Reconciliation runs on exactly one instance per schedule, whichever N; when it finds drift, every instance returns the corrected value within 2 s and the `ETag` changed. · spec 003 · due M11

## Test changes gate

- [x] **AC-TC-1** v1 · A planted change in a test whose tags are not in the active spec's `test-changes.md` (expected value, assertion method, removed assertion, `@Tag` or `[ID]` title, deleted file) makes gate 16 fail and print the file and line. · spec 004 · due M10
- [x] **AC-TC-2** v1 · An import, annotation, helper signature or type-only change in a test that is not listed passes gate 16; a listed test with a changed expected value passes. · spec 004 · due M10
