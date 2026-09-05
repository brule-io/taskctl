---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.032
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.008
  - TASK.draft-mvp.028
  - TASK.draft-mvp.031
---

# TASK.draft-mvp.032: Implement manual draft operations

## Description

Implement draft initialization, one-command manual picks, corrections, undo, and exact replay as the P0 live operational authority.

## Requirements

- Own CLI handlers and domain application services for `ball draft init`, `ball draft pick`, `ball draft correct`, `ball draft undo`, and `ball draft replay`.
- Resolve both assets and fantasy teams by stable UUID, exact external ID, or contextually typed handle; ambiguous asset or team handles return typed candidates and never guess across NFL Team, FantasyTeam, PlayerFantasyAsset, or TeamDefenseFantasyAsset namespaces.
- Recording a pick atomically removes availability and updates the selecting roster; invalid pick coordinates, duplicate selections, and illegal corrections fail without an event.

## Deliverables

- [ ] Command tests demonstrate pick, correction, undo, and replay through actual PostgreSQL transactions, including ambiguous asset/team candidate output and zero event append on failed resolution.
- [ ] A process restart yields identical event sequence, available set, and roster projection.
- [ ] Manual operation needs no network request after DraftBoardInput has been prefetched.

## Closure

```yaml
closed_at: null
closed_by: null
summary: null
verification:
  - null
follow_ups:
  - null
```
