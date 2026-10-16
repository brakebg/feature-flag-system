---
paths:
  - "frontend/**"
---

# Frontend

Before changing UI code, read:

- `docs/product/ui.md` — routes, screens, interaction rules (8–8.6). Behaviour.
- `docs/architecture/frontend.md` — code structure, roles and accessible names (8.7).
- `docs/product/design/` — appearance only; exact values in `N · <screen>.html`.

`frontend/src/api/schema.d.ts` is generated from `backend/openapi.json` (gate 8). No UI
component library (gate 4).
