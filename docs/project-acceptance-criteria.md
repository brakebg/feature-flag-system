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
- [ ] **AC-CACHE-4** v1 · Toggle, create and delete of a flag, and delete of a group, are each visible on the next evaluation call with zero database queries for that call. · spec 001 · due M5
- [ ] **AC-CACHE-5** v1 · A write whose transaction rolls back leaves the cached value unchanged. · spec 001 · due M5
- [ ] **AC-CACHE-6** v1 · The readiness probe reports DOWN until warm-up has finished. · spec 001 · due M5
- [ ] **AC-CACHE-7** v1 · A flag changed directly in the database keeps its old cached value until reconciliation runs; after a reconciliation run (triggered in the test by calling the job), the next evaluation call returns the database value, a WARN is logged and the ETag changes. · spec 001 · due M5
- [ ] **AC-CACHE-8** v1 · A reconciliation run with no differences logs no WARN and leaves the ETag unchanged. · spec 001 · due M5
- [ ] **AC-CACHE-9** v1 · Under the 9.2 load test the cache hit rate metric is ≥ 99 %. · spec 001 · due M8

## Audit

- [ ] **AC-AUD-1** v1 · A new group or flag stores created\_by = the signed-in user; every later update or toggle that changes a value sets updated\_by to the signed-in user while created\_by stays unchanged; extra createdBy / updatedBy fields in a request body are ignored. Every create, update, toggle and delete that changes data produces exactly one audit event with actor `admin`; a no-op update or toggle (6.1) produces none. · spec 001 · due M4
- [ ] **AC-AUD-2** v1 · Audit page lists events newest first and filters by target key prefix. · spec 001 · due M7
- [ ] **AC-AUD-3** v1 · Audit events older than the retention period are deleted by the purge job (verified with an injected `Clock`); newer events are kept. · spec 001 · due M4

## Operations

- [ ] **AC-OPS-1** v1 · `make up` on a clean machine with Docker starts all three containers; UI reachable on port 3000 within 90 s. · spec 001 · due M8
- [ ] **AC-OPS-2** v1 · Backend image runs standalone with only env vars set (no UI needed); UI image runs standalone pointed at any `BACKEND_URL`. · spec 001 · due M8
- [ ] **AC-OPS-3** v1 · `/actuator/health` reports UP with DB status. · spec 001 · due M2
- [ ] **AC-OPS-4** v1 · With `FF_REQUIRE_HTTPS=true`, login and token requests carrying `X-Forwarded-Proto: http` are rejected with 403 `https-required`; the same requests with `https` succeed. Every UI response carries the security headers listed in 10.2 (HSTS only on requests with `X-Forwarded-Proto: https`). · spec 001 · due M5

## Design

- [ ] **AC-STYLE-1** v2 · The style checks of 11.6 item 1, with the spec 005 values: on `/login` the `Sign in` button background is `#1C2433`, the body font is IBM Plex Sans, and the Username and Password inputs are 52 px high with a 16 px border radius; on a group page the top bar background is `#FFFFFF`, its border radius at least half its height and its height at least 64 px, full keys use IBM Plex Mono, and a switch that is on and the `New flag` button have the background `#3D59A8` (spec 005). · spec 001 · changed by spec 005 · due M12

## Platform upgrade

- [ ] **AC-UPG-1** v1 · The running backend reports Spring Boot 4.1.x, Spring Framework 7.0.9 or later, Spring Security 7.1.x and Jackson 3.x, read at runtime from the libraries (not from build files). · spec 002 · due M9
- [ ] **AC-UPG-2** v1 · No backend class imports Jackson 2 `com.fasterxml.jackson.core` or `com.fasterxml.jackson.databind` (ArchUnit, gate 3). · spec 002 · due M9

## Test changes gate

- [x] **AC-TC-1** v2 · A planted change in a test whose tags on `main` name no criterion with a raised version or a retirement since the branch left `main` (expected value, assertion method, removed assertion, `@Tag` or `[ID]` title, deleted file) makes gate 16 fail and print the file and line; so does, in `docs/project-acceptance-criteria.md`, a text change without a version + 1, a removed criterion line, a version jump of more than 1 or a new criterion not at v1. · spec 004 · changed by spec 006 · due M13
- [x] **AC-TC-2** v2 · An import, annotation, helper signature or type-only change in any test passes gate 16; a test tagged with a criterion whose version went up or that was retired may change its expected value and passes, whatever the branch name, also when the commit is checked from a tag. · spec 004 · changed by spec 006 · due M13

## UI redesign

