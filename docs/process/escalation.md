# Escalation

From spec 001 (`docs/SPEC.md`) section 12.5. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

### 12.5 Escalation

The agent escalates to the owner only what it cannot safely decide itself, through the GitHub pull request, because cloud sessions are non-interactive and may end at any time.

**Three levels**

| Level | When | Agent does | Owner does |
| --- | --- | --- | --- |
| 1. Decide and log | Spec is silent; choice is low-impact, reversible, and touches no API, data model, security rule, acceptance criterion or gate | Picks the simplest option; adds a `DECISIONS.md` entry | Nothing |
| 2. Stuck, decide and continue | Stuck rule (12.4 step 5) exhausted, and a way on exists inside the Level 1 limits | Records the choice and what was tried in `builder/DECISIONS.md`; continues; lists it in `STATE.md` and the milestone summary | Reads it at the review |
| 3. Escalate and wait | Any trigger below | Raises an escalation (flow below); parks the item; continues independent work | Answers on the PR |

**Level 3 triggers**

1. Spec conflict: two sections contradict each other, or meeting one acceptance criterion breaks another.
2. A fix would change a locked item: an acceptance criterion, the API contract (sections 6 and 7), the data model (4), the security model (5), the banned-dependency lists, or a gate or threshold (11).
3. Stuck with no way on: the stuck rule is exhausted and no choice inside the Level 1 limits gets past it.
4. Outside the code: missing access or permission, Docker or a package registry unavailable, a dependency with a license or security problem.
5. Target unreachable: the cache hit-rate target still missed after optimisation, with measurements. A p95 above target is reported, not escalated (9.2, decision 0007).
6. Budget exceeded: more than 20 failed `make verify` runs within one milestone.
7. Risky action: force-push, history rewrite, deleting branches, changing repository settings, or anything outside this repository.

Before escalating, the agent checks that the spec, `DECISIONS.md` and earlier escalation answers do not already answer the question; if they do, it is Level 1.

**Flow on GitHub**

1. Working PR: at the start of its spec the agent opens one draft PR from the spec's working branch (`branch` in `<spec>/spec.json`; spec 001: `feature/feature-flag-service`) to `main`. All work is pushed there; the owner enables Auto-fix on it so PR comments reach the live session.
2. Raise: the agent commits `<spec>/builder/escalations/ESC-<NNN>.md` (template below), pushes, adds it to `STATE.md`, and posts a PR comment starting with `[ESCALATION ESC-<NNN>]` that contains the trigger number, the one question, options A/B/C and its recommendation. It also asks the question in the session.
3. Notify: workflow `.github/workflows/escalation-notify.yml` runs on `issue_comment` created. If the body starts with `[ESCALATION`, it adds the label `needs-human` and posts `@<owner> ESC-<NNN> needs your answer` as the GitHub Actions bot (owner from repository variable `ESCALATION_OWNER`). This is needed because the agent's comments appear under the owner's own account, and GitHub does not notify people about their own comments. The workflow has only `issues: write` and `pull-requests: write`, never checks out or runs code.
4. Answer: the owner replies on the PR with `ESC-<NNN>: <option>` plus any notes, or answers in the session. If the session that asked has ended, the owner starts a new cloud session with the prompt `Resume from <spec>/builder/STATE.md`; its startup ritual (12.6) reads the answer from the PR.
5. Close: the agent records the answer in `DECISIONS.md` under the escalation ID, sets the file's status to resolved, applies it, and posts `[RESOLVED ESC-<NNN>] applied option <X> in <commit>`. The workflow removes `needs-human` when no escalation remains open.
6. While waiting: never guess on the escalated item. Continue independent work; if none is left, push, update `STATE.md`, post `[WAITING] all remaining work blocked by ESC-<NNN>`, and end the session.
7. Batching: Level 3 items that are not yet blocking anything are collected and raised together in one escalation at the next milestone boundary.

**Escalation file template**

```markdown
# ESC-003: <short title>
Status: open            # open | resolved
Trigger: 2 (fix would change a locked item)
Milestone / gate / AC: M5 / gate 13 / AC-CACHE-9
Raised: <ISO time> at commit <sha>

## What happened
## Evidence (excerpt from build/verify-report.md)
## What was tried (attempts, approaches, commits)
## Options
A. <option> - trade-off
B. <option> - trade-off
## Recommendation
## Blocked meanwhile / continuing with
## Question (one, answerable with an option letter)
## Answer (filled in when resolved)
```
