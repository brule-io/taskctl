# Pre-v1 review and implementation gates

Current disposition: [the native-v1 assessment](NATIVE-V1-ASSESSMENT-2026-09.md)
reviews the completed correction, compatibility, kernel, IDL and type-boundary
proofs. Freeze is deferred pending an explicit candidate specification, a new
alpha consumer distribution and a separately reviewed freeze decision. The
earlier checkpoint below retains its dated meaning.

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
  bounded `context` and a complete typed `snapshot`, with
  [three-platform closure evidence](proof/read-models/README.md).
- [Audited planning history](PLANNING-HISTORY.md) adds explicit roadmap/epic
  amendments, archival and scope-bound assessments through an opt-in storage
  version. Planning changes preserve task contracts and causal edges; its
  [closure evidence](proof/planning-history/README.md) records three-platform parity.
- [Persisted effective profiles](EFFECTIVE-PROFILES.md) bind contracts, history,
  currency and contributed input observations to the same exact provider pins.
  Deterministic evaluation and probes remain separate; concrete production
  capabilities and preserving legacy mappings remain gated.
- [Typed evidence time](EVIDENCE-TIME.md) distinguishes validated new occurrence
  values, preserved historical strings and optional storage acceptance metadata.
  Its [closure evidence](proof/evidence-time/README.md) records conformance and parity;
  the file adapter does not manufacture authoritative acceptance timestamps.
- The [bounded remote kernel](REMOTE-KERNEL.md) preserves ledger `Revision` as
  whole-snapshot CAS and records measured unrelated-writer contention. A narrower
  operation remains a separately typed/versioned design choice; it cannot silently
  reinterpret the existing precondition.

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
