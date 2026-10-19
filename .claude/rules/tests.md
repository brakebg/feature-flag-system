---
paths:
  - "backend/src/test/**"
  - "frontend/**/*.test.*"
  - "frontend/e2e/**"
---

# Tests

Rules: `CLAUDE.md` section 4, `docs/architecture/testing.md` (test layers, environments, UI
testing), `docs/process/traceability.md` (AC and ERR tags). Gate 16: a test may change beyond plumbing only
when a criterion it is tagged with got a higher version or was retired (`<spec>/test-changes.md`).
