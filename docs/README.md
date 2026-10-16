# Documentation map

Read this page first. It says where every kind of information lives.

| Folder / file | What it holds | Who changes it |
| --- | --- | --- |
| `docs/product/` | What the product does now: overview, UI behaviour, designs | Owner, in a spec's commits |
| `docs/architecture/` | How it is built now: stack, data model, security, APIs, deployment, testing; `decisions/` | Owner, in a spec's commits |
| `docs/process/` | How every spec is built: gates, traceability, working rules, escalation, session memory, builder prompt and agents, validation; `decisions/` | Owner |
| `docs/acceptance-criteria.md` | The current list of acceptance criteria (master) | Owner, in a spec's commits |
| `docs/specs/NNN-name/` | One folder per feature spec; rules in `docs/specs/README.md` | Owner; the builder only its memory files |
| `docs/design-compare/` | Generated side-by-side of built screens and designs | `scripts/design-compare.sh` |

`product/` and `architecture/` describe the current system. Spec 001 created them; later specs
change them.

## Where spec 001 sections went

| Spec 001 section | Now in |
| --- | --- |
| 1 Overview (goals, non-goals, glossary), 13 Future | `docs/product/overview.md` |
| 2 Tech stack | `docs/architecture/tech-stack.md` |
| 3 Architecture | `docs/architecture/overview.md` |
| 4 Data model | `docs/architecture/data-model.md` |
| 5 Authentication | `docs/architecture/security.md` |
| 6 Admin API, 7 Evaluation API | `docs/architecture/api.md` |
| 8–8.6 Frontend UI (routes, screens, rules) | `docs/product/ui.md` (designs: `docs/product/design/`) |
| 8.7 UI code structure | `docs/architecture/frontend.md` |
| 9–9.4 Errors, limits, observability, configuration | `docs/architecture/non-functional.md` |
| 9.5 Backend code structure | `docs/architecture/backend.md` |
| 9.6 Versioning, 10.2 Docker images, 10.3 compose, 10.5 CI | `docs/architecture/deployment.md` |
| 10, 10.1 Layout, 10.4 Local development | `docs/architecture/repository.md` |
| 11, 11.1 Test layers, 11.5 Test environments, 11.6 UI testing | `docs/architecture/testing.md` |
| 11.2 Acceptance criteria (original list) | `docs/specs/001-feature-flag-service/SPEC.md`; the current list is `docs/acceptance-criteria.md` |
| 11.3 Quality gates | `docs/process/quality-gates.md` |
| 11.4 Traceability | `docs/process/traceability.md` |
| 12, 12.2 Milestones, 12.3 Definition of done | `docs/specs/001-feature-flag-service/SPEC.md` |
| 12.1 Working rules, 12.4 Feedback loop | `docs/process/working-rules.md` |
| 12.5 Escalation | `docs/process/escalation.md` |
| 12.6 Session continuity | `docs/process/session-memory.md` |
