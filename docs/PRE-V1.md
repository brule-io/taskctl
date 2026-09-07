# Pre-v1 review and implementation gates

The [2026-09-07 checkpoint](PROTOCOL-CHECKPOINT-2026-09.md) records the decisions
for this review. Its four correction tasks and strengthened compatibility/kernel
prerequisites make the remaining implementation explicit. The findings below
describe the reviewed alpha implementation; recording a decision does not claim
that the corresponding implementation is already available.

These findings come from review of 0.3 current main. Fantastikt and Brule have
exercised consumer adoption. `TASK.protocol.checkpoint` reassesses these
findings after the successful third, divergent compatibility specimen in an
isolated test repository. Operational migrations are consumer-owned (see
[ownership](CONSUMER-OWNERSHIP.md)). Later kernel work should pressure-test the
service-related choices under the checkpoint's strengthened task prerequisites.

- The [distinct read projections](READ-MODELS.md) implement `doctor` diagnostics,
  bounded `context` and a complete typed `snapshot`; their evidence remains
  tracked by `TASK.protocol.read-models` until all required checks pass.
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

The [divergent specimen](DIVERGENT-IMPORT.md) adds concrete checkpoint inputs:
same-label/different-structure rejection, injective legacy identity mapping,
unchecked historical closure without fabricated proof, and execution restrictions
that cannot be lowered to optional labels. The current open/closed import subset
does not admit claimed/blocked or host/operator eligibility semantics. A checkpoint
must explicitly decide the preserving capability/profile boundary or continue to
block those consumer adoptions; passing the bounded specimen alone is insufficient.
