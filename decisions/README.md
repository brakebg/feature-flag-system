# Owner decisions

Decisions made by the owner about how this project is built and validated.
Owner only. The builder agent reads these but never edits them.

Not the same as `docs/DECISIONS.md`: that file holds the agent's own Level 1 decisions
(spec 12.5).

| # | Decision | Status |
| --- | --- | --- |
| 0001 | [Validation approach: single builder + black-box acceptance suite](0001-validation-approach.md) | Accepted |
| 0002 | [Rate limiting at the edge, not in the service](0002-rate-limiting-at-edge.md) | Accepted |
| 0003 | [Locked-files check alerts, it does not block](0003-locked-files-alert-not-block.md) | Accepted |
| 0004 | [Spec changes from the black-box testability review](0004-spec-testability-answers.md) | Accepted |
| 0005 | [Spec changes from review round 2 and tester questions](0005-spec-review-round-2.md) | Accepted |
| 0006 | [Remove the edit-block hook](0006-remove-edit-block-hook.md) | Accepted |
| 0007 | [Performance gate: hit rate blocks, p95 is report-only](0007-performance-gate-report-only.md) | Accepted |
| 0008 | [Accept known vulnerabilities, excluded from Trivy gate 10](0008-accept-known-vulnerabilities.md) | Accepted |
| 0009 | [Spec 002: upgrade the backend to Spring Boot 4.1](0009-upgrade-spring-boot-4.md) | Accepted |

Status values: Proposed → Accepted / Rejected → Superseded by NNNN.
