---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.038
roadmap: ROADMAP.draft-mvp.001
effort: MEDIUM
impact: HIGH
depends:
  - TASK.draft-mvp.022
  - TASK.draft-mvp.024
  - TASK.draft-mvp.025
  - TASK.draft-mvp.026
  - TASK.draft-mvp.029
---

# TASK.draft-mvp.038: Verify operator league configuration

## Description

Obtain and verify every Section 22 operator input against rendered ESPN state and persist only the authorized non-secret overrides needed by strategy and draft operation.

## Requirements

- Own the private ignored `.data/operator/` configuration receipt and the sanitized `docs/operations/2026-draft/operator-readiness.md` verification report; never commit SWID, S2, API keys, private league names, or unnecessary account identifiers.
- Verify league/team ID, privacy, exact draft start instant/timezone, ranking freshness threshold, draft format/order/clock, managers, slots/flex, scoring/bonuses, bench/IR, keepers, economics/payouts, weekly prizes, deadline/veto, and side-payment legality.
- Treat missing or conflicting normative inputs as an explicit blocker to strategy readiness rather than agent discretion.
- Require snake or linear format for the admitted next-pick strategy; an auction requires a new specification-admission task for budgets/bids/nominations. Keeper effects must already be represented in verified ESPN availability/order or likewise block readiness.
- An operator may authorize ESPN-only ranks for emergency draft usability, but that degraded authorization must remain visibly distinct from P0 readiness and cannot satisfy Task 039's current-projection acceptance gate.

## Deliverables

- [ ] The sanitized report has one pass/block row for every Section 22 operator input, citing `ball league show/scoring/slots/economics/assets --draftable`, the visible ESPN app, or an operator statement as its verification source.
- [ ] Our team and next-pick coordinates, exact draft start instant/timezone, ranking freshness threshold, prize pool, payout function, and side-payment policy are configured with provenance.
- [ ] Credential availability is verified through redacted presence checks; when FantasyPros access is unavailable and ffverse has no applicable 2026 rows, both the private receipt and sanitized report explicitly record whether ESPN-only rank observations were accepted as the visible degraded input.

## Closure

```yaml
closed_at: null
closed_by: null
summary: null
verification:
  - null
follow_ups:
  - null
```
