---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.031
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.007
  - TASK.draft-mvp.021
  - TASK.draft-mvp.024
  - TASK.draft-mvp.025
  - TASK.draft-mvp.028
  - TASK.draft-mvp.033
---

# TASK.draft-mvp.031: Persist and replay the append-only draft ledger

## Description

Implement Draft and DraftPickEvent persistence plus a deterministic projection for recorded, corrected, retracted, and reconciled picks.

## Requirements

- Own the draft-ledger migration, PostgreSQL event adapter, replay reducer, and integration tests.
- Every event has a monotonic per-draft sequence, football pick coordinates, team, optional asset, source, caller/injected-clock occurrence time, artifact reference, and explicit supersession link.
- Replay orders solely by `(draft_id, event_sequence)`; `occurred_at` is provenance and clock skew cannot reorder state. A supersession must reference an existing earlier event in the same draft and preserve one active outcome per overall pick.
- Undo and correction append events; no command deletes or mutates history, and each DraftPick targets FantasyAssetId.

## Deliverables

- [ ] Round-trip tests cover PICK_RECORDED, PICK_CORRECTED, PICK_RETRACTED, and SNAPSHOT_RECONCILED with invalid/gapped sequence, out-of-order timestamp, forward/cross-draft supersession, and duplicate-active-pick rejection.
- [ ] Restarted replay produces the exact current picks and fantasy-team rosters from PostgreSQL alone.
- [ ] The Task 028 assembler receives current pick/roster projections and produces a complete Section 15 DraftBoardInput.
- [ ] Concurrent event appends serialize without gaps, duplicates, or partial projections.

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
