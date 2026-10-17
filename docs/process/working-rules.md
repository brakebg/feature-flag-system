# Working rules and feedback loop

From spec 001 (`docs/SPEC.md`) section 12.1, 12.4. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

### 12.1 Working rules

- Read the active spec fully, the `docs/product/` and `docs/architecture/` files the chunk touches, then `<spec>/builder/STATE.md`, before writing code. If code and spec disagree, the spec wins.
- Where the spec is silent, make a Level 1 decision (12.5): choose the simplest option, record it in `<spec>/builder/DECISIONS.md`, and continue.
- Do not add dependencies, services or features beyond this spec (no Redis, Kafka, component libraries, i18n, dark mode).
- Work in small green chunks and push every one (12.6). A milestone ends with a commit `M<n>: complete` and a summary comment on the PR (for information only; the agent does not wait for a reply).
- Never commit real secrets; only the documented dev defaults.

### 12.4 Feedback loop: how the agent reacts to failures

Failed tests drive the work: the agent changes production code until the gates pass, and never changes the gates to pass.

1. Work in small steps. After each change run `make verify-fast`; before every milestone commit run `make verify`. A milestone is done only when every gate active for it is green.
2. On failure, open `build/verify-report.md`, start with the first failing gate, read the failing test and the acceptance criterion it is tagged with, then fix the production code.
3. Forbidden ways to turn a gate green: deleting, skipping or disabling tests; weakening or removing assertions; lowering thresholds; adding exclusions; editing `docs/project-acceptance-criteria.md` or the banned-dependency lists; swallowing exceptions; adding test-only branches to production code. Gates 15 and 16 catch most of these; the rest are rules.
4. A test may be changed only when it contradicts this spec. The agent records the AC ID, the old expectation, the spec section that proves it wrong, and the fix in `<spec>/builder/DECISIONS.md`.
5. Stuck rule: if the same gate fails with the same error after 5 attempts, revert to the last green commit and try a different approach. After 3 different approaches: if a way on exists inside the Level 1 limits, record the gate, the error, what was tried and the choice in `<spec>/builder/DECISIONS.md` and continue; if not, raise a Level 3 escalation and continue with work that does not depend on it. List it in the final summary. A milestone with a red gate is never marked done.
6. Determinism: inject `Clock`; no `Thread.sleep` in tests (use Awaitility); pin Testcontainers image tags; no random data without a fixed seed. An intermittently failing test is a bug to fix, never something to retry.
