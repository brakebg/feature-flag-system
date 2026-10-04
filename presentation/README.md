# Presentation

Executive deck: how an autonomous AI agent (Claude Code, non-interactive) builds the
Feature Flag Service, and how an independent black-box acceptance suite checks the work.

| File | What it is |
| --- | --- |
| `autonomous-development.md` | Source. Marp Markdown, 15 slides, speaker notes in `<!-- ... -->` |
| `autonomous-development.html` | Rendered slides for the browser (press `P` for presenter view with notes) |
| `autonomous-development.pdf` | Rendered slides as PDF |

## Render

Needs [Marp CLI](https://github.com/marp-team/marp-cli) and Chrome (for PDF and PNG).
`--html` is required: the deck uses HTML blocks for columns and the diagram.

```bash
cd presentation
marp autonomous-development.md --html -o autonomous-development.html
marp autonomous-development.md --html --pdf --allow-local-files -o autonomous-development.pdf
```

Preview slides as images (for checking layout):

```bash
marp autonomous-development.md --html --images png -o /tmp/deck/slide.png
```

Facts in the deck come from `docs/SPEC.md`, `docs/VALIDATION.md`, `decisions/`, `CLAUDE.md`,
`scripts/` in this repo, and from the `feature-flag-acceptance` repo (as of 2026-10-04).
