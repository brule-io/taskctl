---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.035
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.021
  - TASK.draft-mvp.022
  - TASK.draft-mvp.034
---

# TASK.draft-mvp.035: Simulate payout-aware speculative strategy

## Description

Add seeded roster-completion rollouts that combine projection uncertainty, survival, roster feasibility, championship utility, and the actual contest payout function.

## Requirements

- Own `strategy` speculative/Monte-Carlo packages, benchmark fixtures, and the immutable recommendation-snapshot migration/port/PostgreSQL adapter.
- Compute monetary expected values with decimal/integral-minor-unit semantics across every verified season-place and weekly/high-score payout rule, and honor the verified side-payment policy without enabling negotiation; an unrepresentable configured prize rule blocks readiness rather than being omitted.
- Return a reproducible initial answer under two seconds and allow refinements without reordering results from mixed snapshot inputs.
- When no current projection snapshot exists, return a typed speculative-unavailable reason and preserve static rank-only operation; never synthesize projection uncertainty, championship probability, or marginal payout from ranks alone.
- Define initial-result latency from receipt of a complete DraftBoardInput to the first ranked speculative result, use a monotonic clock, and record host CPU/memory, OS, JDK/JVM options, pool size, warm-up, seed, sample count, distribution, and maximum.
- Persist draft event sequence, complete source snapshot IDs, input fingerprint, algorithm version, seed, sample/error metadata, ranked outputs, timing, and refinement lineage for every board recommendation.

## Deliverables

- [ ] Seeded fixtures prove roster legality, season/weekly payout conservation, survival conditioning, unsupported-rule blocking, and stable marginal-EV ordering.
- [ ] Every recorded post-warm-up run of a realistic local benchmark returns the first speculative ranking in under two seconds and records reproducible environment/sample/error metadata.
- [ ] Every computed speculative result exposes championship probability, marginal expected payout, model seed, and all source snapshot IDs; a speculative-unavailable outcome exposes its typed reason instead of fake values.
- [ ] Restart/postmortem tests load the exact initial and refined recommendation sequence without recomputation or mixed belief states.

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
