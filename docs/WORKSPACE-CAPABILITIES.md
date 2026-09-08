# Workspace/environment capability conformance

`TASK.migration.dropzone` exercises the persisted provider seam with independently
authored fictional fixtures. The test tree contains two namespaced capability
decoders and verifiers; neither is registered in the product CLI. No Dropzone
repository, host, service, claim or operator integration is migrated or activated.

The workspace fixture declares a local authority-observation task and a set of
repository labels and bounded relative path rules. Its provider contributes that
task as a prerequisite and requires a structured actor review of the exact declared
mutation scope. The environment fixture declares a local observation task, host
label, exact service-contract identities and a reviewable/held policy. It contributes
the observation prerequisite, a blocker for held work and a structured observation
requirement. These are semantic specimens; they perform no filesystem mutation,
workspace resolution, host access or external service operation.

Both providers receive the core's canonical `ProviderTask` and exact effective
contract. The persisted profile records their distinct provider identities, version
labels and fixture descriptor digests. Those digests are deliberately labeled
fictional conformance descriptors. The registry remains a trusted injection boundary;
this is not evidence of production artifact authentication, signed observations,
operator identity or permission to modify a real workspace.

The nine shared JVM/native file-ledger cases prove:

- Installation alone does not activate schemas. Unknown optional values and an
  inactive malformed known payload survive exactly; large integers, decimal type
  and scale are preserved, as are Unicode controls, line separators, noncharacters
  and supplementary characters in nested values/keys. Optional authority-like text
  grants no behavior.
- Required feature identities remain core-readable. Exact persisted pins activate
  contributed prerequisites without rewriting authored `requires` or introducing
  planning ownership. Missing, mismatched or failed providers leave work unresolved.
- Deterministic reads, validation and lifecycle checks do not invoke environment
  probes. Tests call the separate probe interface explicitly and submit its synthetic
  result as a new actor assertion bound to the task, complete effective contract,
  declared host/service identities and a validated observation time.
- An observation alone cannot override a held policy, open prerequisite, missing
  core/provider evidence, absent core acceptance criteria or an out-of-scope review.
  The core retains all state-transition and CAS authority; evidence text is not run.
- Changes to contributed authority inputs propagate transitively. Reviewing an
  intermediate task cannot silently acknowledge its consumers. Historical receipts
  remain intact after explicit revalidation.
- An environment contract change invalidates earlier evidence from both activated
  capabilities, even when the workspace payload itself is unchanged. Fresh explicit
  review addresses the changed full task contract while old receipts remain history.
- Cold reconstruction uses the exact persisted profile. Unavailable code does not
  erase opaque state, profile history, task history or receipts, and failed operations
  leave the repository unchanged.
- Unknown structural capability fields, missing required payloads, path traversal,
  unsupported stateful claim identities and non-core lifecycle labels fail closed.
- Programmatic task construction rejects malformed extension namespaces and
  duplicate required identities before any ledger write, using the same validity
  boundary as parsed tasks and planning records.

The synthetic probe and reviews are controllable test inputs, not attestations of
the external world. A production capability needs its own reviewed observation,
authority and acquisition design. Neither a fixture's `ready` status nor the normal
core receipt model is a cryptographic approval token. No lease, claim ownership,
expiration, release gate or cross-repository authority mapping is supplied here.
Unsupported stateful consumer mappings therefore remain blocked.

The constructor case exposed a real mismatch: at source commit
`2575d5404f2e15e01983a931678d2cc5555e2313`, a programmatic seed could write an
invalid namespace and then fail while reconstructing its own output. The preserved
[failing test result](proof/workspace-capabilities/constructor-regression-red.xml)
records the changed disposable ledger. Shared constructor validation now enforces
the existing namespace grammar before mutation. Valid wire records, contract
digests and opaque extension values retain their existing meaning.

Four additional core Unicode cases cover a second value-boundary defect, reproduced
at source `3df76b8f1b37e47726bbfee3b048cb990a4ec803`. JVM strings can contain unpaired
surrogates, and UTF-8 encoding replaced them with `?`; the two different values
therefore produced the same framed semantic hash before SHA-256 was applied.
String values and object keys now reject unpaired surrogates, including escaped
YAML input. The JSON writer also checks keys if a caller mutates a borrowed map.
This intentionally rejects malformed text that the old decoder admitted; it does
not change canonical identities for well-formed Unicode or normalize text.

Valid C1 controls, NEL, line/paragraph separators and BMP noncharacters remain
supported. The JSON writer escapes these scalars so the shared YAML decoder cannot
fold whitespace, reject raw characters or reinterpret a flow-mapping key. Their
semantic hashes are unchanged; their generated JSON spelling is now safe. The
tests retain a pre-fix Unicode digest and the [completed failing baseline log](proof/workspace-capabilities/unicode-regression-red.log).

A further provider regression at source `cbfd7378df6cb3c4364a19fee50a180becec265b`
showed equal semantic contracts producing different readiness decisions because a
provider could inspect map insertion order and decimal scale. The preserved
[failing result](proof/workspace-capabilities/provider-input-regression-red.xml)
records an allowed versus blocked contribution under the same exact pin/contract.
`ProviderTask` now exposes a recursively canonical semantic view for both evaluation
and evidence verification: maps use the existing ordinal ordering and decimals
use the existing trailing-zero equivalence. The original record/payload remains
lossless, and contract hashes are unchanged. A capability that needs a precision
declaration must encode it as explicit semantic data, such as an integer field;
formatting cannot secretly change readiness under an unchanged contract.

This adds conformance coverage to the existing alpha5 provider/profile design and
repairs those validity boundaries. It introduces no loader, probe runner or reflection
configuration. The existing packaged CLI parity corpus covers missing-provider
diagnostics and bounded profile mutations with the default empty registry; the new
matching-provider cases execute through the shared JVM/Native Image test corpus.
Actual CI evidence is required before the bounded task closes. Operational adoption,
native-v1 freeze and service implementation retain their separate prerequisite gates.
