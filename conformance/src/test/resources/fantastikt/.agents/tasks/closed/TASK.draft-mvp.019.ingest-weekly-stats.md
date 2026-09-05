---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.019
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.013
  - TASK.draft-mvp.014
  - TASK.draft-mvp.015
  - TASK.draft-mvp.017
  - TASK.draft-mvp.018
---

# TASK.draft-mvp.019: Ingest nflverse weekly player statistics

## Description

Implement the typed, streaming 2021–2025 weekly stats pipeline and explicit source-column-to-StatisticCode manifest.

## Requirements

- Own `etl-nflverse` weekly-stat DTO/parser/job packages, stat manifest, and fixtures.
- Include every Section 6.1 P0 statistic; explicitly map observed source names such as `attempts`, `passing_2pt_conversions`, `rushing_2pt_conversions`, `receiving_2pt_conversions`, `fg_att`, and `pat_att` without conflating component facts; missing optional stats warn, while missing core identity/passing/rushing/receiving contract fields fail the run.
- Insert only non-null numeric values and resolve players/teams through exact identities and normalized team codes.

## Deliverables

- [x] `NflversePlayerStatsJob` emits exact line/value/rejection counts and retained checksums for 2021–2025 in the typed result Task 029 later exposes through `ball etl sync stats`.
- [x] Manifest tests map actual fixture headers and reject incompatible schema drift.
- [x] Database assertions prove no fake play-level row exists after ingestion.

## Closure

```yaml
closed_at: 2026-09-03T04:38:18.395339800Z
closed_by: Codex
summary: Implemented typed streaming nflverse 2021-2025 weekly player-stat ingestion with a 21-stat explicit manifest, exact identity/team resolution, non-null decimal facts, schema drift classification, and idempotent artifact replay.
verification:
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL in 56s; 63/63 actions executed; 151 tests, 0 failures/errors/skips; 52 product files and 11 modules verified.
  - NflversePlayerStatsJobTest: 3 tests pass; weekly-2025 fixture yields rows=4, lines=2, values=36, rejections=2 (UNRESOLVED_PLAYER and UNKNOWN_TEAM), warnings=0, and replay retains exactly 2 lines.
  - Manifest asserts 21 P0 StatisticCodes, maps attempts/fg_att/pat_att and all three two-point component columns explicitly, rejects each of 20 missing core columns, and warns for each of 8 missing optional columns.
  - weekly-2025.csv SHA-256 2A828C7F02F2C2BAF0F0E1C3B0D0CD3EBC965C3559EBD217C081707AABF4A951; result returns artifact ID and checksum for fixed 2021-2025 filenames.
  - Post-ingestion PostgreSQL assertion finds 1 player_week_stat_line and 2 player_week_stat_value rows after restart and zero game/drive/play/statistical_event tables.
follow_ups:
  - none
```
