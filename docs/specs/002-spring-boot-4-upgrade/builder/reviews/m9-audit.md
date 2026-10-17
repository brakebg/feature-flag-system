# M9 milestone audit

Range `80bbbb6...HEAD`. Agents: spec-auditor, test-auditor, security-reviewer, final-reviewer
(scope backend). Vendor agents: not available (cloud session). That is the expected state.

| Severity | Found | Fixed | Rejected | Open |
| --- | --- | --- | --- | --- |
| BLOCKER | 0 | 0 | 0 | 0 |
| CRITICAL | 0 | 0 | 0 | 0 |
| MAJOR | 4 | 4 | 0 | 0 |
| MINOR | 8 | 1 | 0 | 7 optional |

## MAJOR (all fixed, tests added)

- SA-1 rule covered production classes only: `Jackson3OnlyTest` now also checks all test classes except the failing fixture.
- TA-1 no test for the cleared OpenAPI `minimum`: assertion added in `OpenApiExportIT`.
- TA-2 `Json.keepHealthStatusAndDb` null branch untested: `JsonHealthFilterTest` added.
- FR-1 field order untested on Jackson 3: exact-order tests in `ResponseJsonTest`.

## MINOR

- SA-2 D-7 list made explicit (fixed). SA-3, SR-1, FR-2: `TokenController.parameter` catches
  `IllegalStateException` (portable, no Tomcat class in main code); `ChunkedBodyIT` pins the
  case; no bypass found. Open, optional. TA-3, TA-4: no action. FR-3: audit `details` JSONB is
  written and read back by `AuditApiIT` (green); open, optional.
