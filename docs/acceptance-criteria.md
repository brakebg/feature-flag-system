# Acceptance criteria registry

Verbatim copy of `docs/SPEC.md` section 11.2, with stable IDs (spec 11.4).
Created once in M1. Read-only for the builder afterwards.

Removed IDs (spec 11.4): AC-AUTH-5 (decision 0002). They need no test.

> Keys and names in the criteria (`orders`, `new-checkout`) are examples of a valid new key. The `dev` profile already contains the group `orders`; tests use their own unique keys.

## Authentication

- [ ] **AC-AUTH-1** · Given wrong credentials, when I sign in, then I see "Invalid username or password" and stay on `/login`.
- [ ] **AC-AUTH-2** · Given `admin` / `admin123`, when I sign in, then I land on `/groups`.
- [ ] **AC-AUTH-3** · Given no token, when I open `/groups` directly, then I am redirected to `/login`.
- [ ] **AC-AUTH-4** · Given an expired token, when any admin call returns 401, then I am sent to `/login?expired=1`.
- [ ] **AC-AUTH-5** · [Removed — decision 0002] ~~11 failed logins within 5 minutes from one IP return 429.~~ Rate limiting is done at the edge. ID kept so the other IDs do not shift; no test required.
- [ ] **AC-AUTH-6** · Sign out clears the token; Back button does not show protected data.

## Groups

- [ ] **AC-GRP-1** · I can create group `orders` (name "Orders"); it appears in the list with badge `0/0`.
- [ ] **AC-GRP-2** · Creating a second group with key `orders` shows "Key already exists" on the Key field.
- [ ] **AC-GRP-3** · Keys failing the regex (`Orders`, `1abc`, `a`, `has space`) are rejected in the UI and by the API.
- [ ] **AC-GRP-4** · I can edit name and description; key is read-only in the edit modal.
- [ ] **AC-GRP-5** · Deleting group `orders` with 2 flags requires typing `orders`; afterwards the group and both flags are gone from the UI, from the Admin API (`GET /groups/{groupId}` returns 404) and from the Evaluation API (404), and one GROUP\_DELETED audit event lists both flag keys.

## Flags

- [ ] **AC-FLAG-1** · I can add flag `new-checkout` to `orders`, initial state off; full key `orders.new-checkout` is shown.
- [ ] **AC-FLAG-2** · The same flag key may exist in two different groups.
- [ ] **AC-FLAG-3** · Toggling updates the switch immediately, persists after page reload, and the badge updates.
- [ ] **AC-FLAG-4** · If the toggle request fails, the switch reverts and an error toast appears.
- [ ] **AC-FLAG-5** · Deleting one flag removes only that flag.
- [ ] **AC-FLAG-6** · Editing with a stale `version` returns 409 and the UI refetches.

## Evaluation API

- [ ] **AC-EVAL-1** · `POST /api/v1/auth/token` with valid client id and secret returns a bearer JWT with scope `flags:read` and `expires_in` 900.
- [ ] **AC-EVAL-2** · Wrong secret → 401 `invalid_client`; unknown grant type → 400 `unsupported_grant_type`; requesting `admin` scope → 400 `invalid_scope`.
- [ ] **AC-EVAL-3** · Evaluation call without a token or with an expired token → 401; with an admin token → 403; with a client token → 200.
- [ ] **AC-EVAL-4** · A client token sent to any `/api/v1/admin/**` endpoint → 403.
- [ ] **AC-EVAL-5** · After toggling a flag in the UI, the next evaluation call returns the new value (no restart).
- [ ] **AC-EVAL-6** · Repeating a call with `If-None-Match` set to the returned ETag → 304 until the next admin write that changes data (a failed or no-op write keeps the ETag, 7.2).
- [ ] **AC-EVAL-7** · Unknown group or flag → 404.

## Caching (verified with a spy on the repositories counting database queries)

- [ ] **AC-CACHE-1** · After warm-up, 1,000 evaluation calls for existing flags run zero database queries.
- [ ] **AC-CACHE-2** · Evicting one entry and reading it twice concurrently runs exactly one database query; the second read is a hit.
- [ ] **AC-CACHE-3** · Reading an unknown key twice within 30 s runs one database query.
- [ ] **AC-CACHE-4** · Toggle, create and delete of a flag, and delete of a group, are each visible on the next evaluation call with zero database queries for that call.
- [ ] **AC-CACHE-5** · A write whose transaction rolls back leaves the cached value unchanged.
- [ ] **AC-CACHE-6** · The readiness probe reports DOWN until warm-up has finished.
- [ ] **AC-CACHE-7** · A flag changed directly in the database keeps its old cached value until reconciliation runs; after a reconciliation run (triggered in the test by calling the job), the next evaluation call returns the database value, a WARN is logged and the ETag changes.
- [ ] **AC-CACHE-8** · A reconciliation run with no differences logs no WARN and leaves the ETag unchanged.
- [ ] **AC-CACHE-9** · Under the 9.2 load test the cache hit rate metric is ≥ 99 %.

## Audit

- [ ] **AC-AUD-1** · A new group or flag stores created\_by = the signed-in user; every later update or toggle that changes a value sets updated\_by to the signed-in user while created\_by stays unchanged; extra createdBy / updatedBy fields in a request body are ignored. Every create, update, toggle and delete that changes data produces exactly one audit event with actor `admin`; a no-op update or toggle (6.1) produces none.
- [ ] **AC-AUD-2** · Audit page lists events newest first and filters by target key prefix.
- [ ] **AC-AUD-3** · Audit events older than the retention period are deleted by the purge job (verified with an injected `Clock`); newer events are kept.

## Operations

- [ ] **AC-OPS-1** · `make up` on a clean machine with Docker starts all three containers; UI reachable on port 3000 within 90 s.
- [ ] **AC-OPS-2** · Backend image runs standalone with only env vars set (no UI needed); UI image runs standalone pointed at any `BACKEND_URL`.
- [ ] **AC-OPS-3** · `/actuator/health` reports UP with DB status.
- [ ] **AC-OPS-4** · With `FF_REQUIRE_HTTPS=true`, login and token requests carrying `X-Forwarded-Proto: http` are rejected with 403 `https-required`; the same requests with `https` succeed. Every UI response carries the security headers listed in 10.2 (HSTS only on requests with `X-Forwarded-Proto: https`).
