---
name: /review-item
id: review-item
category: Workflow
description: Audit a backlog item for quality, testability, and client readiness
agent: item-reviewer
---

Audit the backlog item `$ARGUMENTS` under `workflow/backlog/items/`.

If `$ARGUMENTS` is omitted or empty:
- Inspect `workflow/backlog/items/` for recently modified items or items with `status: draft` or `status: enriched`, and prompt the user to select one.

Apply the full audit checklist (frontmatter, language gate, sizing, goal quality, Gherkin criteria) and return the structured review report with a clear verdict (APPROVE, APPROVE WITH COMMENTS, or REVISE).
