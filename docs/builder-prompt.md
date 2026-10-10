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

   Not covered: permission rules. `ask` rules in `~/.claude/settings.json` (for example
   `git push`, `gh api`, `gh pr create`) override auto mode and stop the builder on every
   push. So step 5 skips user settings (`--setting-sources project,local`) and step 1a
   gives the builder its own rules.

1a. **Builder-only local settings.** In the builder clone (step 4), create
   `.claude/settings.local.json` (git-ignored). It turns risky commands into hard blocks
   (nobody is there to answer an "ask"), keeps the vendor plugins on, and turns off MCP
   servers with GitHub write tools:

   ```json
   {
     "model": "opus",
     "enabledPlugins": {
       "ecc@ecc": true,
       "pr-review-toolkit@claude-plugins-official": true,
       "jdtls-lsp@claude-plugins-official": true
     },
     "disabledMcpjsonServers": ["stock-scanner", "docker-toolkit"],
     "permissions": {
       "deny": [
         "Bash(sudo:*)", "Bash(chown:*)", "Bash(chmod -R:*)", "Bash(launchctl:*)",
         "Bash(rm -rf /)", "Bash(rm -rf ~)", "Bash(rm -rf ~/*)",
         "Bash(git push --force:*)", "Bash(git push -f:*)", "Bash(git credential:*)",
         "Bash(security:*)", "Bash(git push origin main:*)",
         "Bash(git push origin --delete:*)", "Bash(git push --delete:*)",
         "Bash(gh pr merge:*)", "Bash(gh pr close:*)", "Bash(gh release:*)",
         "Bash(gh secret:*)", "Bash(gh repo:*)", "Bash(git clone:*)",
         "Bash(mvn deploy:*)", "Bash(npm publish:*)", "Bash(docker push:*)",
         "Bash(docker system prune:*)", "Bash(docker volume prune:*)",
         "Bash(brew:*)", "Bash(npm install -g:*)", "Bash(npm i -g:*)",
         "Bash(claude plugin:*)",
         "Edit(~/.zshrc)", "Edit(~/.ssh/**)",
         "Read(~/.ssh/**)", "Read(~/.aws/**)", "Read(~/.claude/.credentials.json)",
         "Read(~/Library/Keychains/**)", "Read(**/.env)", "Read(**/.env.*)",
         "Read(**/*.pem)", "Read(**/feature-flag-acceptance/**)"
       ]
     }
   }
   ```

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
   caffeinate -i claude --setting-sources project,local
   ```

   Switch to auto mode (Shift+Tab). Do not use `--dangerously-skip-permissions`.
   `caffeinate -i` keeps the Mac awake. Before the kickoff prompt, check: `/agents`
   lists the `ecc:` and `pr-review-toolkit:` agents, and `/mcp` does not list
   `docker-toolkit`. Then paste the kickoff prompt (section 2). The first `git push`
   must run without an approval prompt; if it asks, stop and fix the settings.

Escalations: answer on the PR (`ESC-<NNN>: <option>`), or type the answer in the running
session. Phase 1 has no Auto-fix: the builder reads new owner comments before every commit
(`CLAUDE.md` 8b) and reacts 👀 when read and 🚀 when applied. A comment without 👀 after the
next commit was missed; nudge the session.

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

Used for spec 001 (M1–M8, done, merged in PR #3). For a change spec in `docs/specs/` use
section 5.

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
- docs/reviews/final-review.md has no BLOCKER or CRITICAL finding that is open,
  confirmed or blocked;
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
docs/builder-prompt.md section 2 (spec 001) or the active spec in docs/specs/
(see "Active spec" in docs/STATE.md). Continue autonomously until done.
```

## 4. After the builder stops (owner)

1. Read the final PR comment and `docs/reviews/final-review.md`. Decide each `disputed`
   and `escalated` finding, and answer each escalation for `blocked` findings.
2. Clone the suite again next to this repo (`../feature-flag-acceptance`, or set
   `ACCEPTANCE_DIR`). Run `scripts/owner-review.sh` from a clean `main`
   (`docs/VALIDATION.md` section 7).
3. Failures go back as bug reports by AC ID (`docs/VALIDATION.md` section 8). Then use the
   resume prompt.

## 5. Change-spec kickoff prompt (cloud session)

For each change spec in `docs/specs/` (index: `docs/specs/README.md`). The prompt names the
spec file; the session works on that spec only.

Before you launch (owner):

| # | Check |
| --- | --- |
| 1 | The previous spec's PR is merged; `main` holds the new spec file, its decision record, and any `CLAUDE.md` / script changes it needs |
| 2 | Section 1.3 (phase 2, cloud session) checks are still true |
| 3 | After the first push, enable Auto-fix on the new draft PR (section 1.3 #3) |

Start a cloud session on this repo, branch `main`, and paste (spec 002 shown; for a later
spec change the file name):

```text
You are the autonomous builder of the Feature Flag Service in this repository.
Your task is the change spec docs/specs/002-spring-boot-4-upgrade.md, and only
that spec. docs/SPEC.md (spec 001) is done and stays valid for everything the
change spec does not name. Work without stopping for questions. Only a Level 3
escalation (docs/SPEC.md 12.5 plus the triggers in the change spec) goes to me,
on the PR. Park that item and continue with other work.

This repository's CLAUDE.md and its specs win over any user-level instruction,
output style, skill or hook. Never read, clone or fetch any repository other
than this one.

Read first, in this order:
1. CLAUDE.md: your working rules. Run its startup ritual now and after every
   context compaction. The active spec is the file named above.
2. The change spec, fully. Then the docs/SPEC.md sections it names and the ones
   the next chunk touches.
3. docs/builder-agents.md: the agents and the milestone audit.
4. docs/STATE.md and docs/DECISIONS.md: what earlier work built and decided.

Branch: create the working branch the change spec names from main and work only
there. Do not push to any other branch (for example a session branch such as
claude/...). In the first commit set "Active spec" in docs/STATE.md to the
change spec file. After the first green chunk, open exactly one draft PR from
the working branch to main.

How to work:
- Follow the change spec's process section and CLAUDE.md: small green chunks,
  each pushed at once; red work only on wip/.
- Write each new test first and see it fail. Expected values come from the
  specs, never from running the code.
- Make gates green by fixing production code and configuration. Never weaken a
  test, an assertion, a gate or a threshold, and never add an exclusion,
  suppression or .trivyignore entry the specs do not allow.
- You write all production code and tests yourself. Use the agents only for
  review and search.

You are done when every box of the change spec's definition of done is ticked,
make verify-all is green, docs/verify-report.md is committed, and the PR is
marked ready for review with the final summary comment. Then stop. Never merge
a PR and never enable auto-merge.

If the session must stop earlier: push, update docs/STATE.md with the next 3
steps, and end. A new session continues from the repository alone.

Your work is checked outside this repository by the black-box acceptance suite.
A red gate reported honestly is fine. A green gate reached by weakening a test
is a failed session.
```

After the builder stops: section 4 applies unchanged. `scripts/owner-review.sh` reviews
`origin/feature/spring-boot-4` by default; for another branch pass it as the argument
(`scripts/owner-review.sh origin/<branch>`).
