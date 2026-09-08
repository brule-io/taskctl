# Audited planning history closure evidence

This evidence covers `TASK.protocol.planning-history` and the explicit boundaries
in [PLANNING-HISTORY.md](../../PLANNING-HISTORY.md). It is development conformance,
not a published release, consumer migration or native-v1 freeze.

[CI run 34173404198](https://github.com/brule-io/taskctl/actions/runs/34173404198)
passed candidate `07e58a52daac7c070a2084d73bec63f4d30dc02b` on Linux x86_64,
Windows x86_64 and macOS aarch64. Each platform passed 142 source tests, including
three architecture policy checks, 139 identical JVM/Native Image behavioral tests
and 212 packaged process parity cases. [validation.json](validation.json) binds
the exact candidate, checkout `927fa8bbd87ac84a03ef619e9ebb34bc39d5bef8`, parents,
jobs, test identities and build artifacts. Downloaded archive bytes were checked
against their metadata digests. Local Windows repeated all 139 behavioral and
212 process cases; the opt-in abstract-machine callers also compiled.

The legacy planning witnesses were captured using the unchanged pre-planning
executable built from `3e504e61aaf040de3aa69f531f47785657ff40a3`. Their
[source index](../../../core/src/test/resources/planning-native/sources.json)
records the public producer snapshot, executable/archive identity and JSON byte
digests. The new codec preserves those old record encodings and rejects new
fields in the old envelope. Existing legacy receipt and task-revision golden
identities continue to pass. Native baselines retain the observed draft-1 records;
imported records retain the exact manifest and original historical classification.

Core cases cover independent planning identities, immutable parent chains,
amendment versus archival, explicit positive/negative assessments, exact scope and
member contract/input binding, old evidence currency, required-capability refusal
and opaque numeric values. File tests cover stale CAS and planning HEADs, cold
reconstruction, tampering, bounded plans and interrupted recovery/conflict refusal.
Packaged cases repeat the planning commands, strict diagnostics, task-state and
file preservation, import-then-track provenance, read-only boundaries and offline
cached execution through both implementations. CI separately runs packaged
bootstrap tests and inspects the actual producer ledger without mutations.

The exact-value cases exposed and reproduce an existing JSON boundary defect:
whole-valued decimals became integer tokens. The focused writer correction retains
decimal type and scale, including zero/negative scales, without changing canonical
hash domains or existing fractional/integer spellings. Native and JVM file bytes
and cold decoded values agree. No broad reflection allowance was introduced.

Review confirms that adoption is explicit under native alpha4, that old records
and task history are not silently upgraded, and that planning operations preserve
task records, contracts, prerequisites and receipts. Archival preserves membership
and readiness. An assessment is an actor assertion with explicit ordered criterion
evidence; no group acceptance is inferred from member closure. Current provider
capabilities remain the existing minimal profile and unavailable required semantics
fail closed. Effective provider/profile persistence is a separate open task.

The producer still uses its published alpha.2 pin. Its lifecycle closure receipt
therefore uses the supported legacy actor-assertion envelope; it neither upgrades
the producer's planning storage nor manufactures an authoritative acceptance time.
