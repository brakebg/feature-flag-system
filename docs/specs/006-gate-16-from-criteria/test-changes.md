# Test changes — spec 006

Owner-authored, locked. Read by the builder and by `test-auditor`.

## Allowed behaviour changes

AC-TC-1, AC-TC-2
(every test tagged with one of these IDs may change freely)

## Plumbing allowed everywhere

Imports, annotations, helper signatures, type renames inside an unchanged assertion.
No assertion method, expected value, @Tag or [AC-…] title may change in any other test.

## Tagged tests today

- AC-TC-1: `scripts/test/check-test-changes.test.mjs:141`, `scripts/test/check-test-changes.test.mjs:146`, `scripts/test/check-test-changes.test.mjs:151`, `scripts/test/check-test-changes.test.mjs:156`, `scripts/test/check-test-changes.test.mjs:161`, `scripts/test/check-test-changes.test.mjs:166`, `scripts/test/check-test-changes.test.mjs:171`, `scripts/test/check-test-changes.test.mjs:179`, `scripts/test/check-test-changes.test.mjs:188`, `scripts/test/check-test-changes.test.mjs:193`, `scripts/test/check-test-changes.test.mjs:198`, `scripts/test/check-test-changes.test.mjs:203`
- AC-TC-2: `scripts/test/check-test-changes.test.mjs:214`, `scripts/test/check-test-changes.test.mjs:226`, `scripts/test/check-test-changes.test.mjs:234`, `scripts/test/check-test-changes.test.mjs:239`, `scripts/test/check-test-changes.test.mjs:244`
