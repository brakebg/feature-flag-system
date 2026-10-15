# Acceptance criteria registry

Verbatim copy of `docs/SPEC.md` section 11.2, with stable IDs (spec 11.4), plus one section per
change spec with its new IDs, heading `## <Title> (spec NNN, due milestone M)`, added by the
spec PR itself (`docs/specs/README.md`, "The registry"). This file is the complete registry of
the product; the black-box acceptance suite is built from it alone. Created once in M1.
Read-only for the builder afterwards.

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
- [ ] **AC-CACHE-4** · Toggle, create and delete of a flag, and delete of a group, are each visible on the next evaluation call with zero database queries for that call on the instance that made the change; on every other instance within `FF_CACHE_SYNC_INTERVAL` (p99 ≤ 2 s), with zero database queries for the evaluation call itself (spec 003).
- [ ] **AC-CACHE-5** · A write whose transaction rolls back leaves the cached value unchanged.
- [ ] **AC-CACHE-6** · The readiness probe reports DOWN until warm-up has finished, and stays UP when the database becomes unreachable after warm-up (spec 003).
- [ ] **AC-CACHE-7** · A flag changed directly in the database keeps its old cached value until reconciliation runs; after a reconciliation run (triggered in the test by calling the job), the next evaluation call returns the database value, a WARN is logged and the ETag changes; every other instance returns the database value within 2 s (spec 003).
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

## Design

- [ ] **AC-STYLE-1** · The style checks of 11.6 item 1, with the spec 005 values: on `/login` the `Sign in` button background is `#1C2433`, the body font is IBM Plex Sans, and the Username and Password inputs are 52 px high with a 16 px border radius; on a group page the top bar background is `#FFFFFF`, its border radius at least half its height and its height at least 64 px, full keys use IBM Plex Mono, and a switch that is on and the `New flag` button have the background `#3D59A8` (spec 005).

## High availability (spec 003, due milestone 11)

"N" below is any number of instances ≥ 2. Nothing in the code may assume a fixed count.

- [ ] **AC-HA-1** · N backend instances on one database serve the same flags from all three Evaluation endpoints, with the same `ETag`, for N = 3 in the test.
- [ ] **AC-HA-2** · A change made through one instance is visible on every other instance within 2 s (p99), with zero database queries for their evaluation calls.
- [ ] **AC-HA-3** · The revision in the database follows commit order with no gaps: two overlapping admin writes on different instances get consecutive revisions, and a rolled-back write leaves the revision unchanged.
- [ ] **AC-HA-4** · Monotonic ETag: a client that got `ETag "<n>"` from one instance and sends `If-None-Match: "<n>"` to another instance that is still behind gets either `304` or a `200` with a revision ≥ n. It never gets older data.
- [ ] **AC-HA-5** · With the database unreachable, every instance keeps answering every Evaluation call from its snapshot with the same `ETag` as before; readiness stays UP; admin writes fail as today.
- [ ] **AC-HA-6** · An instance restarted after changes were made on other instances serves the new state and the current revision as soon as it is ready. A new instance added to a running set does the same.
- [ ] **AC-HA-7** · An instance that lags by more than the retention of `flag_change` does a full reload and ends at the current revision with the current data.
- [ ] **AC-HA-8** · During a rolling restart of all instances, one at a time, a client polling the Evaluation API through the UI proxy gets no 5xx and never a lower revision than it already has.
- [ ] **AC-HA-9** · The revision of an instance never decreases, also across restart and full reload.
- [ ] **AC-HA-10** · Reconciliation runs on exactly one instance per schedule, whichever N; when it finds drift, every instance returns the corrected value within 2 s and the `ETag` changed.

## Test changes gate (spec 004, due milestone 10)

- [x] **AC-TC-1** · A planted change in a test whose tags are not in the active spec's `test-changes.md` (expected value, assertion method, removed assertion, `@Tag` or `[ID]` title, deleted file) makes gate 16 fail and print the file and line.
- [x] **AC-TC-2** · An import, annotation, helper signature or type-only change in a test that is not listed passes gate 16; a listed test with a changed expected value passes.

## UI redesign (spec 005, due milestone 12)

