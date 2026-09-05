---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.033
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.022
  - TASK.draft-mvp.028
  - TASK.draft-mvp.029
  - TASK.draft-mvp.038
---

# TASK.draft-mvp.033: Rank the league-adjusted static board

## Description

Implement league-specific scoring, roster-slot algebra, replacement levels, VORP, flex scarcity, and the initial terminal draft board.

## Requirements

- Own `strategy` scoring/static-ranking packages and `ball draft board` rendering/tests.
- Derive fantasy points from stat inputs and explicit league rules; do not persist points as intrinsic Person properties.
- Exclude drafted assets supplied by Task 028's injected draft-state port, support rank-only degraded mode, handle player and D/ST assets, and label every recommendation with source snapshot IDs; Task 031 later connects the real persisted projection without changing this strategy boundary.
- Define the static latency boundary from an already-materialized DraftBoardInput through ranked output, use a monotonic clock, and record host CPU/memory, OS, JDK/JVM options, pool size, warm-up, sample count, distribution, and maximum.

## Deliverables

- [ ] Golden strategy fixtures prove scoring bonuses, flex/superflex replacement, VORP, scarcity, D/ST, and drafted-asset exclusion.
- [ ] Every recorded post-warm-up static refresh over a realistic ESPN-sized pool completes in under 250 ms on the local machine, with reproducible benchmark metadata.
- [ ] Board output exposes rank, handle, fantasy position, ECR, VORP, degraded state, and snapshot provenance.

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
