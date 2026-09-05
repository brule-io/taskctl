---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.017
roadmap: ROADMAP.draft-mvp.001
effort: MEDIUM
impact: HIGH
depends:
  - TASK.draft-mvp.016
---

# TASK.draft-mvp.017: Ingest nflverse weekly rosters

## Description

Extend roster ingestion to weekly 2023–2026 coordinates now that the 2026 upstream artifact is available.

## Requirements

- Own `etl-nflverse` weekly-roster packages and weekly contract fixtures; reuse the Task 016 observation schema.
- Preserve season, game type, and week exactly; source fetch timestamps remain provenance only.
- Do not materialize inferred wall-clock validity ranges or overwrite season observations.
- Treat a later missing configured artifact as explicit source absence without deleting any previously retained 2026 observations.

## Deliverables

- [x] `NflverseRosterJob` weekly mode loads independently rerunnable 2023–2026 snapshots and returns the per-season result Task 029 later exposes through `ball etl sync weekly-rosters`.
- [x] Tests prove `(team, jersey, season, week)` lookup, native week fidelity, free-agent handling, and source-position rejection reporting.
- [x] A simulated missing configured-season artifact is classified explicitly without destructive expiration of prior data.

## Closure

```yaml
closed_at: 2026-09-03T04:22:16.307826200Z
closed_by: Codex
summary: Extended nflverse roster ingestion with independently rerunnable weekly 2023-2026 mode, coordinate-qualified native keys, durable team/jersey/week lookup, and non-destructive missing-source classification.
verification:
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL in 51s; 62/63 actions executed (root clean up-to-date); 146 tests, 0 failures/errors/skips; 47 product files and 11 modules verified.
  - NflverseRosterJobTest: 8 tests pass; weekly fixture retains REG weeks 1/7 and WC week 19 provenance, resolves MIN jersey 18, keeps FA team/jersey null, reports LS as UNKNOWN_POSITION, and same-run replay remains 4 observations.
  - RosterObservationPersistenceIntegrationTest: PostgreSQL restart-backed lookup returns exactly 1 WEEKLY row for KC/15/2026/REG/1 while excluding the coordinate-identical SEASON observation.
  - Simulated weekly HTTP 404 returns SOURCE_ABSENT with SOURCE_ARTIFACT_ABSENT and leaves 4 retained observations unchanged; HTTP 503 remains a failure. weekly-2025.csv SHA-256 2BE862520B12197DA9003C33C7D42B7BC24D353260F06CE26F7AFB4F16948334.
  - Live primary nflverse release probes: 2026 weekly CSV present with 2,902 week-1 REG rows and 36 columns; 2025 has 46,849 rows across REG/WC/DIV/CON/SB with no missing native identifiers.
follow_ups:
  - none
```
