# Test changes — spec 005

Owner-authored, locked. Read by the builder and by `test-auditor`.

## Allowed behaviour changes

AC-STYLE-1
(every test tagged with one of these IDs may change freely)

## Plumbing allowed everywhere

Imports, annotations, helper signatures, type renames inside an unchanged assertion.
No assertion method, expected value, @Tag or [AC-…] title may change in any other test.

## Tagged tests today

- AC-STYLE-1: `frontend/e2e/style.spec.ts:8`
