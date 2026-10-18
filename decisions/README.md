# Decision records — moved

The owner decision records now live by kind (docs layout, `docs/README.md`):

- Architecture decisions: `docs/architecture/decisions/`
- Process decisions: `docs/process/decisions/`
- Decisions about one spec only: that spec's `history/` folder

Numbers are global and unchanged, so a reference such as "decision 0008" still names one record.

| # | Decision | Status |
| --- | --- | --- |
| 0001 | [Validation approach: single builder + black-box acceptance suite](../docs/process/decisions/0001-validation-approach.md) | Accepted |
| 0002 | [Rate limiting at the edge, not in the service](../docs/architecture/decisions/0002-rate-limiting-at-edge.md) | Accepted |
| 0003 | [Locked-files check alerts, it does not block](../docs/process/decisions/0003-locked-files-alert-not-block.md) | Accepted |
| 0004 | [Spec changes from the black-box testability review](../docs/specs/001-feature-flag-service/history/0004-spec-testability-answers.md) | Accepted |
| 0005 | [Spec changes from review round 2 and tester questions](../docs/specs/001-feature-flag-service/history/0005-spec-review-round-2.md) | Accepted |
| 0006 | [Remove the edit-block hook](../docs/process/decisions/0006-remove-edit-block-hook.md) | Accepted |
| 0007 | [Performance gate: hit rate blocks, p95 is report-only](../docs/process/decisions/0007-performance-gate-report-only.md) | Accepted |
| 0008 | [Accept known vulnerabilities, excluded from Trivy gate 10](../docs/process/decisions/0008-accept-known-vulnerabilities.md) | Accepted |
| 0009 | [Spec 002: upgrade the backend to Spring Boot 4.1](../docs/architecture/decisions/0009-upgrade-spring-boot-4.md) | Accepted |
| 0010 | [One folder per spec; parallel builder sessions](../docs/process/decisions/0010-spec-folders.md) | Accepted |
| 0011 | Spec 003: high availability with per-instance caches and a database revision. On branch `feature/high-availability` until spec 003 is merged | Accepted |
| 0012 | [Spec 004: gate 16 enforces test-changes.md](../docs/process/decisions/0012-test-changes-gate.md) | Accepted |
| 0013 | Spec 005: redesign the admin UI with balanced colours and a shape language. On branch `feature/ui-redesign` until spec 005 is merged | Proposed |
