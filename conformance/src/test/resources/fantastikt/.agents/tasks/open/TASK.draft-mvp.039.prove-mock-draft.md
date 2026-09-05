---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.039
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.030
  - TASK.draft-mvp.036
  - TASK.draft-mvp.037
  - TASK.draft-mvp.038
---

# TASK.draft-mvp.039: Prove the full mock-draft acceptance gate

## Description

Exercise the complete P0 system from an empty durable database through source sync, league verification, a full draft, restart, replay, reconciliation, backup/restore, and offline continuation.

## Requirements

- Own end-to-end acceptance fixtures/scripts and the sanitized `docs/operations/2026-draft/mock-acceptance.md` evidence report; keep raw/private execution artifacts under `.data/` and fix defects only through their owning task boundaries or explicit follow-ups.
- Evaluate every Section 18 data, architecture, draft-operation, and strategy-input criterion with an exact command, count, timing, digest, or observed postcondition.
- Require at least one current projection snapshot and a real speculative result for the full P0 pass; an operator-accepted rank-only emergency mode remains usable evidence but cannot be reported as satisfying Section 18 or the under-two-second speculative gate.
- Exercise one approved duplicate-Person merge after every P0 foreign-key-bearing feature exists and prove transactional repointing, retired-ID redirect resolution, and replay equivalence.
- ESPN polling remains optional; its failed gate cannot fail manual P0 operation, but manual reconciliation must still pass.

## Deliverables

- [ ] All P0 acceptance criteria and twenty doctor checks have linked exact evidence with no draft-affecting unresolved warning.
- [ ] A complete realistic mock draft survives process/database restart, deterministic replay, ESPN reconciliation, backup/restore, and disabled network.
- [ ] Architecture and latency reports prove strict Kotlin boundaries, no Neo4j runtime dependency, <250 ms static refresh, and <2 s initial speculative ranking from a real current projection snapshot.
- [ ] A full-schema identity merge leaves no reference to the retired UUID outside the audit/redirect ledger and does not change reconstructed board state.

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
