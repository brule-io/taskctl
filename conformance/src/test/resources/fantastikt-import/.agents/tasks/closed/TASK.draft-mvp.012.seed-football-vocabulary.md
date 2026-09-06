---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.012
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.004
  - TASK.draft-mvp.006
  - TASK.draft-mvp.007
  - TASK.draft-mvp.011
---

# TASK.draft-mvp.012: Create football vocabulary and jersey rules

## Description

Create Team and football/statistic vocabulary persistence, seed only the static vocabularies, and add a versioned 2026 NFL jersey-position relation independent from observed and fantasy positions. Canonical NFL Team rows remain sourced by Task 014.

## Requirements

- Own the Team/vocabulary migration, PostgreSQL port adapters, typed static seed definitions, and migration verification tests.
- Provide empty Team plus seeded Position, Jersey, JerseyPosition, and Statistic relations with exact keys and decimal value-kind metadata; do not pre-create the 32 source-authoritative Team rows.
- Derive the static relation exactly from 2026 Rule 5: QB `0–19`; P/K `0–49,90–99`; DB `0–49`; RB/FB/TE/H-back/WR `0–49,80–89`; OL `50–79`; DL `50–79,90–99`; LB `0–59,90–99`; never reuse it as ESPN fantasy eligibility.
- Persist other observed source-position codes as vocabulary when required, but do not invent jersey eligibility for roles the rulebook table does not list.

## Deliverables

- [x] All legal jerseys and admitted positions/statistics load deterministically from an empty database while Team remains empty for Task 014's retained-source ingestion.
- [x] A checked rulebook fixture proves every expected `(jersey, position)` pair and no observed-roster inference.
- [x] Team persistence accepts namespaced source identifiers/aliases and nullable no-team classification without creating a fake free-agent Team.

## Closure

```yaml
closed_at: 2026-09-03T03:25:09.275558500Z
closed_by: codex
summary: Added immutable Flyway V003 football vocabulary and team schema, typed deterministic 2026 Rule 5/static-statistic seeds, guarded vocabulary writes, and team persistence with source evidence, aliases, and explicit no-team classification.
verification:
  - .\gradlew.bat :durability-postgres:postgresIntegrationTest --tests io.brule.fantastik.durability.FootballVocabularyIntegrationTest --tests io.brule.fantastik.durability.MigrationIntegrationTest => BUILD SUCCESSFUL; 7 tests, 0 failures, 0 errors, 0 skipped.
  - Fresh V003 migration produced exactly 12 explicitly keyed Rule 5 positions, jerseys 0 through 99, 21 explicitly keyed P0 statistics with COUNT/YARDS value-kind metadata, and 0 Team rows; a second migration executed 0 changes.
  - Typed 2026 Rule 5 fixture and database relation matched as exact 630-element sets; observed LS persisted as vocabulary with 0 eligibility rows, and the adapter rejected an attempted LS/0 eligibility outside the frozen rulebook set.
  - Team adapter idempotently retained MIN plus alias MNN and one nflverse:team/MIN artifact-backed external identifier; null and FA classified NO_TEAM, ZZZ classified UNKNOWN, direct FA creation was rejected, and no fake FA row existed.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL; 55 tasks executed, 11 admitted module boundaries, 35 product files, all PostgreSQL integration tests, and healthy task/ADR ledgers.
follow_ups:
  - none
```
