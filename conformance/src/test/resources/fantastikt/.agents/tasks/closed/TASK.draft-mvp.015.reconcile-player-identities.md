---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.015
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.008
  - TASK.draft-mvp.011
  - TASK.draft-mvp.013
  - TASK.draft-mvp.014
---

# TASK.draft-mvp.015: Reconcile player identities without silent merges

## Description

Implement the exact-first reconciliation order, human-review candidates, provisional ESPN player creation, and explicit entity merge command.

## Requirements

- Own identity reconciliation services/CLI packages and reconciliation fixtures; reuse the persistence boundaries from Task 011.
- Resolve exact namespaced IDs and exact crosswalk paths before considering normalized name, birth date, team, and position evidence.
- Never auto-merge name-only candidates; unresolved draftable ESPN players receive a provisional Person, exact ESPN ID, asset linkage, and visible doctor status.

## Deliverables

- [x] Tests cover GSIS match, ESPN match, crosswalk repair, name collision non-merge, provisional rookie creation, and approved merge replay.
- [x] `ball players resolve` accepts UUID/exact external ID/handle and returns candidates instead of guessing ambiguity; the milestone command `ball players resolve --espn-id <id>` returns the same stable Person UUID after restart.
- [x] `ball etl reconcile` and `ball etl unresolved` expose evidence and require an explicit approval actor for merges.

## Closure

```yaml
closed_at: 2026-09-03T03:48:55.640927500Z
closed_by: codex
summary: Implemented exact-first player identity reconciliation, evidence-bearing crosswalk repair, collision-safe candidate review, durable provisional ESPN asset linkage, explicit audited merge approval, identity lookup queries, and injectable players/ETL CLI handlers.
verification:
  - .\gradlew.bat :domain:test --tests *IdentityReconciliationServiceTest => BUILD SUCCESSFUL; 6 tests, 0 failures, 0 errors, 0 skipped, covering nfl:gsis-player and ESPN exact matches, crosswalk repair, same-name non-merge, provisional rookie linkage, and approved merge replay.
  - .\gradlew.bat :cli:test --tests *IdentityCommandsTest => BUILD SUCCESSFUL; 4 tests, 0 failures, 0 errors, 0 skipped; UUID/exact/handle lookup returned ambiguity candidates and two independently constructed command services over the same persisted identity returned the identical Person UUID for --espn-id 4427366.
  - .\gradlew.bat :durability-postgres:postgresIntegrationTest --tests *IdentityPersistenceIntegrationTest => BUILD SUCCESSFUL; 4 tests, 0 failures, 0 errors, 0 skipped; restart preserved exact lookup, UUID lookup, birth-qualified handle candidates, provisional visibility, and transactional merge aliases.
  - ball etl reconcile rejects a missing --approved-by with exit 64; an approved merge prints survivor/retired UUIDs, approval actor, and evidence count; ball etl unresolved prints exact authority/value, evidence artifact UUID, and reason.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL in 47s; 59 actionable tasks, 59 executed; 11 admitted product modules, 40 product implementation/resource files, 7 forbidden architecture fixtures rejected, all tests and PostgreSQL integration tests passed, task/ADR doctors healthy.
follow_ups:
  - none
```
