---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.034
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.028
  - TASK.draft-mvp.031
  - TASK.draft-mvp.033
---

# TASK.draft-mvp.034: Model next-pick survival and opponent need

## Description

Estimate each asset's probability of surviving to our next pick using ECR/ADP dispersion, draft order, and opponent roster needs.

## Requirements

- Own `strategy` survival/opponent packages and deterministic statistical fixtures.
- Derive our next-pick coordinate for snake and linear drafts from explicit draft configuration; auction bidding/nomination survival is not specified and must fail readiness rather than be invented.
- Treat missing ADP/dispersion as a visible modeled fallback and avoid inventing confidence from unavailable data.

## Deliverables

- [ ] Golden fixtures prove next-pick coordinates, position-need updates, monotonic survival behavior, and seeded reproducibility.
- [ ] Each available asset receives an inspectable survival probability or explicit unavailable reason.
- [ ] A recorded opponent pick recomputes roster need and changes only causally affected inputs.

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
