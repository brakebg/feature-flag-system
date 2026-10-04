# Feature Flag Service

Boolean feature flags in groups, an admin UI, and a read-only Evaluation API for
client services. Spec: `docs/SPEC.md`.

Work in progress. The full README (setup, configuration, API usage) comes in M8.

## Quick start

```bash
make up        # UI http://localhost:3000, login admin / admin123
make down
make verify    # all quality gates, report in build/verify-report.md
```
