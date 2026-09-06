---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.016
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.012
  - TASK.draft-mvp.013
  - TASK.draft-mvp.014
  - TASK.draft-mvp.015
---

# TASK.draft-mvp.016: Ingest nflverse season rosters

## Description

Persist source-native season roster observations for 2023 through 2026 with explicit identity, team, position, jersey, status, and provenance resolution.

## Requirements

- Own the roster-observation migration and `etl-nflverse` season-roster packages/fixtures.
- Resolve player exact-first, normalize Team aliases, validate known positions and jerseys, and make null/free-agent team yield no assignment.
- Key source rows by ETL run and native source key; do not invent effective timestamps or coalesce intervals.

## Deliverables

- [x] `NflverseRosterJob` season mode retains artifact and observation counts per season in the typed result Task 029 later exposes through `ball etl sync rosters --seasons 2023..2026`.
- [x] Fixtures cover player/team resolution, null team, null jersey, unknown position rejection, and identical-artifact idempotency.
- [x] Explicit season/snapshot queries return only observations from the selected coordinate.

## Closure

```yaml
closed_at: 2026-09-03T04:12:34.390708900Z
closed_by: Codex
summary: Added typed nflverse 2023-2026 season roster ingestion, exact-first identity and team/vocabulary validation, immutable roster-observation persistence, and explicit snapshot queries.
verification:
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL in 51s; 63/63 actions executed; 144 tests, 0 failures/errors/skips; 46 product files and 11 modules verified.
  - NflverseRosterJobTest: 6 tests pass covering retained season requests/counts, exact player and historical-team resolution, null/free-agent teams, null jersey, unknown vocabulary rejection, idempotent replay, schema drift, and bounded seasons.
  - RosterObservationPersistenceIntegrationTest: 1 PostgreSQL restart-backed test passes; same run/native key replay is a no-op, conflicting replay rejects, and run+season+snapshot/player-coordinate queries isolate 4 durable rows.
  - MigrationIntegrationTest: 4 tests pass at Flyway V004; fixture season-2026.csv SHA-256 161A56C1DB8CFB05CEFB2CE64E7BE6163FF6AF42E773FD502B61202EC156E12F.
follow_ups:
  - none
```
