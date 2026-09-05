---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.010
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.009
---

# TASK.draft-mvp.010: Orchestrate idempotent ETL runs

## Description

Implement the advisory-lock, staging, merge, rejection, completion, and idempotency protocol shared by concrete source jobs.

## Requirements

- Own `etl-core` run/orchestration packages and staging abstractions; do not create a universal dynamic row mapper.
- Lock by job/source/season, retain source absence as a new snapshot rather than destructive deletion, and merge canonical facts in one transaction. Same-artifact/configuration replay preserves stable canonical entity counts; source-specific append-only capture/snapshot rows follow their owning task's explicit semantics.
- Classify missing required fields as failure and optional source-schema drift as a visible warning with sanitized rejection evidence.

## Deliverables

- [x] A typed fixture job exercises all thirteen fetch-transform-load stages and exact run counts.
- [x] Concurrent same-key runs serialize and same-artifact/configuration reruns leave canonical counts unchanged.
- [x] Failure paths persist sanitized status/rejections and release the advisory lock.

## Closure

```yaml
closed_at: 2026-09-03T03:09:51.858415100Z
closed_by: codex
summary: Implemented typed thirteen-stage ETL orchestration with per-job/source/season advisory locking, atomic canonical merge and replay accounting, non-destructive empty snapshots, exact completion counts, and sanitized rejection/failure evidence.
verification:
  - .\gradlew.bat :etl-core:test --tests io.brule.fantastik.etl.run.EtlRunOrchestratorTest => BUILD SUCCESSFUL; 4 tests, 0 failures, 0 errors, 0 skipped.
  - Typed fixture observed EtlStage.entries in exact order and persisted SUCCEEDED_WITH_REJECTIONS counts read=3 accepted=2 rejected=1 with two sanitized issue records.
  - Two executor-backed same-key runs held the second run outside the merge until release; maximum concurrent merges=1, merge calls=1, replay flags=false/true, canonical count=1, and transaction invocations=2.
  - A merge exception containing credential=raw-secret produced only ETL_STAGE_FAILURE:IllegalStateException plus the fixed sanitized rejection, exposed no cause, released the lock, and allowed the next same-key run to succeed; fatal required-field input skipped merge and empty input appended one snapshot while retaining canonical count 7.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL; 55 tasks (54 executed, 1 up-to-date), 11 admitted module boundaries, 30 product files, PostgreSQL integration tests, and healthy task/ADR ledgers.
follow_ups:
  - none
```
