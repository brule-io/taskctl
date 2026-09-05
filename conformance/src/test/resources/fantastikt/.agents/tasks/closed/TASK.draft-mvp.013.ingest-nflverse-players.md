---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.013
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.009
  - TASK.draft-mvp.010
  - TASK.draft-mvp.011
---

# TASK.draft-mvp.013: Ingest the nflverse player corpus

## Description

Implement typed `players.csv` acquisition, contract validation, identity seeding, external-ID attachment, and conservative Person updates.

## Requirements

- Own `etl-nflverse` player DTO/parser/job packages and sanitized player fixtures.
- Require the exact P0 manifest `gsis_id`, `display_name`, `first_name`, `last_name`, `football_name`, `suffix`, `birth_date`, `espn_id`, `pfr_id`, `pff_id`, `otc_id`, `esb_id`, `position`, `position_group`, `jersey_number`, `latest_team`, and `last_season`; tolerate additive columns, fail incompatible removal/type drift, and stream rather than load the whole corpus.
- A present but blank GSIS value is not by itself a rejected/dropped player: reconcile another exact namespaced ID when available, otherwise retain a locally minted unresolved Person plus source-row evidence for Task 015 review, with idempotency anchored to the retained artifact/source row rather than a provider-derived UUID.
- Store source team, jersey, position, status, and last-season values only as observations or raw evidence, never on FootballPlayer.

## Deliverables

- [x] Representative, additive-column, each-missing-required-column, incompatible-type, quoted-value, and malformed-row fixtures have exact outcomes.
- [x] GSIS-anchored reruns preserve Person UUIDs and attach every nonblank namespaced external ID; a blank-GSIS fixture remains visible and resolves to the same local unresolved UUID on same-artifact replay.
- [x] `NflversePlayersJob` returns read/accepted/rejected counts and retained artifact SHA in the typed result contract that Task 029 later exposes unchanged through `ball etl sync players`.

## Closure

```yaml
closed_at: 2026-09-03T03:31:19.128673700Z
closed_by: codex
summary: Implemented streaming source-specific nflverse players.csv acquisition and RFC-4180 parsing, exact 17-column contract validation, typed drift failures, conservative exact-first identity seeding, complete provider-ID attachment, and durable artifact-row anchoring for blank-GSIS unresolved players.
verification:
  - .\gradlew.bat :etl-nflverse:test --tests io.brule.fantastik.etl.nflverse.player.NflversePlayersJobTest => BUILD SUCCESSFUL; 7 tests, 0 failures, 0 errors, 0 skipped.
  - The additive representative fixture streamed 2 accepted rows including a quoted comma; a 17-case loop removed each required manifest column and each failed naming that column; birth_date, jersey_number, and last_season incompatible values failed visibly.
  - A three-row malformed fixture returned read=3 accepted=1 rejected=2 with ordered MALFORMED_CSV_ROW and CSV_COLUMN_COUNT_MISMATCH outcomes while preserving the following valid record.
  - Two same-artifact executions retained 2 Person rows and 7 exact identifiers; the GSIS player kept one PersonId and attached nfl:gsis-player, espn:football-player, pfr:player, pff:player, otc:player, and esb:player identifiers.
  - Blank-GSIS same-artifact replay retained one UUIDv4 Person independent of provider/digest UUID derivation, one unresolved review record, and exact nflverse:source-row value <artifact-sha>:row-1; mutable team/jersey/position/last-season fields remained only in the retained raw artifact/typed source row.
  - NflversePlayersJob acquisition captured source=nflverse, file=players.csv, config=nflverse-players-test-v1 and returned runId plus exact read=2 accepted=2 rejected=0 and retained ArtifactSha256.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL; 59 tasks executed, 11 admitted module boundaries, 37 product files, all PostgreSQL and nflverse tests, and healthy task/ADR ledgers.
follow_ups:
  - none
```
