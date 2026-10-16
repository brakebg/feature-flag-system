# Spec 001 — Feature Flag Service

Oct 1, 2026 · @Yordan

The first spec: it built the product (M1–M8, release 1.0.0). Its product and architecture
sections now live as current documents in `docs/product/` and `docs/architecture/`; its
process sections in `docs/process/`. Map: `docs/README.md`. What stays here is what belongs
to spec 001 alone: its milestones, its definition of done, and the original criteria list.

## 12. Implementation plan for the AI agent

The agent builds in eight milestones, in order; each ends with green tests and a commit, and no milestone starts while the previous one is red. The agent runs straight through all eight milestones without waiting for review; only a Level 3 escalation (12.5) pauses work. The owner reviews once, at the end.


### 12.2 Milestones

1. **M1 Scaffolding** — repo layout (10.1), Spring Boot app with health endpoint, Vite React TS app, docker-compose with Postgres, CI workflow, lint/format configured, make verify with all 15 gates wired (inactive gates pass trivially), docs/acceptance-criteria.md registry with IDs, CLAUDE.md with the startup ritual, <spec>/STATE.md and <spec>/PROGRESS.md, the draft PR, and the escalation-notify workflow. Done when: `make up` shows a placeholder UI and `/actuator/health` is UP.
2. **M2 Schema and domain** — Flyway V1/V2, entities, repositories, DTO records, `GlobalExceptionHandler`. Done when: migration test on Testcontainers passes.
3. **M3 Auth** — config properties, `/auth/login`, client credentials token endpoint, resource-server JWT validation with scopes and audiences, security rules (5.4). Done when: auth integration tests pass.
4. **M4 Admin API** — group and flag services/controllers, optimistic locking, cascade delete, audit events, `/audit`. Done when: all section 6 integration tests pass and OpenAPI renders.
5. **M5 Evaluation API** — flag cache (Caffeine loading caches, warm-up, after-commit write-through, daily reconciliation job), ETag/304, metrics. Done when: section 7 tests pass, including "toggle then read".
6. **M6 UI foundation** — API client, auth flow, routing, app shell, shared components, toasts. Done when: login/logout/redirect unit tests pass.
7. **M7 UI features** — groups pane, flags table, modals, toggle, delete confirmations, audit page. Done when: frontend unit tests pass and coverage targets are met.
8. **M8 Hardening** — Playwright e2e for every 11.2 scenario, Dockerfiles finalised (non-root, healthchecks), README (setup, env vars, API usage with curl examples, client polling guidance). Done when: the full CI pipeline is green and every 11.2 box is ticked.
9. **M9 Spring Boot 4 upgrade** — spec 002, `docs/specs/002-spring-boot-4-upgrade/`, release 1.1.0. Done and merged.
10. **M10 Test-changes gate** — spec 004, `docs/specs/004-test-changes-gate/`, gate 16. Built by the owner (decision 0012). Done.
11. **M11 High availability** — spec 003, `docs/specs/003-high-availability/`, release: the next free minor version.
12. **M12 UI redesign** — spec 005, `docs/specs/005-ui-redesign/`, release: the next free minor version.

### 12.3 Definition of done

- [ ] `make verify-all` passes from a fresh clone (`git clean -xfd`), all 16 gates green.
- [ ] The traceability matrix shows every acceptance criterion and every error case covered by at least one passing test.
- [ ] Gate 15 reports no integrity violations; thresholds equal the values in this spec.
- [ ] README documents setup, configuration, the single-instance rule, Admin and Evaluation API with curl examples.
- [ ] `<spec>/DECISIONS.md` lists every assumption; `<spec>/BLOCKERS.md` is empty, or each entry explains what was tried.
- [ ] No escalation is open, no `wip/` branch remains, and `<spec>/STATE.md` shows all milestones complete.
- [ ] The final `<spec>/verify-report.md` is committed and the PR is marked ready for review.


### 11.2 Acceptance criteria

Keys and names in the criteria (`orders`, `new-checkout`) are examples of a valid new key. The `dev` profile already contains the group `orders`; tests use their own unique keys.

Authentication

- [ ] Given wrong credentials, when I sign in, then I see "Invalid username or password" and stay on `/login`.
- [ ] Given `admin` / `admin123`, when I sign in, then I land on `/groups`.
- [ ] Given no token, when I open `/groups` directly, then I am redirected to `/login`.
- [ ] Given an expired token, when any admin call returns 401, then I am sent to `/login?expired=1`.
- [ ] [Removed — decision 0002] ~~11 failed logins within 5 minutes from one IP return 429.~~ Rate limiting is done at the edge. ID kept so the other IDs do not shift; no test required.
- [ ] Sign out clears the token; Back button does not show protected data.

