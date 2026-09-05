---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.020
roadmap: ROADMAP.draft-mvp.001
effort: MEDIUM
impact: HIGH
depends:
  - TASK.draft-mvp.009
  - TASK.draft-mvp.010
  - TASK.draft-mvp.015
---

# TASK.draft-mvp.020: Ingest the ffverse identity crosswalk

## Description

Load the DynastyProcess player-ID crosswalk as supplemental exact identity evidence without replacing local UUIDs or auto-merging heuristic candidates.

## Requirements

- Own `etl-ffverse` crosswalk DTO/parser/job packages and crosswalk fixtures.
- Attach ESPN, GSIS, FantasyPros, Sleeper, Yahoo, MFL, and other IDs using fully namespaced entity-specific authorities; classify blank, `NA`, and provider-specific zero placeholders as missing rather than attaching shared fake identifiers.
- Normalize observed ffverse team codes such as `FA*`, `GBP`, `JAC`, `KCC`, `LVR`, `NEP`, `NOS`, `RAM`, `SDC`, `SFO`, and `TBB` only as reconciliation context through an explicit source manifest.
- Conflicting assertions create visible reconciliation evidence; they never overwrite an existing source assertion.

## Deliverables

- [x] The ffverse crosswalk job retains `db_playerids.csv` and returns exact attachment/conflict/candidate counts for Task 029's later `ball etl sync ffverse` composition.
- [x] Tests prove ESPN-to-GSIS repair, sentinel-ID rejection, source-team alias handling, same-numeric-value namespace isolation, name-only non-merge, and idempotent rerun.
- [x] Crosswalk absence leaves primary nflverse identities intact and visible degraded behavior.

## Closure

```yaml
closed_at: 2026-09-03T03:57:12.444993100Z
closed_by: codex
summary: Implemented the typed DynastyProcess player-ID crosswalk parser/job, explicit 20-provider identity and observed-team manifests, exact attachment and conflict evidence, collision-safe candidate generation, idempotent replay, and sanitized unavailable-source degradation.
verification:
  - .\gradlew.bat :etl-ffverse:test --tests *FfverseCrosswalkJobTest => BUILD SUCCESSFUL; 6 tests, 0 failures, 0 errors, 0 skipped.
  - Sanitized db_playerids.csv fixture SHA-256 811d8afa17fc2704ec688165fbeb99363575625001b0aca9522e82abbcbb159b parsed 3/3 rows and attached exactly 16 new identifiers to the GSIS-anchored Person; ESPN and MFL value 4430807 remained separate namespaced keys resolving the same stable UUID, while stats_global_id=0 and NA cells created no identifiers.
  - Identical artifact replay attached 0 identifiers and preserved the exact identifier cardinality; the name-only Josh Allen row returned candidateRows=1 candidateCount=2 with mergeCalls=0.
  - The explicit source-team manifest normalized FA/FA*, GBP, JAC, KCC, LVR, NEP, NOS, OAK, RAM, SDC, SFO, STL, and TBB only in candidate evidence; a two-Person GSIS/ESPN conflict produced conflictRows=1 and one two-assertion SourceDisagreement without changing either stored identifier.
  - An ArtifactFetchException returned availability=UNAVAILABLE, rowsRead=0, no artifact digest, a sanitized class-only reason, and byte-for-byte unchanged primary nflverse identity mappings.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL in 48s; 63 actionable tasks, 63 executed; 11 admitted product modules, 42 product implementation/resource files, 7 forbidden architecture fixtures rejected, every test and PostgreSQL integration suite passed, task/ADR doctors healthy.
follow_ups:
  - none
```
