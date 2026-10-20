# M12 milestone audit

Range: origin/main...cb7c7e9   Date: 2026-10-20
Agents: spec-auditor (SA), test-auditor (TA), design-checker (DC, six designs incl. 6 · States),
final-reviewer frontend (FR). All read-only; tree and HEAD unchanged after the run (checked).

## Summary
| Severity | Total | Fixed | Rejected | Escalated | Open |
| --- | --- | --- | --- | --- | --- |
| BLOCKER | 1 | 1 | 0 | 0 | 0 |
| CRITICAL | 4 | 4 | 0 | 0 | 0 |
| MAJOR | 5 | 5 | 0 | 0 | 0 |
| MINOR | 11 | 8 | 1 | 0 | 2 |

## Findings
| ID | Severity | Problem | Status | Commit / reason |
| --- | --- | --- | --- | --- |
| SA-1 | BLOCKER | AC-UI-1, 3, 6, 7, 9 tested on 5 screens; the criteria file defines "the screens" as 11 states (empty groups, group without flags, 404, every dialog) | fixed | tests cover the 11 screens of the criteria; ARIA snapshots of the other 6 recorded from spec-005-ready (5a829fa, D-007) |
| SA-2 | CRITICAL | AC-UI-8 test data is not the criteria's "long data" (100-char name without spaces, 500-char descriptions); audit full key not checked | fixed | exact long data, audit full key checked (this commit) |
| TA-1 | CRITICAL | ARIA snapshot match is "contains", not "equals"; false states not compared | fixed | deep-equal compare and ARIA states list (5a829fa, D-007) |
| TA-2 | CRITICAL | `document.fonts.check` is true even without a face; the test cannot fail when a font is missing | fixed | loaded FontFace checked; seen failing without the condensed import (this commit) |
| TA-3 | CRITICAL | below 860 px only the flags column heads are checked, not the Created by / Updated cells | fixed | Created by / Updated cells checked; seen failing without hideSm (this commit) |
| SA-3 | MAJOR | AC-UI-2: Save changes of Edit flag and every switch not checked | fixed | Edit flag Save changes, every switch incl. Initial state (this commit) |
| TA-4 | MAJOR | panel-above-table check passes when a box is null | fixed | null boxes fail (this commit) |
| TA-5 | MAJOR | ARIA snapshot does not cover aria-current | fixed | aria-current in the ARIA states list (5a829fa) |
| DC-1 | MAJOR | prefixed Key field about 63 px instead of 52 px | fixed | Key field 52 px; baseline 3 re-recorded (D-008) |
| DC-2 | MAJOR | busy Sign in: content not centred, arrow circle stays | fixed | busy Sign in centred, no arrow (D-009) |
| DC-3..DC-6 | MINOR | counter gap 8, Edit flag textarea 2 rows, Load more weight 500, Delete flag paddings | fixed | gap 8, Edit flag 2 rows, dark 500 (D-009), Delete flag paddings |
| SA-4, FR-1 | MINOR | `#ffffff` used for fills outside the 4.2 exception | fixed | fills and the audit hero text use --color-surface |
| SA-5 | MINOR | Sign in button not the shared Button | fixed | Sign in is the shared Button dark (D-009) |
| SA-6 | MINOR | lock-file churn from npm 11.21 | rejected | npm 11.19.1 gives the same lines (D-010) |
| TA-6 | MINOR | Tab walk minimum count | open | MINOR, optional |
| TA-7 | MINOR | `FF_UPDATE_BASELINES=1` would also update the ARIA snapshots | fixed | baselines re-recorded only with screenshots.spec.ts (D-007, D-008) |
| FR-2 | MINOR | long components (LoginPage, GroupPanel, AuditPage) | open | MINOR, optional refactor; not done to keep the change small |

## Ignored suggestions
- none yet
