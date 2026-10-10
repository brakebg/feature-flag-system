# Builder expert agents and final review

Owner-authored. The builder reads this file and follows it. The builder never edits it
(locked, see `scripts/locked-paths.txt`). `docs/SPEC.md` and `CLAUDE.md` win over this file.

## 1. Why agents

You (the builder) wrote the code, so you cannot review it with fresh eyes. A subagent
starts with an empty context and sees only what you give it, so it finds what you
missed. The agents only review and search. They never change files.

The project agents live in `.claude/agents/`. They are part of the repo, so they work in
every session. They are the gate: they use the spec, the severity in section 4 and one
report format.

Vendor agents (section 2a) add deeper language checks. They are extra input, not the
gate. Use them when the session has them (local phase); skip them when it does not
(cloud phase) and write "not available" in the report.

## 2. The agents

| Agent | Checks | Used at |
| --- | --- | --- |
| `spec-auditor` | Built vs spec: missing, extra, exact values, drift, wrong decision level | Every milestone end, final review |
| `test-auditor` | Your tests can fail, values from the spec, exact assertions, tags, no weakening | M2 to M7 ends, final review |
| `security-reviewer` | Spec 5 and 10.2 security model, OWASP Top 10 | M3, M4, M5 ends, final review |
| `design-checker` | UI vs `docs/design/` and spec 8 | M7 end, final review |
| `final-reviewer` | Real bugs: correctness, errors, concurrency, cache, contract, silent failures | Final review |
| `Explore` (built in) | Find code across many files | Any time |

## 2a. Vendor agents (local phase)

| Agent | Checks | Used at |
| --- | --- | --- |
| `ecc:java-reviewer` | Spring Boot, JPA, transactions, Java concurrency | M2 to M5 ends, final review |
| `ecc:database-reviewer` | Flyway migrations, SQL, indexes, constraints | M2 end, final review |
| `ecc:react-reviewer` | React hooks, state, rendering, accessibility | M6, M7 ends, final review |
| `pr-review-toolkit:silent-failure-hunter` | Swallowed errors, bad fallbacks | Final review |
| `pr-review-toolkit:pr-test-analyzer` | Test gaps, edge cases | Final review |

Vendor agents do not know this project. They follow generic rules, and some of these
rules contradict the spec (for example "rate limiting on every endpoint", while spec 5.3
forbids a rate limiter in the service). So:

1. Add this to every vendor brief, after the template in section 3:

   ```
   Project rules: docs/SPEC.md is the only source of requirements and wins over any
   general best practice. Do not report what the spec leaves out on purpose (spec 1.2,
   5.3). Read-only: do not edit files. For each finding give file:line, severity
   (BLOCKER / CRITICAL / MAJOR / MINOR as defined in docs/builder-agents.md section 4),
   and a concrete failure scenario.
   ```

2. You map each vendor finding to section 4. Keep the vendor ID with a prefix, for
   example `JR-3` (java-reviewer), `DB-1`, `RR-2`, `SF-4`, `PT-1`.
3. A vendor finding that conflicts with the spec, or only asks for a general best
   practice the spec does not require, goes to "Ignored suggestions" with the reason.
4. Never install, update or remove plugins yourself.

## 3. Rules for using agents

1. You write all production code and all tests yourself. Do not give coding work to a
   subagent. One author per chunk keeps test-first honest and avoids edit conflicts.
2. Brief an agent with facts only: scope, git range, spec sections, and in a re-check
   the earlier findings plus your rejection reasons. Do not add your opinion of the code
   ("this part is fine", "I already handled X"). That removes the fresh eyes.
3. Brief template:

   ```
   Scope: <M4 | final | backend | frontend | infra>
   Git range: <base>...HEAD
   Spec sections: <list>
   Re-check: <no | earlier findings + rejection reasons, pasted>
   Return the output format from your agent file.
   ```

4. Run independent agents in parallel (one message, several Agent calls).
5. Findings are input, not orders. The spec wins. Ignore any suggestion to weaken a
   test, an assertion, a gate or a threshold, and note it in the review file.
6. Agents cost time. Use them at the points in sections 5 and 6, not after every chunk.

## 4. Severity

| Severity | Meaning | You must |
| --- | --- | --- |
| BLOCKER | A MUST or AC broken or missing; auth bypass; data loss; secret leak; app does not start; weakened test | Fix before moving on |
| CRITICAL | Wrong behaviour on a spec path; security weakness; race or data bug; 500 where the spec gives a status; an AC with no test that can fail | Fix before moving on |
| MAJOR | Real risk that does not break the spec today | Fix only if small (about 30 lines or less), inside the spec, no new dependency. Else leave open for the owner |
| MINOR | Style, naming, comments | Optional |

A BLOCKER or CRITICAL without a concrete failure scenario counts as MAJOR.

## 5. Milestone audit

When: at each milestone end, after `make verify` is green, before the `M<n>: complete`
commit. M8 has no separate milestone audit; the final review (section 6) covers it.

| Milestone | Agents (in parallel) |
| --- | --- |
| M1 | `spec-auditor` |
| M2 | `spec-auditor`, `test-auditor`, `ecc:java-reviewer`, `ecc:database-reviewer` |
| M3, M4, M5 | `spec-auditor`, `test-auditor`, `security-reviewer`, `ecc:java-reviewer` |
| M6 | `spec-auditor`, `test-auditor`, `ecc:react-reviewer` |
| M7 | `spec-auditor`, `test-auditor`, `design-checker`, `ecc:react-reviewer` |
| M9 | `spec-auditor`, `test-auditor`, `security-reviewer`, `ecc:java-reviewer` (spec 002; no separate final review) |

