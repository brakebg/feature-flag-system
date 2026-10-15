---
name: Builder task
about: "Owner only. Hands one merged change spec to an autonomous builder session (docs/builder-prompt.md section 6)."
title: "Implement spec <NNN>: <Title> (M<n>)"
labels: []
assignees: []
---
Spec folder: docs/specs/<NNN-name>/

<!-- Keep the line above as the first line, exactly in this form. The builder reads only that
line from this issue. Everything below is for people; the spec decides the work. -->

- Spec: `docs/specs/<NNN-name>/SPEC.md`, criteria in `acceptance-criteria.md`
- Spec PR: #<number> · decision: `decisions/<DDDD>-<name>.md`
- Milestone: M<n> · builder branch: `feature/<name>` (from `spec.json`)

## Before the builder starts (owner)

- [ ] The spec is merged on `main`; its row in `docs/specs/README.md` says `Ready`
- [ ] No `**Open:**` item left in section 10 of its `SPEC.md`
- [ ] Every owner step the spec names before the build is done
- [ ] The launch checks of `docs/builder-prompt.md` section 5 are true

## Start

Cloud session on this repo, branch `main`, with the kickoff prompt of `docs/builder-prompt.md`
section 5 and the task line `Task issue: #<this issue's number>`.

## Done when

Every box of the spec's definition of done (`SPEC.md` section 9) is ticked and the builder's
PR is ready for review. Merging that PR closes this issue.
