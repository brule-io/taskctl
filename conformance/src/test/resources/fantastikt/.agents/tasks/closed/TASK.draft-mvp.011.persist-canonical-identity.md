---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.011
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.004
  - TASK.draft-mvp.006
  - TASK.draft-mvp.007
---

# TASK.draft-mvp.011: Persist canonical person identity

## Description

Add canonical metadata, Person, FootballPlayer, namespaced external-identifier, alias, and merge-ledger persistence with locally minted UUIDs.

## Requirements

- Own the identity migration, identity adapter packages, and identity integration tests.
- Keep `football_player` to its Person primary/foreign key and forbid team, jersey, position, status, eligibility, forecast, injury, or statistic columns.
- Enforce unique `(authority, value)`, conservative profile updates, transactional merges, resolvable retired UUIDs, and no UUID derivation from providers.

## Deliverables

- [x] Exact external-ID lookup and stable local UUID minting survive idempotent replay and restart.
- [x] Schema tests prove the FootballPlayer column invariant and duplicate external-ID rejection.
- [x] Merge tests repoint all fixture references atomically while retaining an auditable redirect and evidence list.

## Closure

```yaml
closed_at: 2026-09-03T03:17:29.353220900Z
closed_by: codex
summary: Added canonical metadata/person/football-player identity persistence, exact namespaced external identifiers with artifact evidence, UUIDv4 minting, conservative profile enrichment, retired-person redirects, and transactional auditable merge history in immutable Flyway V002.
verification:
  - .\gradlew.bat :durability-postgres:postgresIntegrationTest --tests io.brule.fantastik.durability.IdentityPersistenceIntegrationTest => BUILD SUCCESSFUL; 4 tests, 0 failures, 0 errors, 0 skipped.
  - Replay supplied different newly minted UUIDv4 values for the same nfl:gsis-player identifier and returned the original PersonId; a closed/reopened adapter resolved that same ID, while a canonical enrichment preserved conflicting existing display/first names and filled only absent last name/birth date.
  - information_schema returned football_player columns exactly [person_id]; PostgreSQL rejected a duplicate (espn:football-player,12345) external identifier with SQLSTATE 23505.
  - Two chained merges moved three exact identifiers and prior aliases to one survivor, left one football_player row, retained two entity_merge rows and three ordered artifact-evidence rows, and resolved both retired UUIDs to the survivor.
  - A duplicate merge-ledger key failed late with SQLSTATE 23505 after reference-update code ran; transaction rollback retained both football_player rows, zero aliases, zero RETIRED people, and the retired identifier's original PersonId.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL; 55 tasks executed, 11 admitted module boundaries, 32 product files, 10 PostgreSQL integration tests, and healthy task/ADR ledgers.
follow_ups:
  - none
```
