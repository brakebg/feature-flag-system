---
paths:
  - "backend/**"
---

# Backend

Before changing backend code, read the current docs it must match:

- `docs/architecture/backend.md` — package structure (9.5).
- `docs/architecture/api.md` — Admin API and Evaluation API, caching (6, 7).
- `docs/architecture/security.md` — authentication, tokens, security rules (5).
- `docs/architecture/non-functional.md` — error format, limits, observability, config (9.1–9.4).
- `docs/architecture/data-model.md` — tables and validation (4).

The active spec wins over these for what it names. `backend/openapi.json` is generated and
checked by gate 8; never edit it by hand.
