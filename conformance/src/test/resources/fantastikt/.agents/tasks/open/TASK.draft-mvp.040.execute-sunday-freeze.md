---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.040
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.039
---

# TASK.draft-mvp.040: Execute the Sunday freeze runbook

## Description

Run and evidence the specification's T-24h, T-2h, T-15m, live-draft, and post-draft operational gates while preserving the bug-fix-only freeze after T-12h.

## Requirements

- Own the sanitized `docs/operations/2026-draft/sunday-runbook.md` report plus private dated receipts, backup identifiers, doctor outputs, and postmortem artifacts under `.data/`; calculate T-24h/T-12h/T-2h/T-15m from Task 038's verified draft start instant and timezone.
- Resolve every warning affecting draftable assets before freeze; after T-12h admit only verified bug fixes required for P0 operation.
- Final operation uses manual picks as authority unless Task 037's real mock gate passed, and all bulk data remains locally durable for offline use.

## Deliverables

- [ ] T-24h full sync/reconcile/doctor/league/draft-init/board commands pass with exact artifact and snapshot IDs.
- [ ] T-2h and T-15m refreshes, database backup, and `draft watch --offline-ready` pass with recorded times and digests.
- [ ] A revision audit from the recorded T-12h freeze boundary classifies every later change as a verified P0-blocking bug fix or records that no such change occurred.
- [ ] Live and post-draft receipts retain the complete event/recommendation sequence, ESPN reconciliation, exact replay, final backup, and postmortem inputs.

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
