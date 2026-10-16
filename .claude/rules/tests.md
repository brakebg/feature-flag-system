---
paths:
  - "backend/src/test/**"
  - "frontend/**/*.test.*"
  - "frontend/e2e/**"
---

# Tests

Rules: `CLAUDE.md` section 4, `docs/architecture/testing.md` (test layers, environments, UI
testing), `docs/process/traceability.md` (AC and ERR tags). Gate 16: a test whose IDs are not in
`<spec>/test-changes.md` may change only by the plumbing clause there.
