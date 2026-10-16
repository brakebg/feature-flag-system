---
paths:
  - "**/Dockerfile"
  - "docker-compose*.yml"
  - "ops/**"
  - "perf/**"
  - ".github/**"
  - "Makefile"
---

# Build, images and CI

Read `docs/architecture/deployment.md` (images, compose, CI, versioning) and
`docs/architecture/repository.md` (layout, local development). Gate commands and thresholds:
`docs/process/quality-gates.md`. `.github/workflows/locked-files-guard.yml` and
`escalation-notify.yml` are owner files.
