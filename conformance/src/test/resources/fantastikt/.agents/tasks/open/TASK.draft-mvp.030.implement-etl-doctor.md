---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.030
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.003
  - TASK.draft-mvp.007
  - TASK.draft-mvp.009
  - TASK.draft-mvp.010
  - TASK.draft-mvp.017
  - TASK.draft-mvp.019
  - TASK.draft-mvp.022
  - TASK.draft-mvp.025
  - TASK.draft-mvp.026
  - TASK.draft-mvp.027
  - TASK.draft-mvp.029
  - TASK.draft-mvp.031
  - TASK.draft-mvp.032
---

# TASK.draft-mvp.030: Implement ETL doctor and durability checks

## Description

Implement all twenty Section 17 health checks, concise terminal rendering, nonzero failure semantics, database backup, and durable artifact-directory probes.

## Requirements

- Own ETL health services, the `ball etl doctor` and `ball db backup` handlers, and their integration tests; Task 029 owns `ball db status` and may consume these health details without transferring handler ownership.
- Distinguish pass, actionable warning, degraded rank-only operation, and P0 invariant failure with exact counts/ages/paths.
- Evaluate official/ESPN ranking age against an explicit Task 038-verified freshness threshold; absence is a readiness failure rather than an inferred default. Preserve the specification's fixed 48-hour warning boundary for ffverse fallback snapshots.
- Freeze one independently forceable observation for each Section 17 check: PostgreSQL/migrations; latest parsed player artifact; exactly 32 active teams; unique external IDs; immutable-state-free FootballPlayer schema; complete ESPN asset/provisional accounting; one-player-per-player-asset; one-team-per-D/ST; known ESPN fantasy positions; classified roster team codes; valid jersey references; mapped-or-rejected source positions; projection-or-visible-rank-only state; ranking age; reported rejections; prize-pool-consistent economics; actual manual event round-trip/replay; durable writable artifact storage; successful backup; and zero secret leakage.

## Deliverables

- [ ] A versioned 1–20 test matrix independently forces and observes every required doctor check, severity, suggested inspection command, and exit code.
- [ ] Backup/restore and artifact write/read probes preserve a fixture database and retained raw artifact.
- [ ] Doctor output is concise, stable, and identifies the command needed to inspect every warning or failure.

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
