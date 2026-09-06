---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.014
roadmap: ROADMAP.draft-mvp.001
effort: MEDIUM
impact: HIGH
depends:
  - TASK.draft-mvp.009
  - TASK.draft-mvp.010
  - TASK.draft-mvp.012
  - TASK.draft-mvp.013
---

# TASK.draft-mvp.014: Ingest and normalize NFL teams

## Description

Implement the typed nflverse teams pipeline and normalize historical source abbreviations to the stable 32-team corpus.

## Requirements

- Own `etl-nflverse` team DTO/parser/job packages and sanitized team fixtures, using Task 012's Team persistence port as the only creator of source-authoritative canonical Team rows.
- Require the six specified P0 team columns, tolerate optional visual metadata, and preserve source identifiers as namespaced evidence.
- Collapse the currently observed 36 source rows to 32 stable Team identities by `team_id`, retaining `LA/LAR/STL`, `LV/OAK`, and `LAC/SD` as aliases; keep canonical current abbreviations unique and classify null/free-agent codes without inventing Team rows.

## Deliverables

- [x] `NflverseTeamsJob` maps the 36-row contract fixture to exactly 32 stable NFL teams, preserves all historical abbreviation aliases, and returns the typed counts Task 029 later exposes through `ball etl sync teams`.
- [x] Alias, additive-column, duplicate-abbreviation, and free-agent tests have visible deterministic outcomes.
- [x] Rerunning the identical artifact does not duplicate teams or external identifiers.

## Closure

```yaml
closed_at: 2026-09-03T03:37:02.268008700Z
closed_by: codex
summary: Implemented typed nflverse teams acquisition/parsing and exact team_id grouping, frozen historical abbreviation normalization, artifact-backed namespaced team identity, stable local Team UUID reuse, visible no-team classification, and typed sync counts.
verification:
  - .\gradlew.bat :etl-nflverse:test --tests io.brule.fantastik.etl.nflverse.team.NflverseTeamsJobTest => BUILD SUCCESSFUL; 5 tests, 0 failures, 0 errors, 0 skipped.
  - Sanitized additive 36-row fixture returned read=36 accepted=36 rejected=0 noTeam=0 canonicalTeams=32 and retained the artifact SHA; persisted aliases were LAR=[LA,STL], LV=[OAK], and LAC=[SD].
  - Every canonical group retained one nflverse:team source identifier; an identical artifact replay preserved the exact 32 TeamId set, 32 teams, 32 identifiers, and 36 canonical-plus-alias abbreviations.
  - Removing each of six required columns failed naming that column; duplicate ARI across team ids 1 and 2 failed naming ARI; an FA row returned read=1 accepted=0 rejected=0 noTeam=1 canonical=0 and created no Team.
  - PostgreSQL Team persistence resolved an artifact-backed nflverse:team identifier to the same canonical Team and continued to reject fake free-agent creation.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL; 59 tasks (58 executed, 1 up-to-date), 11 admitted module boundaries, 38 product files, all PostgreSQL/nflverse tests, and healthy task/ADR ledgers.
follow_ups:
  - none
```
