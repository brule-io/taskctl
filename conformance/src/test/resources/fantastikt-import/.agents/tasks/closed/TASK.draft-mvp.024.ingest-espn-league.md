---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.024
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.021
  - TASK.draft-mvp.022
  - TASK.draft-mvp.023
---

# TASK.draft-mvp.024: Ingest the ESPN league overlay

## Description

Normalize ESPN settings, fantasy teams, rosters, lineup slots, scoring rules, and draft metadata into snapshot-scoped platform assertions.

## Requirements

- Own `etl-espn` league/team/roster jobs and their adapter tests.
- Preserve original artifacts and unmapped platform codes; never infer missing economics or silently let manual overrides replace conflicting ESPN values.
- Persist draft format/order only when supplied and require operator verification before strategy use.

## Deliverables

- [x] `mSettings`, `mTeam`, and `mRoster` fixtures produce exact league/team/slot/scoring/assignment counts.
- [x] Unknown slot/stat codes fail or warn according to an explicit manifest with no silent drop.
- [x] Repeated fetches append source snapshots without duplicating canonical league or team entities.

## Closure

```yaml
closed_at: 2026-09-03T05:20:54.164914400Z
closed_by: Codex
summary: Implemented snapshot-scoped ESPN league overlay ingestion with explicit slot/stat manifests, retained-code warnings, roster resolution, unverified draft metadata, and canonical team reuse across fetches.
verification:
  - clean .\\gradlew.bat clean verify: BUILD SUCCESSFUL; 67 actionable tasks executed; 62 product files; 39 XML reports, 166 tests, 0 failures/errors/skips
  - EspnLeagueJobTest: fixture ingestion reports league=1 teams=2 slots=2 scoring=1 assignments=1 draftMetadata=1 draftOrder=2 per snapshot; two fetches retain 6 raw artifacts, append 2 snapshots, and keep 2 canonical teams
  - LeaguePersistenceIntegrationTest and MigrationIntegrationTest: V008 draft metadata persists operator_verified=false, rejects ingestion self-verification, and identical appendTeam replay remains idempotent across PostgreSQL restart
follow_ups:
  - none
```
