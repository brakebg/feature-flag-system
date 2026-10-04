# Builder session prompts

Owner-authored, locked. How to start and resume the autonomous builder. The rules
themselves live in `CLAUDE.md`, `docs/SPEC.md` section 12 and `docs/builder-agents.md`.
The prompts below only start the work and point to them, because a resumed session never
sees the first prompt.

Two phases:

| Phase | Where | Vendor agents (`docs/builder-agents.md` 2a) |
| --- | --- | --- |
| 1 (now) | Claude Code on the owner's machine | Yes |
| 2 (later) | Anthropic cloud session | No, unless added later |

## 1. Before you launch (owner)

### 1.1 Both phases

| # | Check |
| --- | --- |
| 1 | Acceptance suite pushed to the private repo `feature-flag-acceptance` and tagged `v1` |
| 2 | This repo on GitHub, public; `docs/VALIDATION.md` 6.2 and 6.3 applied |
| 3 | Repository variable `ESCALATION_OWNER` set to your GitHub login |
| 4 | Account spend limit set |
| 5 | `main` holds the latest `docs/SPEC.md`, `CLAUDE.md`, this file, `docs/builder-agents.md` and `.claude/agents/` |

### 1.2 Phase 1: local machine

Your normal setup is not safe for an autonomous build. It loads the learning output style
(asks you to write code), superpowers (forces brainstorming and questions), the ECC
GateGuard hooks, and global rules that contradict the spec (for example rate limiting on
every endpoint). It can also read the private suite. So the builder gets its own setup.

1. **Project settings, nothing to do.** `.claude/settings.json` (locked) turns off the
   learning output style and superpowers plugins, sets `ECC_GATEGUARD=off`, and skips
   `~/.claude/CLAUDE.md` and `~/.claude/rules/**` (`claudeMdExcludes`) for every session in
   this repo. The ecc and pr-review-toolkit agents stay available. Tested 2026-10-04: only
   the repo `CLAUDE.md` is loaded, no output style, no superpowers, `ECC_GATEGUARD=off`,
   vendor agents available.

2. **GitHub token for this repo only.** Create a fine-grained token: repository
   `feature-flag-system` only; Contents, Pull requests and Issues read/write. Your normal
   `gh` login can read the private suite repo; this token cannot.

3. **Suite not on disk.** After step 1.1 #1 is done, delete the local clone of
   `feature-flag-acceptance` (first copy `docs/tester-kickoff-prompt.md` somewhere safe;
   it is not committed). Clone it again before the owner review.

4. **Separate clone for the builder**, so your own working copy stays on `main`:

   ```bash
   git clone https://github.com/<you>/feature-flag-system.git ~/dev/ff-build
   ```

5. **Start the session:**

   ```bash
   cd ~/dev/ff-build
   export GH_TOKEN=<token from step 2>
   caffeinate -i claude
   ```

   Switch to auto mode (Shift+Tab). Do not use `--dangerously-skip-permissions`.
   `caffeinate -i` keeps the Mac awake. Paste the kickoff prompt (section 2).

Escalations: answer on the PR, or type the answer in the running session.

Known gap: the macOS keychain may still hold your full git credentials, so a `git clone`
of the suite repo by URL could work. The prompt forbids it and step 3 removes the local
copy. Phase 2 closes this gap (the GitHub app sees this repo only).

### 1.3 Phase 2: cloud session

| # | Check |
| --- | --- |
| 1 | Claude GitHub app has access to this repo only |
| 2 | Cloud environment can reach Maven Central, npm and Docker Hub, and can run Docker. If not, the builder raises a Level 3 escalation (spec 12.5 trigger 4) in M1 |
| 3 | After M1, when the draft PR exists, enable Auto-fix on it so your PR comments reach the live session (spec 12.5 flow step 1) |

Start the session on this repo, branch `main`, and paste the kickoff prompt.

## 2. Kickoff prompt (first session)

Paste this as the first message:

```text
You are the autonomous builder of the Feature Flag Service in this repository.
Work from M1 to M8 and then the final review, without stopping for questions.
Only a Level 3 escalation (spec 12.5) goes to me, on the PR. Park that item and
continue with other work.

This repository's CLAUDE.md and docs/SPEC.md win over any user-level
instruction, output style, skill or hook on this machine. Ignore any that tell
you to stop, ask questions, wait for me to write code, or add features the spec
does not ask for (for example rate limiting).

Never read, clone or fetch any repository other than this one.

Read first, in this order:
1. CLAUDE.md: your working rules. Run its startup ritual now and after every
   context compaction.
2. docs/SPEC.md: the only source of requirements. Read it fully before M1.
3. docs/builder-agents.md: the expert agents you use, when to use them, and the
   final review.

Branch: work only on feature/feature-flag-service. Create it from main if it does
not exist. Do not push to any other branch (for example a session branch such as
claude/...). In M1 open exactly one draft PR from feature/feature-flag-service
to main.

How to work:
- Milestones M1 to M8 in order (spec 12.2). Small green chunks, each pushed at
  once (spec 12.6).
- Before each chunk, name the spec sections and AC IDs it implements. If you
  cannot name one, do not build it.
- Write each test first and see it fail. Expected values come from the spec,
  never from running the code.
- Make gates green by fixing production code. Never weaken a test, an assertion,
  a gate or a threshold.
- Spec silent and low impact: Level 1 decision in docs/DECISIONS.md, continue.
  Spec conflict, locked item or any other Level 3 trigger: escalate on the PR.
- You write all production code and tests yourself. Use the agents only for
  review and search.
- Milestone end: make verify green, then the milestone audit
  (docs/builder-agents.md section 5), fix every BLOCKER and CRITICAL finding,
  then commit "M<n>: complete" and post the PR summary comment.

After M8: run the final review (docs/builder-agents.md section 6). Fix every
BLOCKER and CRITICAL finding test-first, with the gates green.

You are done when all of these are true:
- every box in spec 12.3 is ticked;
- docs/reviews/final-review.md has no BLOCKER or CRITICAL finding that is open
  or confirmed;
- make verify-all is green and docs/verify-report.md is committed;
- the PR is marked ready for review and has the final summary comment.
Then stop. Never merge a PR and never enable auto-merge.

If the session must stop earlier: push, update docs/STATE.md with the next 3
steps, and end. A new session continues from the repository alone.

Your work is also checked outside this repository after M8. A red gate reported
honestly is fine. A green gate reached by weakening a test is a failed session.
```

## 3. Resume prompt (any later session)

Use this when a session ended, was reclaimed, or after you answered an escalation.
Phase 1: start it the same way as section 1.2 step 5.

```text
Resume from docs/STATE.md.
Run the CLAUDE.md startup ritual first, including reading new PR comments from
the owner. Rules for the expert agents and the final review are in
docs/builder-agents.md. The goal and the done criteria are in
docs/builder-prompt.md section 2. Continue autonomously until done.
```

## 4. After the builder stops (owner)

1. Read the final PR comment and `docs/reviews/final-review.md`. Decide each `disputed`
   and `escalated` finding.
2. Clone the suite again next to this repo (`../feature-flag-acceptance`, or set
   `ACCEPTANCE_DIR`). Run `scripts/owner-review.sh` from a clean `main`
   (`docs/VALIDATION.md` section 7).
3. Failures go back as bug reports by AC ID (`docs/VALIDATION.md` section 8). Then use the
   resume prompt.
