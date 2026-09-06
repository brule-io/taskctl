---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.025
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.015
  - TASK.draft-mvp.020
  - TASK.draft-mvp.021
  - TASK.draft-mvp.023
  - TASK.draft-mvp.024
---

# TASK.draft-mvp.025: Account for the complete ESPN draftable pool

## Description

Ingest every ESPN draftable row as a resolved player asset, team-defense asset, or explicit provisional/unresolved asset with snapshot-scoped fantasy eligibility.

## Requirements

- Own `etl-espn` player-pool job/DTO packages and player/DST fixtures.
- Treat ESPN as draftability and fantasy-position authority, not football identity or observed-position authority.
- Enforce `draftable rows = resolved players + resolved D/ST + explicit provisional/unresolved` and fail visibly on missing player IDs or unknown fantasy-position codes.

## Deliverables

- [x] Complete pagination fixture satisfies the no-silent-drop accounting equation with exact counts.
- [x] D/ST creates one TeamDefenseFantasyAsset backed by Team and no Person; a crosswalk-lag rookie creates a usable provisional player asset.
- [x] ESPN reruns append eligibility/status/projection observations without duplicating canonical assets.

## Closure

```yaml
closed_at: 2026-09-03T05:32:23.282715700Z
closed_by: Codex
summary: Implemented complete ESPN draftable-pool ingestion with strict pagination retention, exact identity accounting, team-backed D/ST assets, provisional rookie identities, fantasy eligibility, and immutable status/rank/projection observations.
verification:
  - clean .\\gradlew.bat clean verify: BUILD SUCCESSFUL; 67 actionable tasks, 66 executed; 64 product files; 40 XML reports, 169 tests, 0 failures/errors/skips
  - EspnPlayerPoolJobTest: two retained pages produce exactly 52 draftable rows = 50 resolved player assets + 1 resolved TeamDefenseFantasyAsset + 1 provisional player asset; rerun appends 52 eligibility and 52 observation assertions with 0 new assets
  - LeaguePersistenceIntegrationTest and MigrationIntegrationTest: V009 independently keys eligibility and ESPN observations to league/source snapshots; player and D/ST subtype lookups, ownership/rank/projection values, and restart round trips pass
follow_ups:
  - none
```
