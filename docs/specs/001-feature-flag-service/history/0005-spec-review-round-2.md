# 0005 — Spec changes from testability review round 2 and tester questions

Status: **Accepted**
Decided by: owner (@Yordan), 2026-10-03
Source: `feature-flag-acceptance/docs/spec-review/round-2/questions.md` (Q-074 to Q-104) and
`feature-flag-acceptance/docs/tester-questions.md` (TQ-1 to TQ-32)

## 1. Why

A second review of the updated spec, and the tester writing the suite, found 31 more gaps
(1 Blocker, 18 Major, 12 Minor). 29 tester questions repeat them; 3 are new.

## 2. Answers

All round-2 questions: option A (recommended). Exceptions and notes:

| Q / TQ | Answer |
| --- | --- |
| Q-074 / TQ-30 | A, using the existing harness: `scripts/owner-review.sh` records `FF_STACK_STARTED_AT` right after `make up` and passes it with `FF_COMPOSE_PROJECT`; it starts the https and limits stacks before `make up`. No separate run script |
| Q-087 / TQ-12 | The hit-rate test runs in the 1-worker serial project (counter difference before/after the load), not in a fresh stack — the owner's TQ-12 confirmation wins over the Q-087 text |
| TQ-16 | `docker network`, `docker port`, `docker rm -f` allowed for AC-OPS-2 (test repo rule) |
| TQ-31 | A: `Cache-Control: no-store` on every Admin API status, including 401 and 403 |
| TQ-32 | A: tests for rules no criterion names are tagged with the closest criterion |

## 3. Spec changes (summary)

| Area | Questions | Spec |
| --- | --- | --- |
| Data and validation: `null` in `details` for a missing description; non-integer `version` → `malformed-request`; stale `version` → 409 even on a no-op | Q-075–Q-077 | 4.1, 6.1 |
| Auth: HS256 key = UTF-8 bytes of `jwt-secret`; `WWW-Authenticate` starts with the scheme; CORS values as sets; `/swagger-ui.html` in the path table | Q-078, Q-093–Q-095 | 5.1, 5.4 |
| Evaluation: response types `AllFlags`, `GroupFlags`, `OneFlag`; body `revision` = ETag number | Q-096 | 7.1 |
| UI: 8.7 roles win over design markup; search rules; dialog validation timing; edit dialog `version` and 409 behaviour; audit Details text rules; 429 text for N = 1; read-only Key input; `by <updatedBy>`; server field errors | Q-080, Q-081, Q-083–Q-086, Q-097–Q-100 | 8, 8.2, 8.4–8.6 |
| Config and operations: `FF_REQUIRE_HTTPS` in 9.4; security headers exactly once; HSTS only with `X-Forwarded-Proto: https`; standalone image check; version formats; AC-OPS-1 timing via `FF_STACK_STARTED_AT` | Q-074, Q-088–Q-091, Q-101, Q-102 | 9.4, 9.6, 10.2, 10.3 |
| Test rules: storage access for negative tokens; empty group list via `page.route`; AC-AUD-1 other-user part; "stays UP"; black-box projects; hit-rate placement | Q-079, Q-082, Q-087, Q-092, Q-103, Q-104 | 11.4–11.6 |
| Gate 11 wording: HSTS only with `X-Forwarded-Proto: https` | (consistency with Q-090) | 11.3 |

Acceptance criteria wording changed (IDs and positions unchanged): AC-OPS-4 — "(HSTS only on
requests with `X-Forwarded-Proto: https`)" added (Q-090).

## 4. Follow-up

- Optional: align the design markup of the status filter and tables with spec 8.7 (Q-080).
  Not required: spec 8 now says roles and names in 8.7 win over the design markup.
