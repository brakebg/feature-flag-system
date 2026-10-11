# Test changes — spec 003

Owner-authored, locked. Read by the builder and by `test-auditor`.

## Allowed behaviour changes

AC-CACHE-4, AC-CACHE-6, AC-CACHE-7
(every test tagged with one of these IDs may change freely)

## Plumbing allowed everywhere

Imports, annotations, helper signatures, type renames inside an unchanged assertion.
No assertion method, expected value, @Tag or [AC-…] title may change in any other test.

## Tagged tests today

- AC-CACHE-4: `backend/src/test/java/com/example/featureflags/evaluation/EvaluationIT.java:170`, `backend/src/test/java/com/example/featureflags/evaluation/EvaluationIT.java:198`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:216`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:241`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:366`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:390`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:412`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:440`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:480`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:499`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:513`
- AC-CACHE-6: `backend/src/test/java/com/example/featureflags/evaluation/EvaluationIT.java:408`, `frontend/e2e/api/ops.spec.ts:34`, `scripts/smoke.sh:64`, `scripts/smoke.sh:66`
- AC-CACHE-7: `backend/src/test/java/com/example/featureflags/evaluation/EvaluationIT.java:336`, `backend/src/test/java/com/example/featureflags/evaluation/FlagCacheServiceTest.java:285`
