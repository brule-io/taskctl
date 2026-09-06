---
kind: epic
schema: loom.agent/v1
ref: EPIC.draft-mvp.001
---

# EPIC.draft-mvp.001: Operate the Sunday fantasy-football draft engine

## Description

Deliver the P0 source federation and offline-capable terminal decision loop defined by the admitted Sunday Draft MVP specification.

## Outcomes

- Every ESPN-draftable asset is represented durably and recommendations are reproducible from explicit source snapshots.
- A manual opponent pick updates availability, roster state, scarcity, survival estimates, and payout-aware rankings within the specification's latency gates.
- The complete draft can be operated offline after pre-draft synchronization and reconstructed from PostgreSQL after restart.

## Boundaries

- PostgreSQL remains authoritative; Neo4j, play-by-play ingestion, web/mobile interfaces, and event brokers are outside P0.
- Source facts retain their native semantics and provenance; identity, football observations, fantasy eligibility, forecasts, and derived scoring are not collapsed.
- Production Kotlin obeys the module boundaries and strict type prohibitions in `SPEC.md`.
- External writes and live platform activity require their own task and operator-supplied credentials or configuration.

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
