---
description: Audit an OpenSpec change for traceability and spec quality
agent: spec-reviewer
---

Audit the OpenSpec change `$ARGUMENTS`.

If `$ARGUMENTS` is omitted or empty:
- Infer the change from the current git branch (stripping any Jira issue prefix such as `PROJ-123-` or `feature/`).
- If still ambiguous, list active changes with `openspec list --json` and prompt for selection.

Run `openspec status --change "<name>" --json` and `openspec validate "<name>" --strict`. Apply the full audit checklist across all change artifacts and return the structured review report with a clear verdict (APPROVE, APPROVE WITH COMMENTS, or REVISE).