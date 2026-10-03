# 0004 — Spec changes from the black-box testability review

Status: **Accepted** (in progress — updated as questions are answered)
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

## 3. Open

Q-007 to Q-073 (47 Major, 20 Minor): not answered yet.
