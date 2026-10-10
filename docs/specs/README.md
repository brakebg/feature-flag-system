# Specs

Owner-authored, locked. One file per spec. A builder session works on exactly one spec: the
one its kickoff prompt names. `docs/STATE.md` records it as the active spec, so a resumed
session knows it too.

| # | Spec | Milestones | Branch | Status |
| --- | --- | --- | --- | --- |
| 001 | [Feature Flag Service v1](../SPEC.md) (`docs/SPEC.md`, the product spec) | M1–M8 | `feature/feature-flag-service` | Done, merged (PR #3), release 1.0.0 |
| 002 | [Spring Boot 4 upgrade](002-spring-boot-4-upgrade.md) | M9 | `feature/spring-boot-4` | Ready |

Rules:

- Spec 001 (`docs/SPEC.md`) defines the product and the working rules. It stays valid.
- A later spec changes only what it names; for those items it wins over spec 001 while it is
  active. After its PR is merged the owner folds the changes into `docs/SPEC.md`.
- Milestone numbers continue across specs (M9, M10, …), so the gate scripts keep working.
