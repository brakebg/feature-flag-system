# Decisions

Level 1 decisions of the builder for spec 005 (`docs/process/escalation.md` 12.5) and answers to
escalations. Owner decisions live in `SPEC.md` section 10 and `decisions/`.

Format: ID · date · spec section · decision · why.

## D-001 · 2026-10-20 · CLAUDE.md 9, kickoff prompt · Push only to `feature/ui-redesign`

The cloud session was started with a session branch (`claude/...`). The kickoff prompt
(`docs/process/builder-prompt.md` section 5) says: work only on the spec's working branch and
never push to a session branch. All work goes to `feature/ui-redesign`.

## D-002 · 2026-10-20 · environment · Docker daemon and npm 11 in the cloud container

The container has no running Docker daemon at start; the builder starts `dockerd` itself.
`npm ci` needs npm 11 (lock file v3, spec 001 D-010); the container has npm 10, so the builder
runs `npm i -g npm@11` first. No repository file changes.
