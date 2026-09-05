---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.027
roadmap: ROADMAP.draft-mvp.001
effort: MEDIUM
impact: HIGH
depends:
  - TASK.draft-mvp.009
  - TASK.draft-mvp.010
  - TASK.draft-mvp.020
  - TASK.draft-mvp.021
  - TASK.draft-mvp.025
---

# TASK.draft-mvp.027: Ingest ffverse fallback rankings

## Description

Load the latest applicable draft-ranking rows from `db_fpecr.csv.gz` into an independently rerunnable fallback ranking snapshot.

## Requirements

- Own `etl-ffverse` ECR DTO/parser/job packages and compressed ranking fixtures.
- Preserve source scrape date, scoring/position context, ECR, dispersion, team, and ownership fields when available.
- Never overwrite or impersonate official FantasyPros snapshots, never relabel ECR as points, never treat a prior-season row as current, and warn visibly when an applicable fallback age exceeds 48 hours.
- The live artifact observed at admission ended at `2025-08-08`; if it still has no applicable 2026 draft rows, report fallback unavailable and preserve ESPN rank observations as the only no-key degraded input.

## Deliverables

- [x] GZIP fixture loading creates a source-labeled immutable ranking snapshot with exact counts.
- [x] Freshness-boundary, prior-season exclusion, no-applicable-row, missing-key, unresolved-ID, and official-snapshot coexistence tests pass.
- [x] The fallback job returns availability and age alongside the crosswalk result so Task 029 can report both through one `ball etl sync ffverse` invocation.

## Closure

```yaml
closed_at: 2026-09-03T06:02:39.601141Z
closed_by: codex
summary: Implemented independently rerunnable ffverse GZIP draft-ranking ingestion with exact season/scoring/position selection, immutable source-labeled snapshots, freshness and availability reporting, and combined crosswalk/fallback output.
verification:
  - .\\gradlew.bat verify --rerun-tasks => BUILD SUCCESSFUL; 180 tests, 0 failures, 0 errors, 0 skipped; 73 product files conform.
  - FfverseRankingJobTest => 7-row GZIP fixture selected exactly 3 newest applicable rows (1 resolved, 2 visible unresolved); exact 48-hour boundary stayed fresh and +1 second warned; prior-season and mismatched-scoring cases wrote no snapshot; missing ID remained visible; official fantasypros and ffverse snapshots coexisted; ECR remained rank-only.
  - Live source scan of https://raw.githubusercontent.com/DynastyProcess/data/master/files/db_fpecr.csv.gz on 2026-09-03 => 1,528,918 rows, maximum scrape_date 2025-08-08, zero 2026 rows; production 2026 selection therefore returns fallback UNAVAILABLE while preserving ESPN degraded input.
  - FfverseSyncJob contract test => one invocation returns the crosswalk result together with fallback availability and source age for Task 029 wiring.
follow_ups:
  - none
```
