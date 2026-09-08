# Assertion validity before persistence

The completed red witness at `1861299a75e7d2c2f795918dfd3878e2e4a6f0a3`
contains seven shared cases, five failing. A preceding Java probe against the
compiled value-correction source `355e70000178039d5e3e1594c54c42ac88f77bd6`
cleared profile-audit evidence after decoding. The shared reducer accepted a
profile revision that its typed decoder could not reconstruct. The probe only
initialized a disposable fixture; it did not apply the invalid profile. Exact
probe, source/JAR identities and completed red output are retained under
`docs/proof/assertion-boundaries`.

Before reducing an actor assertion, core now applies its existing typed codec
validation. This includes closure evidence, reconciliation, planning amendments,
disposition changes, assessments and profile changes. The exhaustive transition
dispatch makes future assertion kinds an explicit implementation choice. Task and
planning record constructors and manifest-bound imports retain their separate
checks. Storage adapters cannot opt out of the shared reducer.

Reusing the current assertion codecs preserves their existing validity rules and
version distinctions. In particular, legacy non-calendar `recorded_at` strings
retain their original meaning; new occurrence times remain validated, and neither
becomes storage acceptance time. No receipt hash, historical contract, protocol
version or evidence-truth claim changes. Assertions remain actor assertions.

Provider verification receives an owned, unmodifiable evidence map. A provider
can inspect or reject a claim, but cannot alter the caller's evidence through
that callback. Mutation attempts become ordinary verification failures, leaving
the receipt/review and ledger unchanged. This protects both the legacy direct
provider interface and current persisted-profile reconciliation. It is an API
authority boundary, not a sandbox for arbitrary injected JVM code.

The seven shared cases cover invalid profile audit maps; planning amendment and
disposition audits; blanked assessment criteria; emptied assessment audits;
receipt/review evidence and observation guards; valid legacy receipt and current
planning/profile cold reconstruction; and provider evidence mutation. Rejection
is checked through pure reduction, evolution, file planning and application, with
unchanged file bytes/mtimes and cold state. Valid file persistence owns its written
representation even if callers later change their DTO collection aliases.

The draft assertion DTO collections themselves are not all deeply immutable.
Callers must not concurrently mutate inputs during an operation. This correction
checks their validity at the shared mutation boundary and removes mutable evidence
authority from callbacks; it does not claim universal collection ownership.
No native reflection allowance, implicit command, consumer migration or v1 freeze
is introduced. Full source, shared JVM/native, packaged process and affected
kernel/generated-client checks are required before canonical closure.
