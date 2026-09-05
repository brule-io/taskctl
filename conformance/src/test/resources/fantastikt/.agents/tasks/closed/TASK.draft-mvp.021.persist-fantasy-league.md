---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.021
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.005
  - TASK.draft-mvp.006
  - TASK.draft-mvp.007
  - TASK.draft-mvp.011
  - TASK.draft-mvp.012
  - TASK.draft-mvp.018
---

# TASK.draft-mvp.021: Persist fantasy assets, leagues, and economics

## Description

Add fantasy asset, eligibility, league, roster-slot, scoring-rule, contest, payout, trade-policy, and roster-assignment persistence.

## Requirements

- Own the fantasy/league/economics migration and PostgreSQL adapter packages.
- Enforce PlayerFantasyAsset-to-one-FootballPlayer and TeamDefenseFantasyAsset-to-one-Team without a D/ST Person.
- Scope ESPN fantasy eligibility to league and explicit snapshot; store money as integral cents and preserve source versus manual assertions separately.
- Represent season-place and weekly/high-score payout rules with explicit kind/period coordinates so prize-pool validation and strategy cannot silently collapse or omit a configured prize class.

## Deliverables

- [x] Schema round trips every Section 7.5 and 7.7 relation with subtype and uniqueness constraints.
- [x] Tests prove D/ST cannot reference Person, player assets cannot reference Team, and eligibility requires a selected snapshot.
- [x] Season-place plus weekly/high-score Contest payouts and side-payment policy retain distinct provenance, exact cent arithmetic, and a conserved total purse.

## Closure

```yaml
closed_at: 2026-09-03T04:50:33.919064Z
closed_by: Codex
summary: Implemented typed PostgreSQL persistence for fantasy assets, ESPN league assertions, eligibility, roster configuration, draft order, roster assignments, contest payouts, and trade policies with subtype integrity, snapshot provenance, and exact conserved-purse validation.
verification:
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL in 59s; 63/63 actions executed; 152 tests, 0 failures/errors/skips; 55 product files and 11 modules verified.
  - LeaguePersistenceIntegrationTest round-trips all Section 7.5/7.7 persisted relations across ESPN and manual snapshots, including player and team-defense assets, eligibility, teams, roster slots, scoring, assignments, draft order, two payout kinds, economics, and trade policies after adapter restart.
  - PostgreSQL negative assertions return SQLSTATE 23503 for cross-subtype player/team-defense inserts and missing-snapshot eligibility; composite league/team keys and unique asset backing constraints prevent ownership and subtype collapse.
  - Integral BIGINT entry_fee_cents and amount_cents are schema-asserted; exact Long addition/multiplication conserves a 20,000-cent two-team purse and rejects a 19,999-cent payout schedule.
follow_ups:
  - none
```
