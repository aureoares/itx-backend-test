---
id: PROJ-000        # Issue/item key, provided by the user (placeholder PROJ-D01 if not created yet)
title: <short title>
status: draft        # draft | enriched | estimated | proposed | in-progress | done
discovery: <topic>   # source file in workflow/backlog/discovery/
change: ~            # openspec change name once /opsx-propose runs
priority: ~          # must | should | could | wont (MoSCoW)
estimate: ~          # story points or hours, per project convention
language: ~          # es | en — set by /architect after reading discovery doc or asking the user
---

# PROJ-000 — <short title>

## Goal

<what must exist or behave differently when this item is done, and why>

## Context

<relevant background: affected modules, related specs, dependencies on other items>

## Acceptance criteria

```gherkin
Scenario: <name>
  Given <precondition>
  When <action>
  Then <observable outcome>
```

## Notes & edge cases

<!-- Added by /item-enrich: error paths, permissions, empty states, limits, documentation impacts. -->

## Estimate

<!-- Added by /item-enrich: estimation and rationale. -->

## Definition of Done

- [ ] Acceptance criteria covered by tests
- [ ] Spec deltas archived in openspec/specs/
- [ ] Work merged into the integration branch
