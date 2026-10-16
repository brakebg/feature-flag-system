---
paths:
  - "backend/src/main/resources/db/**"
---

# Database migrations

Read `docs/architecture/data-model.md` (4, 4.3 Migrations) first. Flyway checks the checksum of
every applied script, so editing an existing `V<n>__*.sql` breaks databases that already ran
it. A schema change goes into a new script unless the active spec says otherwise.
