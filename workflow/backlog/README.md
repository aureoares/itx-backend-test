# Backlog

Product artifacts that live **upstream** of OpenSpec. Flow:

```
discovery/  →  items/  →  openspec/changes/  →  code
(/discover)   (/architect, /item-enrich)  (/opsx:propose)
```

| Dir | Contents |
|-----|----------|
| `discovery/` | Requirements-gathering notes, one file per topic (`/discover`) |
| `items/` | Backlog items `<key>-<slug>.md` with frontmatter traceability (`/architect`) |
| `exports/jira/` | Jira-ready markup renders of items (`/item-jira`) |
| `exports/pr/` | PR descriptions when no platform CLI is available (`/pr-open`) |

Item IDs match issue tracker keys (`<project_key>-<n>`, e.g. Jira or GitHub Issues), provided by the user; drafts use `<project_key>-Dnn` until the real issue exists. An item's frontmatter (`status`, `change`) is the single place that tracks where it is in the pipeline. Commands update it; don't edit it by hand unless correcting drift.