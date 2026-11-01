---
name: /item-enrich
id: item-enrich
category: Workflow
description: Enrich an existing backlog item — edge cases, scenarios, DoD
---

Enrich the backlog item `$ARGUMENTS` (an issue key like `PROJ-123`, or a path) in `workflow/backlog/items/`.

**Steps**

1. Locate the item file. If the argument is ambiguous or missing, list items with `status: draft` and ask.

   **Language gate (mandatory):** if the item has `language:` in its frontmatter, write all additions in that language. If it doesn't, ask with **AskUserQuestion** (castellano or English, preselecting `content.default_language` from `workflow/config.yaml`) and record it in the frontmatter.

2. Cross-check against `openspec/specs/` and the source discovery doc: does it contradict current behavior? Does it overlap another backlog item?

3. Enrich the item:
   - **Scenarios** — add missing unhappy paths: invalid input, permissions, empty states, concurrency, limits. Every Then must be observable/testable.
   - **Notes & edge cases** — record decisions and discovered constraints.
   - **Estimate** — propose one with a line of justification (points or hours per project convention).
   - **Ambiguities** — if any acceptance criterion is untestable as written, ask the user with **AskUserQuestion** rather than guessing.

4. Update frontmatter: `status: enriched`, `estimate`.

5. Run the `item-reviewer` subagent on the result. If it returns REVISE, fix the findings and re-run once.

6. Suggest next: `/item-jira <id>` to export, or `/opsx:propose` referencing the item to start implementation.