Vendor agents (`ecc:`, `pr-review-toolkit:`) only when available (section 2a).

Steps:

1. Run the agents with git range `<previous M complete commit>...HEAD` (M1: `main...HEAD`).
2. Fix every BLOCKER and CRITICAL the same way as in section 6 steps 3 and 4.
3. `make verify` green again.
4. In the milestone PR comment, add one line: findings per severity, fixed, rejected
   (with reason), open MAJOR.
5. No re-check round for milestone audits. The final review checks everything again.

## 6. Final review

When: after all M8 work is done and `make verify-all` is green. Before the definition of
done (spec 12.3) and before the PR is marked ready.

### Step 1: run the agents in parallel, git range `main...HEAD`

| Agent | Scope |
| --- | --- |
| `final-reviewer` | `backend` |
| `final-reviewer` | `frontend` |
| `final-reviewer` | `infra` |
| `security-reviewer` | `final` |
| `spec-auditor` | `final` |
| `test-auditor` | `final` |
| `design-checker` | `final`, with the screenshot paths from the latest Playwright run, if any |
| `ecc:java-reviewer` | `backend/` (if available) |
| `ecc:database-reviewer` | migrations and SQL (if available) |
| `ecc:react-reviewer` | `frontend/` (if available) |
| `pr-review-toolkit:silent-failure-hunter` | `backend/` and `frontend/` (if available) |
| `pr-review-toolkit:pr-test-analyzer` | all tests (if available) |

### Step 2: write the report

Merge all findings into `docs/reviews/final-review.md` (template in section 7). Keep each
agent's ID. When two agents report the same defect, keep one row, list both IDs, use the
higher severity. Commit and push the report before fixing anything. It is your memory if
the session ends.

### Step 3: triage each BLOCKER and CRITICAL

- **Confirm**: write a test that shows the defect and see it fail. Then the finding is
  `confirmed`.
- **Reject**: only when the finding contradicts the spec or you cannot make it fail.
  Write the reason with the spec section or the evidence (the test you wrote, which
  passes). Status `rejected`. Never reject because a fix is hard.
- **Locked item**: if the fix needs a change to a locked file or value, raise a Level 3
  escalation (spec 12.5). Status `escalated`. Continue with the other findings.

### Step 4: fix each confirmed finding

1. The failing test from step 3 is already there. Tag it with the AC or `ERR-*` ID it
   relates to. If there is none, do not invent a tag (gate 14 rejects unknown IDs); put
   the finding ID in a code comment.
2. Fix the production code. Fix the general behaviour, not only the one input.
3. `make verify-fast` green. Commit `M8 review: fix <ID> <what changed>` with the
   `AC:` and `Spec:` trailers. Push.
4. Set the finding to `fixed` with the commit SHA in the report. Update `docs/STATE.md`.

### Step 5: re-check (rounds 2 to 4)

Round 1 is step 1. Steps 4 and 5 repeat at most 3 times (rounds 2, 3 and 4).

1. `make verify-all` green.
2. Run again only the agents that reported a BLOCKER or CRITICAL (project or vendor). Give each: the git
   range of the fix commits, its earlier findings, and your rejection reasons.
3. Results:
   - `fixed` confirmed: done.
   - `not fixed`, or a new BLOCKER or CRITICAL from the fixes: fix it (step 4).
   - `accept rejection`: done.
   - `still holds` on a rejection: status `disputed`. Do not argue further. The owner
     decides at the final review.
4. After round 4 (3 fix attempts), every BLOCKER or CRITICAL that is still not `fixed`
   gets status `blocked`:
   - add it to `docs/BLOCKERS.md`;
   - raise one Level 3 escalation for all `blocked` findings (spec 12.5, trigger 3: the
     final review is the last step, so all remaining work depends on it);
   - write the ESC ID in the report row.
5. When the owner answers, apply the answer. The finding becomes `fixed` (with the commit
   SHA), or `owner-accepted` if the owner accepts it as it is.

### Step 6: finish

1. `make verify-all` green. Commit the final `docs/verify-report.md`.
2. Tick spec 12.3.
3. The report shows no BLOCKER or CRITICAL with status `open`, `confirmed` or `blocked`.
   While a `blocked` escalation is open, do not mark the PR ready: follow spec 12.5 flow
   step 6 (post `[WAITING] all remaining work blocked by ESC-<NNN>` and end the session).
4. Mark the PR ready for review. Post the final PR comment: what was built, gate results,
   review counts per severity (fixed / rejected / disputed / escalated / owner-accepted /
   open MAJOR),
   decisions, blockers.
5. Stop. Never merge.

## 7. Report template: `docs/reviews/final-review.md`

```markdown
# Final review

Range: main...<sha>   Round: 1 | 2 | 3 | 4   Date: <ISO date>

## Summary
| Severity | Total | Fixed | Rejected | Disputed | Escalated | Blocked | Owner-accepted | Open |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BLOCKER | | | | | | | | |
| CRITICAL | | | | | | | | |
| MAJOR | | | | | | | | |
| MINOR | | | | | | | | |

## Findings
| ID(s) | Severity | Area | File:line | Spec / AC | Problem | Status | Commit / reason |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FR-3, SR-1 | CRITICAL | backend | ... | 5.4 | ... | fixed | abc1234 |

## Ignored suggestions
- <ID>: <suggestion that would weaken a test, gate or threshold, or break the spec>

## Not checked by any agent
- <area>: <why>
```
