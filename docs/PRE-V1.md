# Decisions to revisit before native v1

These findings come from review of 0.3 current main. They do not block the first
Fantastikt migration. `TASK.protocol.checkpoint` should reassess them after the
first three migration specimens; later kernel work should pressure-test the
service-related choices. This board does not authorize implementation early.

- Separate `doctor` diagnostics, a bounded `context` briefing, and a complete
  machine-oriented `snapshot`. They currently expose substantially the same data.
- Choose an explicit audit/revision model for roadmap and epic amendments,
  archival and acceptance. Planning changes must not implicitly invalidate task
  contracts or create ownership/dependency edges.
- Before activating behavioral providers, persist the same effective profile and
  provider identity for contracts, history and currency. Contributed prerequisites
  must participate in transitive input observations; deterministic evaluation and
  environment probes remain separate.
- Validate a typed actor-asserted occurrence timestamp and distinguish it from
  server-authoritative acceptance time before exposing a remote API. Existing
  nonblank timestamp strings must retain their historical meaning on conversion.
- Preserve ledger `Revision` as snapshot identity while deciding remote CAS
  granularity from concrete contention/conformance tests. The file adapter's
  whole-ledger CAS need not force unrelated remote task edits to serialize.

The 0.3 native guide has been updated to describe alpha2/history and the complete
dependency observation shape directly. Historical versions remain named explicitly.
No native-v1 freeze, provider activation or service implementation follows from
this documentation correction.
