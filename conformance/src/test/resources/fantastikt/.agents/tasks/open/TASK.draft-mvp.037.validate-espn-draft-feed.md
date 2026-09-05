---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.037
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: MEDIUM
depends:
  - TASK.draft-mvp.023
  - TASK.draft-mvp.025
  - TASK.draft-mvp.031
  - TASK.draft-mvp.032
---

# TASK.draft-mvp.037: Gate the experimental ESPN draft feed

## Description

Implement `EspnDraftFeed` and append-only reconciliation behind a default-off feature flag, with manual events remaining authoritative until the full mock validation gate passes.

## Requirements

- Own ESPN draft-feed/reconciliation packages, `ball draft reconcile-espn` wiring/tests, and the sanitized `docs/operations/2026-draft/espn-feed-gate.md` report.
- Poll `mDraftDetail` every 1–2 seconds only when enabled; confirmations and disagreements append evidence rather than rewriting manual history.
- Primary-feed eligibility requires 20 consecutive real mock picks with non-placeholder IDs, monotonic gap-free sequence, correct teams, p95 detection under five seconds, and idempotent restart reconstruction.

## Deliverables

- [ ] Default configuration proves zero polling and unchanged manual authority.
- [ ] Golden completed-draft fixtures prove confirmation, disagreement, correction, duplicate retry, gap, and restart behavior.
- [ ] A mock-validation report records all seven gate measurements; failure leaves ESPN non-primary and correction commands enabled.

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
