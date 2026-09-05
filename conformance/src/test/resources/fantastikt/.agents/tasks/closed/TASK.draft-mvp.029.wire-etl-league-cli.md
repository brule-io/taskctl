---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.029
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.008
  - TASK.draft-mvp.010
  - TASK.draft-mvp.022
  - TASK.draft-mvp.024
  - TASK.draft-mvp.025
  - TASK.draft-mvp.026
  - TASK.draft-mvp.027
  - TASK.draft-mvp.028
---

# TASK.draft-mvp.029: Wire ETL and league verification commands

## Description

Complete database, source-sync, run-history, and league display commands over the implemented ports, and compose the identity commands already owned by Task 015.

## Requirements

- Own concrete CLI handlers for `ball db migrate/status`, every `ball etl sync players/teams/rosters/weekly-rosters/stats/fantasypros/ffverse/espn/all` command, `ball etl runs`, and all Section 12.2 league views; preserve command names and season-range semantics.
- Compose Task 015's implemented `ball etl reconcile/unresolved` handlers without taking their ownership. Keep Task 008's `ball etl doctor` registration visibly unimplemented until Task 030 supplies that handler and service.
- `sync all` orders prerequisites safely, reports per-job artifact/snapshot IDs, counts, and outcomes, and supports useful rank-only degraded mode. A mid-pipeline failure retains completed independent runs but may promote a board snapshot selection only when Task 028 can prove it coherent; otherwise preserve the prior known-good selection or fail visibly.
- League views display ESPN and active manual assertions side by side and require explicit snapshot selection where ambiguity exists.

## Deliverables

- [x] Every owned or composed database/ETL/league command has a deterministic success, warning, and failure contract test, and `ball etl runs` exposes retained run/artifact/configuration fingerprints.
- [x] `ball league show/scoring/slots/economics/assets --draftable` exposes all operator verification inputs and no silent conflict.
- [x] From an empty database, a fixture `sync all` populates every source/league input and, with Task 028's injected fixture draft-state port, assembles a complete DraftBoardInput; real draft-state completion remains Task 031.
- [x] A forced mid-pipeline failure leaves completed jobs durable, the failed run non-complete with evidence, and no partially refreshed or mixed board selection promoted.

## Closure

```yaml
closed_at: 2026-09-03T06:29:13.658001500Z
closed_by: codex
summary: Implemented typed database, ETL sync, run-history, and explicit-snapshot league command handlers; composed identity handlers while leaving ETL doctor unimplemented; added prerequisite-ordered coherent board-selection promotion and PostgreSQL run evidence.
verification:
  - .\\gradlew.bat verify --rerun-tasks => BUILD SUCCESSFUL; 187 tests, 0 failures, 0 errors, 0 skipped; 81 product files conform.
  - OperationalCommandsTest => all nine sync commands render deterministic success/warning/failure contracts with exact season parsing, IDs/counts/outcomes; database, run-history, and five league views cover success, warning, usage, and unavailable paths with explicit snapshot inputs.
  - OrderedSourceSyncCoordinator + DraftBoardReadModelIntegrationTest => empty PostgreSQL fixture ran players/teams/rosters/weekly-rosters/stats/espn/ffverse/fantasypros order, retained rank-only ffverse warning, proved complete DraftBoardInput coherence, promoted exactly one selection, and reconstructed identical bytes after restart.
  - Forced mid-pipeline failure test => completed reports remained visible, failed report retained artifact evidence with run_completed=false, proof/promotion were not called; no-candidate path visibly warned and preserved the prior known-good selection.
  - PostgresFoundationAdapterTest => ball etl runs backing query returned source, run status, configuration fingerprint, artifact UUID and SHA-256; rolled-back artifact remained absent.
follow_ups:
  - none
```
