---
name: Builder task
about: "Owner only. Hands one tagged change spec to an autonomous builder session (docs/process/builder-prompt.md section 6)."
title: "Implement spec <NNN>: <Title> (M<n>)"
labels: []
assignees: []
---
Spec folder: docs/specs/<NNN-name>/

<!-- Keep the line above as the first line, exactly in this form. The builder reads only that
line from this issue. Everything below is for people; the spec decides the work. -->

- Spec: `docs/specs/<NNN-name>/SPEC.md` on branch `feature/<name>`, tag `spec-<NNN>-ready`
- Criteria: the `spec <NNN>` lines in `docs/project-acceptance-criteria.md`
- Milestone: M<n> (from `spec.json`)

## Before the builder starts (owner)

- [ ] `spec.json` says `"status": "ready"` on the branch head
- [ ] No `**Open:**` item left in section 10 of its `SPEC.md`
- [ ] Every owner step the spec names before the build is on the branch
- [ ] `/handover-check #<this issue>` says READY (launch checks of `docs/process/builder-prompt.md` section 5)
- [ ] The tag `spec-<NNN>-ready` is pushed on the commit `/handover-check` checked

## Start

Cloud session on this repo, branch `main` (the builder switches to the spec's branch), with the kickoff prompt of `docs/process/builder-prompt.md`
section 5 and the task line `Task issue: #<this issue's number>`.

## Done when

Every box of the spec's definition of done (`SPEC.md` section 9) is ticked and the builder's
PR is ready for review. Merging that PR closes this issue.
