---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.036
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.030
  - TASK.draft-mvp.032
  - TASK.draft-mvp.035
---

# TASK.draft-mvp.036: Run the offline live draft watch loop

## Description

Compose manual events, persisted projections, static ranking, and speculative refinement into the terminal `draft watch` experience.

## Requirements

- Own `ball draft watch` application/terminal packages and end-to-end offline/performance tests.
- After every pick, immediately show removed availability, updated roster/scarcity, round/pick/next-pick state, initial speculative rankings, last pick, and rerank timing.
- If Task 035 reports speculative-unavailable, continue automatic static rank-only reranking with a prominent reason and omit rather than fabricate survival/championship/marginal-EV values.
- Append the initial recommendation and every asynchronous refinement through Task 035's immutable persistence port before presenting it as postmortem-replayable output.
- Gate presentation by draft event sequence and complete input fingerprint: a refinement from an older pick remains persisted for analysis but is never allowed to replace or reorder the current board.
- `--offline-ready` must prove no required network dependency, writable persistence, current snapshots, configured economics, replay integrity, and successful backup.

## Deliverables

- [ ] A scripted multi-round session reranks automatically after every manual event and remains operable with network access disabled.
- [ ] A rapid-consecutive-pick fixture proves late refinements are retained under their original lineage but never displayed as current advice.
- [ ] Terminal output meets the Section 2 operator contract when projections are available, preserves usable correction/undo commands, and has an explicit no-fake-values degraded rendering when they are not.
- [ ] Static and initial speculative timings meet their gates under the integrated watch loop.

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
