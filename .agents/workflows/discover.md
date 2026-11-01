---
name: /discover
id: discover
category: Workflow
description: Guided requirements elicitation — produces a discovery doc in workflow/backlog/discovery/
---

Capture requirements for an initiative and write the structured result to `workflow/backlog/discovery/<topic>.md` using `workflow/templates/discovery.md`.

**Input**: `$ARGUMENTS` may be a topic name (e.g. `checkout-flow`), raw initiative notes, or a path to an existing PRD/notes file.

---

### Steps

#### 1. Context Resolution & Topic Selection
- **Input Ingestion**: If `$ARGUMENTS` references a local file or provides preliminary notes, read and extract information from it.
- **Topic Name**: Derive a concise kebab-case topic name (e.g., `user-notifications`). If absent, ask the user.
- **Collision Check**: Check if `workflow/backlog/discovery/<topic>.md` already exists. If it does, ask whether to update the existing doc, resume discovery, or choose a new name.
- **Language Gate (mandatory)**: Ask with **AskUserQuestion** whether the discovery doc must be written in **castellano** or **English**. Preselect `content.default_language` from `workflow/config.yaml`.

#### 2. Domain & Spec Baseline (Context-First)
Before questioning, quickly inspect:
- `openspec/specs/`: Identify existing capabilities and system behaviors that might intersect or constrain this initiative; record them under **Constraints & assumptions**.
- `docs/domain.md`: Review existing ubiquitous language, glossary terms, and entity names to use consistent terminology.
- `docs/topology.md` / `docs/flows/` (if present): Understand existing service boundaries and user flows.

#### 3. Structured Requirements Interview
Conduct the interview using the **AskUserQuestion tool**, one focused round at a time (1–2 concise questions per round), incorporating the project context from step 2:

1. **Context & Problem**:
   - Who are the primary stakeholders and users?
   - What is the pain point today?
   - What does measurable success look like? (KPIs, business goals)
2. **Functional Needs**:
   - What must the system allow users/actors to do?
   - Tag each discrete need with candidate MoSCoW priorities (`[Must]`, `[Should]`, `[Could]`).
3. **Non-Functional Needs**:
   - Performance (latency, throughput, concurrency), security, compliance, auditability, accessibility.
   - Probe vague terms: "fast" → specific latency threshold (e.g., p95 < 200ms); "secure" → auth/role requirements.
4. **Scope Boundaries & Constraints**:
   - What is explicitly **Out of Scope** for this initiative? (Crucial to prevent scope creep).
   - Known technical, organizational, or timeline constraints and assumptions.
5. **System & Documentation Impacts**:
   - Check if this initiative introduces new domain concepts (`docs/domain.md`), external integrations or service boundaries (`docs/topology.md`), user/system sequences (`docs/flows/`), or setup prerequisites (`docs/README.md`).
   - Record these under **System & documentation impacts** so `/architect` and `/opsx:propose` can trace documentation tasks.

*Note: Allow the user to say "skip", "enough", or "done" at any point once essential sections have substance.*

#### 4. Open Questions Resolution
Review any unanswered or ambiguous points:
- Remind the user that `/architect` cannot generate backlog items from items in **Open questions**.
- Ask if they wish to resolve any open questions now, or move them explicitly to **Out of scope** (deferred to future initiatives).

#### 5. Write the Discovery Document
Write `workflow/backlog/discovery/<topic>.md` following `workflow/templates/discovery.md`.
Unanswered items go to **Open questions** — never invent answers.

#### 6. Summary & Next Steps
- Display a concise summary of the captured initiative (Problem, count of Must/Should functional needs, System/Doc impacts, Out of scope, and Open questions).
- Suggest the next step: `/architect <topic>` (or resolve remaining open questions first if blocking).
