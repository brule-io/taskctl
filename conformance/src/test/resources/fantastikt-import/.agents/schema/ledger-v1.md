# `loom.agent/v1` ledger schema

Ledger files are UTF-8 without a byte-order mark and use LF line endings. Front matter supports only scalar values and indented block lists. Files contain exactly one H1 followed by the required H2 sections in the documented order.

## References and filenames

- Epic: `EPIC.<namespace>.<NNN>` in `EPIC.<namespace>.<NNN>.<kebab-slug>.md`.
- Roadmap: `ROADMAP.<namespace>.<NNN>` in `ROADMAP.<namespace>.<NNN>.<kebab-slug>.md`.
- Task: `TASK.<namespace>.<NNN>` or one dotted realization suffix such as `TASK.<namespace>.<NNN>.1`, in `TASK.<namespace>.<NNN>[.<realization>].<kebab-slug>.md`.
- Namespaces begin with a lowercase letter and contain lowercase letters, digits, or hyphens.
- Refs are immutable identity. Slugs are descriptive and do not participate in identity.

## Epic

Front matter fields are exactly `kind`, `schema`, and `ref`, in that order. The H2 sections are exactly `Description`, `Outcomes`, `Boundaries`, and `Closure`.

```yaml
---
kind: epic
schema: loom.agent/v1
ref: EPIC.namespace.000
---
```

An epic states one long-horizon outcome and invariants shared by its roadmaps.

## Roadmap

Front matter fields are exactly `kind`, `schema`, `ref`, `epic`, and `ordinal`, in that order. The H2 sections are exactly `Description`, `Outcomes`, `Exit Criteria`, and `Closure`. Exit criteria contain Markdown checkboxes.

```yaml
---
kind: roadmap
schema: loom.agent/v1
ref: ROADMAP.namespace.000
epic: EPIC.namespace.000
ordinal: 0
---
```

Ordinals are unique within an epic and express presentation order, not dependency edges.

## Task

Front matter fields are `kind`, `schema`, `ref`, `roadmap`, `effort`, `impact`, `depends`, and optional `realizes`, in that order. The H2 sections are exactly `Description`, `Requirements`, `Deliverables`, and `Closure`. Deliverables contain Markdown checkboxes.

```yaml
---
kind: task
schema: loom.agent/v1
ref: TASK.namespace.000
roadmap: ROADMAP.namespace.000
effort: MEDIUM
impact: HIGH
depends:
  - TASK.namespace.001
---
```

- `effort` is `LOW`, `MEDIUM`, or `HIGH`.
- `impact` is `HIGH`, `MEDIUM`, or `LOW`.
- Dependencies are unique, lexical, existing task refs. A closed task may not depend on an open task.
- `realizes` optionally decomposes one same-roadmap aggregate task by one level. A realization must transitively include every aggregate prerequisite, and open realizations cannot point to a closed aggregate.
- The open Kahn frontier contains tasks with no open dependency or realization prerequisite. Cycles are invalid.

## Closure receipt

Every record ends with this fenced YAML structure:

```yaml
closed_at: null
closed_by: null
summary: null
verification:
  - null
follow_ups:
  - null
```

Open records retain null values. Closure commands check the task deliverables or roadmap exit criteria, insert an RFC 3339 UTC timestamp ending in `Z`, actor, nonblank summary, one or more exact verification strings, optional task refs as follow-ups, and atomically move the file to its `closed` directory.

Epics currently have no closure command; close them only through a separately reviewed workflow change that preserves the invariant that no open roadmap belongs to a closed epic.
