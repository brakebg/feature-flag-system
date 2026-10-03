# 0004 — Spec changes from the black-box testability review

Status: **Accepted**
Decided by: owner (@Yordan)
Source: `feature-flag-acceptance/docs/spec-review/questions.md` (73 questions; review of
`docs/SPEC.md` against `feature-flag-acceptance/docs/black-box-testing.md` section 10)

## 1. Why

A black-box test can only check what the spec defines exactly. The review found 73 gaps:
6 Blocker, 47 Major, 20 Minor. Each answer below closes one gap with a spec change.
The builder must build the same contract, so the answers go into `docs/SPEC.md`.

## 2. Answers applied

| Q | Gap | Answer | Spec |
| --- | --- | --- | --- |
| Q-001 | Browser storage of the token not defined | `sessionStorage` key `ff.accessToken`, raw JWT; removed on sign out and on 401 | 5.2 |
| Q-002 | Expired token cannot be produced reliably | TTL down to `PT1S`; JWT clock skew 0 s | 5.1 |
| Q-003 | `Page<AuditEvent>` shape not defined | Spring Data stable DTO format: `{ content, page: { size, number, totalElements, totalPages } }`; "Load more" while `number + 1 < totalPages` | 6.2, 8.6 |
| Q-004 | Dialog texts missing for New group, Edit group, Edit flag | Dialog text table; `role="dialog"`, named by title, `Close` button | 8.4 |
| Q-005 | Error toast text and role not defined | `role="status"` success, `role="alert"` error; toggle failure text `Could not update flag <fullKey>` | 8.5 |
| Q-006 | No way to start a stack with other configuration | Compose passes every 9.4 variable; host ports `FF_BACKEND_PORT`, `FF_UI_PORT` | 10.3 |

### 2.1 Majors and Minors (Q-007 to Q-073)

All answered by the owner on 2026-10-03: the recommended option, except Q-003 (above),
Q-009 (empty description = no description, to match Q-020) and Q-023 (option B). The
20 Minors (Q-054 to Q-073) were accepted as recommended in one decision. The exact answer
for each question is in `questions.md`.

| Area | Questions | Spec |
| --- | --- | --- |
| Auth and tokens: claim types, login validation, audience rule (both audiences accepted, 403 by path), denied paths, token endpoint errors (`invalid_request`), CORS | Q-010–Q-014, Q-016, Q-054–Q-056 | 5.1, 5.2, 5.4, 5.5, 9.1 |
| Admin API: validation rules, full Errors column, malformed ids, search and sort, PATCH semantics, immutable key ignored, `version` rules, toggle body required, no-op writes, audit actions and `details` shapes, audit query and order, `Location`, optional fields omitted, timestamps, key order | Q-008, Q-009, Q-017–Q-027, Q-057–Q-060 | 4.1, 4.2, 6.1, 6.2 |
| Evaluation and cache: ETag on all 3 endpoints, every data change increments `revision` (also group rename), hit-rate formula | Q-028, Q-047 | 7.1, 7.2, 9.2 |
| UI: expiry redirect, banner text, sign out, button names (design), real buttons, visible full key, narrow layout (spec wins), confirm dialogs, validation display, table semantics, audit labels, normative accessible names, 404 page, empty states, toast texts, footer | Q-030–Q-038, Q-040–Q-042, Q-061–Q-068, Q-071 | 8.1–8.7, 9.6 |
| Errors and operations: problem-detail members, new 9.1 rows (`https-required`, `limit-reached`, `payload-too-large`), limits, actuator shapes and public paths, prometheus needs admin token, security headers on every UI response, `X-Request-Id`, metric labels, `X-Forwarded-Proto`, image and container rules | Q-043, Q-044, Q-046, Q-048–Q-050, Q-052, Q-053, Q-069, Q-070, Q-072 | 9.1–9.3, 10.2, 10.3 |
| Test rules in the spec: readiness DOWN and the 500 row covered by backend tests only; signed negative tokens; `page.route` for UI states; 1-worker project for `revision`/ETag and group-limit tests; keys in criteria are examples | Q-007, Q-010, Q-029, Q-039, Q-045, Q-046, Q-073 | 11.2 intro, 11.4, 11.5, 11.6 |

Acceptance criteria wording changed (IDs and positions unchanged):

| AC | Change | Question |
| --- | --- | --- |
| AC-GRP-5 | "gone from UI, DB and Evaluation API" → checked through the UI, the Admin API (`GET /groups/{groupId}` 404) and the Evaluation API | Q-051 |
| AC-AUD-1 | "every later update or toggle" → "every later update or toggle that changes a value" | Q-024 |
| AC-EVAL-6 | "304 until the next admin write" → "... that changes data (a failed or no-op write keeps the ETag, 7.2)" | Q-024, Q-028 |

## 3. Follow-up

- The owner updates the designs where the spec now decides differently: narrow layout keeps Edit/Delete visible (Q-036); action controls are buttons, not links (Q-034); tables have table semantics (Q-040); "Sessions last 8 hours." removed (Q-062).
