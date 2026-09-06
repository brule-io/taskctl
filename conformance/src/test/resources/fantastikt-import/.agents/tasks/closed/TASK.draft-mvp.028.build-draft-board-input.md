---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.028
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.017
  - TASK.draft-mvp.019
  - TASK.draft-mvp.024
  - TASK.draft-mvp.025
  - TASK.draft-mvp.026
  - TASK.draft-mvp.027
---

# TASK.draft-mvp.028: Build snapshot-explicit strategy read models

## Description

Implement the source/league materialized views or equivalent query ports and assemble the snapshot-explicit portion of DraftBoardInput behind an injected draft-state port.

## Requirements

- Own PostgreSQL read-model migrations/queries and DraftBoardInput assembly services/tests.
- Cover draftable assets, latest selected projections/rankings, recent player history, unresolved identity, and ETL health; Task 031 owns current pick/roster projections.
- `current` and `latest` always mean under inspectable caller-selected snapshot IDs; `generatedAt` comes from a caller-injected clock, and no read model may use implicit `now()` or silently mix belief states.

## Deliverables

- [x] A typed DraftBoardInput assembler fills every non-draft-state Section 15 field with inspectable source snapshot IDs and accepts a typed draft-state port.
- [x] Query tests prove unresolved assets, rankings, history, injuries, rules, economics, and draft order use one coherent snapshot selection.
- [x] Process restart reconstructs byte-equivalent serialized source/league input from PostgreSQL using a fixture draft-state port and the same injected clock instant.

## Closure

```yaml
closed_at: 2026-09-03T06:15:31.819924Z
closed_by: codex
summary: Implemented caller-selected draft-board read models and typed assembly across league, economics, player pool, projection, injury, ranking, history, unresolved identity, ETL health, and injected draft state, with deterministic serialization and PostgreSQL restart reconstruction.
verification:
  - .\\gradlew.bat verify --rerun-tasks => BUILD SUCCESSFUL; 183 tests, 0 failures, 0 errors; 78 product implementation/resource files conform.
  - DraftBoardInputAssemblerTest => every non-draft-state Section 15 field used one explicit selection; mixed history artifacts were rejected; generatedAt used the injected clock.
  - DraftBoardReadModelIntegrationTest => PostgreSQL league, economics, rules, draft order, draftable assets, projection, injury, ranking, history, provisional identity, unresolved forecast, and ETL health reconstructed byte-equivalent canonical input before and after full adapter restart with the same fixture draft state and clock.
  - V011 migration integration => fresh schema migrated exactly 11 revisions to create snapshot read-model index/views; no read-model query or migration contains implicit now/current/latest selection.
follow_ups:
  - none
```
