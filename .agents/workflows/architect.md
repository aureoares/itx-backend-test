---
name: /architect
id: architect
category: Workflow
description: Architect backlog items from a discovery doc into workflow/backlog/items/
---

Architect backlog items from the discovery doc `workflow/backlog/discovery/$ARGUMENTS.md` using `workflow/templates/item.md`.

**Steps**

1. Read the discovery doc. If it doesn't exist, list available ones and ask which to use (or suggest `/discover` first).

2. Read `workflow/config.yaml` (`jira.project_key`, `backlog.items_dir`).

3. Slice the functional needs into backlog items. Rules:
   - One verifiable outcome per item; split anything you couldn't implement in one OpenSpec change.
   - Each item gets a **Goal** (what & why), **Context**, and at least one Gherkin scenario per acceptance criterion, including one unhappy path. Every Then must be observable/testable.
   - If the discovery doc notes **System & documentation impacts**, reference relevant impacts in the item's **Context** (e.g. noting required updates to `docs/domain.md`, `docs/topology.md`, `docs/flows/`, or `docs/README.md`) so they carry forward into implementation.
   - Non-functional needs become acceptance criteria on the items they constrain, or their own item if cross-cutting.
   - Do NOT create items from **Open questions** — list them at the end as "blocked: needs answer".

4. **Item IDs are issue tracker keys (e.g. Jira).** Show the user the proposed item list (title + one-line goal) and ask them to provide the ID for each (e.g. `PROJ-123`). For items not yet created in the tracker, assign draft IDs `<project_key>-D01`, `-D02`, ... and note they must be renamed when the real key exists.

5. Write each item to `workflow/backlog/items/<id>-<slug>.md` (or `backlog.items_dir` from config) with frontmatter filled: `id`, `title`, `status: draft`, `discovery`, `priority` (MoSCoW from the discovery's goals), `language` (inherited from the discovery doc frontmatter, or confirm if missing).

6. Suggest `/item-enrich <id>` for the ones to refine, or `/item-jira <id>` to export.