Groups

- [ ] I can create group `orders` (name "Orders"); it appears in the list with badge `0/0`.
- [ ] Creating a second group with key `orders` shows "Key already exists" on the Key field.
- [ ] Keys failing the regex (`Orders`, `1abc`, `a`, `has space`) are rejected in the UI and by the API.
- [ ] I can edit name and description; key is read-only in the edit modal.
- [ ] Deleting group `orders` with 2 flags requires typing `orders`; afterwards the group and both flags are gone from the UI, from the Admin API (`GET /groups/{groupId}` returns 404) and from the Evaluation API (404), and one GROUP\_DELETED audit event lists both flag keys.

Flags

- [ ] I can add flag `new-checkout` to `orders`, initial state off; full key `orders.new-checkout` is shown.
- [ ] The same flag key may exist in two different groups.
- [ ] Toggling updates the switch immediately, persists after page reload, and the badge updates.
- [ ] If the toggle request fails, the switch reverts and an error toast appears.
- [ ] Deleting one flag removes only that flag.
- [ ] Editing with a stale `version` returns 409 and the UI refetches.

Evaluation API

- [ ] `POST /api/v1/auth/token` with valid client id and secret returns a bearer JWT with scope `flags:read` and `expires_in` 900.
- [ ] Wrong secret → 401 `invalid_client`; unknown grant type → 400 `unsupported_grant_type`; requesting `admin` scope → 400 `invalid_scope`.
- [ ] Evaluation call without a token or with an expired token → 401; with an admin token → 403; with a client token → 200.
- [ ] A client token sent to any `/api/v1/admin/**` endpoint → 403.
- [ ] After toggling a flag in the UI, the next evaluation call returns the new value (no restart).
- [ ] Repeating a call with `If-None-Match` set to the returned ETag → 304 until the next admin write that changes data (a failed or no-op write keeps the ETag, 7.2).
- [ ] Unknown group or flag → 404.

Caching (verified with a spy on the repositories counting database queries)

- [ ] After warm-up, 1,000 evaluation calls for existing flags run zero database queries.
- [ ] Evicting one entry and reading it twice concurrently runs exactly one database query; the second read is a hit.
- [ ] Reading an unknown key twice within 30 s runs one database query.
- [ ] Toggle, create and delete of a flag, and delete of a group, are each visible on the next evaluation call with zero database queries for that call on the instance that made the change; on every other instance within `FF_CACHE_SYNC_INTERVAL` (p99 ≤ 2 s), with zero database queries for the evaluation call itself (spec 003).
- [ ] A write whose transaction rolls back leaves the cached value unchanged.
- [ ] The readiness probe reports DOWN until warm-up has finished, and stays UP when the database becomes unreachable after warm-up (spec 003).
- [ ] A flag changed directly in the database keeps its old cached value until reconciliation runs; after a reconciliation run (triggered in the test by calling the job), the next evaluation call returns the database value, a WARN is logged and the ETag changes; every other instance returns the database value within 2 s (spec 003).
- [ ] A reconciliation run with no differences logs no WARN and leaves the ETag unchanged.
- [ ] Under the 9.2 load test the cache hit rate metric is ≥ 99 %.

Audit

- [ ] A new group or flag stores created\_by = the signed-in user; every later update or toggle that changes a value sets updated\_by to the signed-in user while created\_by stays unchanged; extra createdBy / updatedBy fields in a request body are ignored. Every create, update, toggle and delete that changes data produces exactly one audit event with actor `admin`; a no-op update or toggle (6.1) produces none.
- [ ] Audit page lists events newest first and filters by target key prefix.

* [ ] Audit events older than the retention period are deleted by the purge job (verified with an injected `Clock`); newer events are kept.

Operations

- [ ] `make up` on a clean machine with Docker starts all three containers; UI reachable on port 3000 within 90 s.
- [ ] Backend image runs standalone with only env vars set (no UI needed); UI image runs standalone pointed at any `BACKEND_URL`.
- [ ] `/actuator/health` reports UP with DB status.

* [ ] With `FF_REQUIRE_HTTPS=true`, login and token requests carrying `X-Forwarded-Proto: http` are rejected with 403 `https-required`; the same requests with `https` succeed. Every UI response carries the security headers listed in 10.2 (HSTS only on requests with `X-Forwarded-Proto: https`).

Design

- [ ] The style checks of 11.6 item 1: on `/login` the `Sign in` button background is `#2350C8`, the body font is IBM Plex Sans, and the Username and Password inputs are 44 px high with a 6 px border radius; on a group page the top bar background is `#15181D` and its height 60 px, full keys use IBM Plex Mono, and a switch that is on and the `New flag` button have the background `#2350C8`.