Colours are computed CSS values (`rgb(...)`), as a browser test reads them. "The screens" means:
`/login?expired=1`, `/groups/<id>` with flags, `/groups` with no groups, a group with no flags,
`/audit` with events, the 404 page, and each dialog open (New group, Edit group, New flag,
Edit flag, Delete flag, Delete group). "Long data" means: a group name of 100 characters without
spaces, a group description of 500 characters, a flag key of 50 characters without hyphens (full
key 101 characters), and a flag description of 500 characters.

- [ ] **AC-UI-1** · On the screens, the `body` background is `rgb(241, 242, 244)` and its text colour `rgb(28, 36, 51)`; the `body` font family starts with `"IBM Plex Sans"`; every `h1` uses a font family that starts with `"IBM Plex Sans Condensed"` with weight 600; flag keys and full keys use a font family that starts with `"IBM Plex Mono"`; after `document.fonts.ready`, `document.fonts.check` is true for `600 56px "IBM Plex Sans Condensed"`, `400 14px "IBM Plex Sans"` and `500 15px "IBM Plex Mono"`; no request goes to an origin other than the UI origin.
- [ ] **AC-UI-2** · The buttons `New flag`, `Create flag`, `Create group`, `Save changes`, `Add flag` and `Create your first group`, and every switch with `aria-checked="true"`, have the background `rgb(61, 89, 168)`; a switch with `aria-checked="false"` has `rgb(205, 210, 219)`; `Sign in` and `Load more` have `rgb(28, 36, 51)`; an enabled `Delete` or `Delete group and <N> flag(s)` button has `rgb(181, 71, 58)`.
- [ ] **AC-UI-3** · On the screens, every visible `button` element except the group buttons in the `Flag groups` list, and the inputs `Search groups`, `Search flags` and `Filter by target key`, have a computed border radius of at least half their rendered height, or `50%` on a square element.
- [ ] **AC-UI-4** · Each group button in the `Flag groups` list contains one element with `aria-hidden="true"` whose trimmed `getPropertyValue('--share')` equals `floor((200 × enabled + total) / (2 × total))` followed by `%`, and `0%` when the group has no flags (for example 1 of 8 gives `13%`, 23 of 40 gives `58%`, 2 of 3 gives `67%`); after a toggle the value follows the new badge text.
- [ ] **AC-UI-5** · Each Action label in the audit table keeps its spec 001 text and starts with one element with `aria-hidden="true"`; the label background and text colour are: `Group created` and `Flag created` `rgb(223, 229, 243)` / `rgb(45, 68, 134)`; `Group updated` and `Flag updated` `rgb(236, 238, 241)` / `rgb(61, 69, 86)`; `Flag toggled` `rgb(248, 230, 218)` / `rgb(138, 75, 38)`; `Group deleted` and `Flag deleted` `rgb(245, 224, 220)` / `rgb(154, 58, 47)`.
- [ ] **AC-UI-6** · On the screens, no element with `aria-hidden="true"` is focusable, has a `tabindex` or contains a focusable element; and the ARIA snapshot (roles, names, `aria-checked`, `aria-pressed`, `aria-current`, `aria-invalid`) of each screen equals the snapshot recorded from the code before the redesign.
- [ ] **AC-UI-7** · At viewport widths 768, 800, 1024 and 1440 px, on the screens: `document.documentElement.scrollWidth` is not larger than the viewport width; the flags table and the audit table containers have `scrollWidth` ≤ `clientWidth`; every `Edit <fullKey>` and `Delete <fullKey>` button lies inside the viewport; at 1024 px and below the `Flag groups` panel is above the flags table; below 860 px the sign-in art panel is not displayed and the column rules of spec 001 sections 8.4 and 8.6 hold.
- [ ] **AC-UI-8** · With long data, at 1440 and 800 px: every element that shows a group name, group key, description, flag key or full key has `scrollWidth` ≤ `clientWidth` (it wraps, nothing is clipped), and in the group header and audit header cards no box of an `h1` or `p` intersects the box of an element with `aria-hidden="true"`; at 1280 × 600 with a group of 30 flags, after typing the group key, `Delete group and 30 flags` can be scrolled into view inside the dialog and clicked.
- [ ] **AC-UI-9** · Moving focus with Tab through each screen, every focused element shows a solid 2 px outline with a 2 px offset, `rgb(61, 89, 168)` on light surfaces and `rgb(255, 255, 255)` inside the audit header card.
