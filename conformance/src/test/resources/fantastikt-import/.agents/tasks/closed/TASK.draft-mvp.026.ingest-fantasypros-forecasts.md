---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.026
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.009
  - TASK.draft-mvp.010
  - TASK.draft-mvp.015
  - TASK.draft-mvp.021
  - TASK.draft-mvp.025
---

# TASK.draft-mvp.026: Ingest immutable FantasyPros forecasts

## Description

Implement official FantasyPros players, consensus rankings, projections, and injuries jobs as immutable request-parameter-scoped snapshots.

## Requirements

- Own the forecast/ranking/source-injury migration and PostgreSQL adapters plus `etl-fantasypros` client/DTO/job packages and sanitized endpoint fixtures; Task 027 reuses but never edits this immutable snapshot schema.
- Follow current official API documentation for exact parameters; authenticate only through the redacted `x-api-key` environment configuration.
- Preserve scoring mode, projection period, capture time, request fingerprint, ECR, ADP, positional rank, tier, dispersion, projected stats/points, and injury observations when supplied.
- Every successful endpoint request appends its own timestamped forecast/ranking belief snapshot and request fingerprint. Identical response bytes may reference the Task 009 content-addressed artifact instead of duplicating storage, but must not collapse distinct capture events or overwrite an earlier belief.

## Deliverables

- [x] Each endpoint response is retained before decoding and produces exact typed snapshot/value counts.
- [x] Identical-byte, changed-parameter, and changed-response requests prove distinct capture snapshots, content-hash reuse where applicable, and no overwrite of prior beliefs.
- [x] Unresolved non-draftable forecast rows are visible without failing resolvable assets or mislabeling ranks as projections.

## Closure

```yaml
closed_at: 2026-09-03T05:50:07.034200100Z
closed_by: codex
summary: Implemented official FantasyPros players, rankings, projections, and injury ingestion as immutable request-scoped belief snapshots with durable PostgreSQL round trips and visible unresolved rows.
verification:
  - .\\gradlew.bat verify --rerun-tasks => BUILD SUCCESSFUL; 174 tests, 0 failures, 0 errors, 0 skipped; 70 product files conform.
  - FantasyProsForecastJobTest => exact endpoint counts; raw artifact present before malformed decode; x-api-key redacted/environment-only; identical bytes reused artifact IDs while changed parameters minted distinct fingerprints and snapshots; changed bytes minted a new artifact without overwriting prior values.
  - ForecastPersistenceIntegrationTest => V010 migrated on fresh PostgreSQL; forecast, ranking, projection-stat, full injury, and unresolved records survived restart; one artifact backed distinct capture snapshots; duplicate snapshot overwrite rejected with SQLSTATE 23505.
follow_ups:
  - none
```