Colours are computed CSS values (`rgb(...)`), as a browser test reads them. "The screens" means:
`/login?expired=1`, `/groups/<id>` with flags, `/groups` with no groups, a group with no flags,
`/audit` with events, the 404 page, and each dialog open (New group, Edit group, New flag,
Edit flag, Delete flag, Delete group). "Long data" means: a group name of 100 characters without
spaces, a group description of 500 characters, a flag key of 50 characters without hyphens (full
key 101 characters), and a flag description of 500 characters.

- [ ] **AC-UI-1** v1 · On the screens, the `body` background is `rgb(241, 242, 244)` and its text colour `rgb(28, 36, 51)`; the `body` font family starts with `"IBM Plex Sans"`; every `h1` uses a font family that starts with `"IBM Plex Sans Condensed"` with weight 600; flag keys and full keys use a font family that starts with `"IBM Plex Mono"`; after `document.fonts.ready`, `document.fonts.check` is true for `600 56px "IBM Plex Sans Condensed"`, `400 14px "IBM Plex Sans"` and `500 15px "IBM Plex Mono"`; no request goes to an origin other than the UI origin. · spec 005 · due M12
- [ ] **AC-UI-2** v1 · The buttons `New flag`, `Create flag`, `Create group`, `Save changes`, `Add flag` and `Create your first group`, and every switch with `aria-checked="true"`, have the background `rgb(61, 89, 168)`; a switch with `aria-checked="false"` has `rgb(205, 210, 219)`; `Sign in` and `Load more` have `rgb(28, 36, 51)`; an enabled `Delete` or `Delete group and <N> flag(s)` button has `rgb(181, 71, 58)`. · spec 005 · due M12
- [ ] **AC-UI-3** v1 · On the screens, every visible `button` element except the group buttons in the `Flag groups` list, and the inputs `Search groups`, `Search flags` and `Filter by target key`, have a computed border radius of at least half their rendered height, or `50%` on a square element. · spec 005 · due M12
- [ ] **AC-UI-4** v1 · Each group button in the `Flag groups` list contains one element with `aria-hidden="true"` whose trimmed `getPropertyValue('--share')` equals `floor((200 × enabled + total) / (2 × total))` followed by `%`, and `0%` when the group has no flags (for example 1 of 8 gives `13%`, 23 of 40 gives `58%`, 2 of 3 gives `67%`); after a toggle the value follows the new badge text. · spec 005 · due M12
- [ ] **AC-UI-5** v1 · Each Action label in the audit table keeps its spec 001 text and starts with one element with `aria-hidden="true"`; the label background and text colour are: `Group created` and `Flag created` `rgb(223, 229, 243)` / `rgb(45, 68, 134)`; `Group updated` and `Flag updated` `rgb(236, 238, 241)` / `rgb(61, 69, 86)`; `Flag toggled` `rgb(248, 230, 218)` / `rgb(138, 75, 38)`; `Group deleted` and `Flag deleted` `rgb(245, 224, 220)` / `rgb(154, 58, 47)`. · spec 005 · due M12
- [ ] **AC-UI-6** v1 · On the screens, no element with `aria-hidden="true"` is focusable, has a `tabindex` or contains a focusable element; and the ARIA snapshot (roles, names, `aria-checked`, `aria-pressed`, `aria-current`, `aria-invalid`) of each screen equals the snapshot recorded from the code before the redesign. · spec 005 · due M12
- [ ] **AC-UI-7** v1 · At viewport widths 768, 800, 1024 and 1440 px, on the screens: `document.documentElement.scrollWidth` is not larger than the viewport width; the flags table and the audit table containers have `scrollWidth` ≤ `clientWidth`; every `Edit <fullKey>` and `Delete <fullKey>` button lies inside the viewport; at 1024 px and below the `Flag groups` panel is above the flags table; below 860 px the sign-in art panel is not displayed and the column rules of spec 001 sections 8.4 and 8.6 hold. · spec 005 · due M12
- [ ] **AC-UI-8** v1 · With long data, at 1440 and 800 px: every element that shows a group name, group key, description, flag key or full key has `scrollWidth` ≤ `clientWidth` (it wraps, nothing is clipped), and in the group header and audit header cards no box of an `h1` or `p` intersects the box of an element with `aria-hidden="true"`; at 1280 × 600 with a group of 30 flags, after typing the group key, `Delete group and 30 flags` can be scrolled into view inside the dialog and clicked. · spec 005 · due M12
- [ ] **AC-UI-9** v1 · Moving focus with Tab through each screen, every focused element shows a solid 2 px outline with a 2 px offset, `rgb(61, 89, 168)` on light surfaces and `rgb(255, 255, 255)` inside the audit header card. · spec 005 · due M12
