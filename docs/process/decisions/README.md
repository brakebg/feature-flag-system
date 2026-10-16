# Process decisions

Owner decisions about the process. Owner only; the builder reads them, never edits them.
Numbers are global across both decision folders (`docs/README.md`). Next free number: take the
highest in either folder plus one.

| # | Decision | Status |
| --- | --- | --- |
| 0001 | [Validation approach: single builder + black-box acceptance suite](0001-validation-approach.md) | Accepted |
| 0003 | [Locked-files check alerts, it does not block](0003-locked-files-alert-not-block.md) | Accepted |
| 0006 | [Remove the edit-block hook](0006-remove-edit-block-hook.md) | Accepted |
| 0007 | [Performance gate: hit rate blocks, p95 is report-only](0007-performance-gate-report-only.md) | Accepted |
| 0008 | [Accept known vulnerabilities, excluded from Trivy gate 10](0008-accept-known-vulnerabilities.md) | Accepted |
| 0010 | [One folder per spec; parallel builder sessions](0010-spec-folders.md) | Accepted |
| 0012 | [Spec 004: gate 16 enforces test-changes.md](0012-test-changes-gate.md) | Accepted |

Status values: Proposed → Accepted / Rejected → Superseded by NNNN.
