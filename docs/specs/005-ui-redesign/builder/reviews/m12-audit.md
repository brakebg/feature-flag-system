# M12 milestone audit

Range: origin/main...cb7c7e9   Date: 2026-10-20
Agents: spec-auditor (SA), test-auditor (TA), design-checker (DC, six designs incl. 6 · States),
final-reviewer frontend (FR). All read-only; tree and HEAD unchanged after the run (checked).

## Summary
| Severity | Total | Fixed | Rejected | Escalated | Open |
| --- | --- | --- | --- | --- | --- |
| BLOCKER | 1 | | | | 1 |
| CRITICAL | 4 | | | | 4 |
| MAJOR | 5 | | | | 5 |
| MINOR | 11 | | | | 11 |

## Findings
| ID | Severity | Problem | Status | Commit / reason |
| --- | --- | --- | --- | --- |
| SA-1 | BLOCKER | AC-UI-1, 3, 6, 7, 9 tested on 5 screens; the criteria file defines "the screens" as 11 states (empty groups, group without flags, 404, every dialog) | open | |
| SA-2 | CRITICAL | AC-UI-8 test data is not the criteria's "long data" (100-char name without spaces, 500-char descriptions); audit full key not checked | open | |
| TA-1 | CRITICAL | ARIA snapshot match is "contains", not "equals"; false states not compared | open | |
| TA-2 | CRITICAL | `document.fonts.check` is true even without a face; the test cannot fail when a font is missing | open | |
| TA-3 | CRITICAL | below 860 px only the flags column heads are checked, not the Created by / Updated cells | open | |
| SA-3 | MAJOR | AC-UI-2: Save changes of Edit flag and every switch not checked | open | |
| TA-4 | MAJOR | panel-above-table check passes when a box is null | open | |
| TA-5 | MAJOR | ARIA snapshot does not cover aria-current | open | |
| DC-1 | MAJOR | prefixed Key field about 63 px instead of 52 px | open | |
| DC-2 | MAJOR | busy Sign in: content not centred, arrow circle stays | open | |
| DC-3..DC-6 | MINOR | counter gap 8, Edit flag textarea 2 rows, Load more weight 500, Delete flag paddings | open | |
| SA-4, FR-1 | MINOR | `#ffffff` used for fills outside the 4.2 exception | open | |
| SA-5 | MINOR | Sign in button not the shared Button | open | |
| SA-6 | MINOR | lock-file churn from npm 11.21 | open | |
| TA-6 | MINOR | Tab walk minimum count | open | |
| TA-7 | MINOR | `FF_UPDATE_BASELINES=1` would also update the ARIA snapshots | open | |
| FR-2 | MINOR | long components (LoginPage, GroupPanel, AuditPage) | open | |

## Ignored suggestions
- none yet
