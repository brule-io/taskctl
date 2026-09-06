---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.018
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.011
  - TASK.draft-mvp.012
  - TASK.draft-mvp.016
---

# TASK.draft-mvp.018: Persist weekly aggregate statistic facts

## Description

Add the long-form PlayerWeekStatLine and PlayerWeekStatValue schema and ports at nflverse's native weekly aggregate fidelity.

## Requirements

- Own the weekly-stat migration, PostgreSQL stat adapter, and schema tests.
- Use NUMERIC/BigDecimal and a foreign-keyed Statistic vocabulary for all values.
- Uniqueness includes player, football coordinate, source, and artifact; no Game, Drive, Play, or StatisticalEvent identity may be fabricated.

## Deliverables

- [x] Schema and adapter round trips preserve decimal values and explicit season/game-type/week/source/artifact coordinates.
- [x] Duplicate line/value constraints fail atomically with evidence.
- [x] Architecture tests prove weekly aggregate writes cannot create play-level entities.

## Closure

```yaml
closed_at: 2026-09-03T04:30:31.547592100Z
closed_by: Codex
summary: Added normalized PostgreSQL weekly aggregate stat-line/value persistence with typed source/artifact coordinates, canonical BigDecimal round trips, atomic uniqueness enforcement, and aggregate-only architecture proof.
verification:
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL in 55s; 63/63 actions executed; 148 tests, 0 failures/errors/skips; 49 product files and 11 modules verified.
  - WeeklyStatisticsPersistenceIntegrationTest: 2 PostgreSQL tests pass; 1234.5678901234567890123456789 round-trips exactly after restart with season 2025/REGULAR/week 7/nflverse/artifact/team coordinates.
  - Duplicate natural line and duplicate (stat_line_id, statistic_id) value both return SQLSTATE 23505; the duplicate-value transaction leaves zero week-8 stat lines while retaining the original single week-7 line.
  - Flyway reaches V005 with NUMERIC numeric_value and foreign keys to football_player, team, etl_artifact, statistic; schema architecture assertion finds zero game/drive/play/statistical_event tables.
follow_ups:
  - none
```
