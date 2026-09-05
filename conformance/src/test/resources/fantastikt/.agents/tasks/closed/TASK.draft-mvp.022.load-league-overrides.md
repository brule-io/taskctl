---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.022
roadmap: ROADMAP.draft-mvp.001
effort: MEDIUM
impact: HIGH
depends:
  - TASK.draft-mvp.005
  - TASK.draft-mvp.007
  - TASK.draft-mvp.008
  - TASK.draft-mvp.021
---

# TASK.draft-mvp.022: Load provenance-tracked league overrides

## Description

Implement typed `config/league-overrides.json` validation and persistence for entry fee, payouts, side-payment policy, draft order, and our team.

## Requirements

- Own the override DTO/service, safe example configuration, CLI integration, and tests.
- Manual values may fill absent ESPN settings but never silently replace conflicting platform assertions; both remain displayable.
- Reject invalid cents, duplicate payout coordinates, inconsistent total/season/weekly prize pools, unknown team IDs, and incomplete draft order.

## Deliverables

- [x] Example and fixture overrides round trip with artifact/configuration fingerprints and no secrets.
- [x] Conflict tests retain ESPN and manual assertions and identify the active operator choice.
- [x] League economics validation reports exact entry-fee, total prize-pool, season-place sum, weekly/high-score sum, and conserved payout values.

## Closure

```yaml
closed_at: 2026-09-03T05:04:33.935864500Z
closed_by: Codex
summary: Implemented strict provenance-tracked league override loading, validation, persistence, active-choice selection, and terminal display while preserving separate ESPN and manual assertions.
verification:
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL in 1m; 67 actions (66 executed, 1 up-to-date); 157 tests, 0 failures/errors/skips; 58 product files and 11 modules verified.
  - Example and fixture load identically with artifact SHA-256 24061d8c7fe0b4e5ddf539df0f33c748eb8e5296ed3d87731943034ef45adbde and semantic configuration fingerprint 0e2b1251047c49c2e121c8e67874d83b7b74fbd0913bcfc3707da49b24b3c2b0; whitespace changes only the artifact fingerprint and neither file contains password/token/secret fields.
  - LeagueOverridesTest retains conflicting ESPN/manual entry fee, payout, side-payment, and draft-order assertions, reports MANUAL as the explicit active choice, and idempotently replays one fingerprinted override.
  - Validation rejects negative cents, duplicate payout coordinates, inconsistent total/season/weekly pools, unknown teams, and incomplete draft order; accepted fixture reports entry=7500, total=15000, season=10000, weekly=5000, payouts=15000, conserved purse=15000.
  - PostgreSQL V007 and restart assertions retain both override fingerprints and our-team selection; activating a replacement marks the prior assertion inactive without deleting it, while league show renders complete ESPN/manual economics and conflict fields.
follow_ups:
  - none
```
