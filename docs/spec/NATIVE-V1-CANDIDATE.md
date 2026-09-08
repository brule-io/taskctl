# Native-v1 candidate boundary

Candidate revision: **2026-09-07.1**. Status: **proposal, not a frozen protocol**.
Owning task: `TASK.protocol.v1-spec-candidate`. This records the boundary selected
by the [readiness assessment](../NATIVE-V1-ASSESSMENT-2026-09.md). No executable
accepts `brule.task/v1` as a result of this document. Existing alpha identifiers
continue to select their existing semantics.

The proposal is to stabilize the semantic rules below, with separately versioned
record/evidence envelopes and explicit adapter contracts. It does not stabilize
Kotlin classes, repository layout, every CLI presentation field or the experimental
HTTP protocol as one indivisible API. Project-specific concepts remain namespaced
capabilities unless they are necessary for the task lifecycle or causal graph.

[candidate.json](candidate.json) binds this document's invariant identifiers to
exact source files and executed conformance identities. The check script verifies
those links and selected source fingerprints. It is a traceability check, not a
second reducer, proof of coverage completeness or rerun of historical CI.

## Proposed stable semantic rules

### C01 — Typed exact values

Protocol values are the closed sum of null, boolean, Unicode-scalar string,
arbitrary integer, exact decimal, array and string-keyed object. Integer and decimal
are distinct categories. Arrays retain order. Unknown optional extension payloads
retain their exact typed values, including decimal scale. Duplicate object keys,
malformed Unicode and unsupported scalar/tag kinds are rejected at decoding.
YAML spelling/comments are a source-preservation concern distinct from typed value
round trips. Neither JSON serialization nor hashing may pass through binary float.

The reference Value objects own immutable collection copies. Programmatic draft
record/assertion inputs are revalidated at the transition boundary. Concurrent
caller mutation during an operation remains unsupported; the internal Kotlin DTOs
are not promised as universally immutable public APIs. The syntax policy forbids
untyped protocol state and unchecked-cast escapes outside a reviewed boundary;
the current YAML decoder needs no such escape.

### C02 — Complete nominal identities

Tasks, roadmaps and epics have distinct identities and are unique within their
repository namespace. The complete case-sensitive identity is significant; a
numeric prefix or filename locator is not an identity. In the current alpha,
`TASK.`, `ROADMAP.` and `EPIC.` are followed by
`[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*`. Digest identity spelling is
`sha256:` followed by 64 lowercase hexadecimal digits. Parsing crosses the
validity boundary; nominal Kotlin constructors remain private.

Repository identity, task identity, task revision, semantic contract, transitive
input digest, import identity and ledger revision are different concepts even
where their text representations coincide. Cross-repository observations must
retain the repository namespace. The current core does not resolve another
repository or refresh an external observation automatically.

### C03 — Bounded task contract and owned namespace

A task records identity, a display title, lifecycle state, bounded intent,
prerequisites, nonempty requirements and acceptance criteria, named verification
requirements, separately readable required capabilities and an `extensions` map.
Core owns top-level fields; unknown fields fail. Every extension key uses a
validated versioned namespace. A task is one executable state transition, not a
container that owns roadmaps or child tasks.

The currently writable task envelope is `tasking/core-draft-2`. Its exact keys,
optional defaults and decoding rules remain in `DraftDocument` and `NativeCodec`.
This candidate does not silently relabel that envelope or make a draft-1 record
use draft-2 title/verification semantics.

### C04 — Causal prerequisites

Validate unique identities, all prerequisite targets and acyclicity over the
complete effective task graph, including contributed provider edges. Filtering
by roadmap or epic happens afterward and cannot hide an invalid graph or unmet
prerequisite. Open tasks are executable only when prerequisites are closed,
current-input and provider checks permit execution, and core closure requirements
are satisfied. Presentation order never creates an edge or an execution priority.

### C05 — Independent native planning

Roadmaps are durable named lines of advance; epics are feature/capability scopes.
Both associate tasks, with empty and overlapping memberships allowed. A task may
belong to several roadmaps and epics; neither implies ownership of the other.
Planning changes do not alter unchanged task contracts or prerequisite edges.

Audited amendments and explicit active/archived disposition retain immutable
planning revisions. Archival is not deletion or a task readiness override.
Planning acceptance is an explicit assertion about an exact scope/revision and
observed task contracts/inputs, never inferred from all members being closed.
Changed scope or semantic inputs make the old assessment historical; the old
evidence is retained. Ongoing roadmaps need not have a completion event.

### C06 — Canonical semantic contracts

Closure evidence binds the semantic contract it addresses, not raw Markdown bytes.
The current draft-2 projection includes identity, normalized intent, sorted
prerequisites, ordered normalized requirements/acceptance, sorted verification
names, required capabilities and active semantic payload/pin data. Title, lifecycle,
optional inactive annotations and incidental formatting are excluded. Draft-1
historically included title and keeps its old hash domain.

CommonMark structure determines semantic prose. Wrapping, supported bullet
presentation and acceptance checkbox progress are normalized; nested requirements,
code, link targets and hard breaks remain meaningful. Exact normalization is the
pinned `SemanticMarkdown` implementation and its witnesses, not a claim that all
human paraphrases are equivalent.

The reference framed encoding is **not RFC 8785**. Null is `n`; booleans `b0`/`b1`;
strings are `s` + UTF-8 byte length + `:` + text; integers are `i` + an encoded
decimal integer string; decimals are `d` + an encoded plain decimal string with
trailing zeros stripped. Arrays are `l` + count + `:` + encoded elements. Objects
are `m` + count + `:` + encoded key/value pairs sorted by UTF-16 ordinal key order.
There is no Unicode normalization. A semantic digest hashes the UTF-8 encoding of
the two-element array `[domain, value]` with SHA-256.

Current hash domains remain explicitly distinct: draft-1 semantic-contract/1,
`taskctl.semantic-contract/2`, and profile-bound semantic-contract/3. The latter
binds the unchanged base contract, complete effective profile and active payloads.
Provider inputs use the same canonical semantic view; map order or decimal scale
excluded from the hash cannot secretly change eligibility. Stored values keep
their original representation. Receipt-file byte digests and adapter snapshot
digests are separate identities; do not substitute one for a semantic contract.

### C07 — Revision observations and currency

A prerequisite observation can bind upstream identity, observed contract,
observed task revision and observed transitive inputs. Missing historical
observations remain explicitly unknown. Transitive input digests include the
effective contract and each upstream input digest, so an intermediate unchanged
contract cannot hide an earlier upstream change.

Lifecycle and currency are independent. A closed task keeps its receipt while
becoming affected by changed contracts or unresolved inputs. A historical receipt
continues to address the old contract and does not satisfy the changed contract.
Explicit reconciliation addresses the inspected task HEAD and exact observations,
supplies rationale and evidence, and uses CAS. Only `revalidated` acknowledges
current inputs; `revise`, `successor` and `unresolved` retain an unresolved review.
Task revision changes that preserve the semantic input need not invalidate work
merely because an incidental record representation changed.

### C08 — Core transitions and evidence

Seed new work as open. Close only through the shared lifecycle checks, binding
task identity, current semantic contract, named verification requirements,
prerequisite/currency checks and required provider evidence. Revise preserves
lifecycle and records immutable history; it cannot quietly reopen or close work.
The current operation algebra has no task deletion or reopen operation. A successor
is a distinct admitted task. Retain earlier task revisions and receipts.

Evidence stays labeled as an actor assertion unless a separately supported
evidence verifier establishes a stronger classification. Validation does not run
the text in an evidence field or prove an actor truthful. Historical source closure
and native actor receipt are distinct facts. Providers may add obligations and
reject evidence; they cannot override a failed core guard.

### C09 — Occurrence and acceptance time

Preserve legacy nonblank `recorded_at` strings and their original envelope hashes.
New occurrence envelopes require validated explicit-offset timestamps with seconds
and at most nanosecond precision; the current grammar rejects year zero, unknown
offset `-00:00`, malformed dates and excess precision. Original valid spelling is
retained. Equivalent instants may remain distinct actor assertions.

An accepting storage boundary may add separately typed `AcceptedAt`; actors cannot
supply it as storage authority. The file adapter does not invent acceptance time.
The kernel samples its acceptance clock after semantic validation, durably only
with its transaction. Revisions and event sequence determine CAS/order, never an
actor clock. A timestamp is not authentication or an exact physical commit claim.

### C10 — Narrow extension authority

Unknown core field: reject. Unknown optional extension: preserve without execution.
Known activated extension: evaluate its pinned semantics. Required unavailable or
mismatched support: permit bounded inspection/storage where valid, fail execution
and evidence acceptance closed. Installation alone never activates a provider.

The effective profile binds validated extension/provider identities, exact version
labels and artifact digests. The trusted registry must supply matching verified
code; comparing its declared descriptor is not independently authenticating it.
Providers contribute deterministic validation, prerequisites, readiness blockers,
evidence obligations and verification. Core validates the resulting graph and
owns every transition. Environment probes remain separate explicit observations.
The reference CLI ships no provider loader or placeholder approval implementation.
Schema-only data validators acquire semantic authority only through the same
explicit profile/capability mechanism when they affect admission or execution.

### C11 — First-class import provenance

Select an explicit adapter/version and exact source repository/revision/content
witnesses. Structure and provenance can disambiguate legacy dialects; a shared
label alone cannot. Preserve injective complete identity mapping, historical state,
unsupported semantics, the resulting manifest and target contract identities.
Conversion review binds that exact manifest. Changed source, adapter or projection
requires a new review even if some task contracts happen to remain equal.

Do not fabricate native receipts or missing prerequisite observations, reinterpret
old closure as current verification, or present a legacy dialect as native v1.
Claimed/blocked, host/operator and authority restrictions cannot be lowered to
optional labels. Their operational import requires a proved preserving capability
and consumer-owned integration. Current fictional specimens establish mechanisms,
not approval to migrate a particular live repository.

### C12 — Ledger seam and atomic acceptance

The semantic seam is snapshot, task lookup, frontier and
`apply(expectedRevision, transition)`. Core reduction is independent of paths,
network, locks and clocks. A ledger Revision identifies the complete inspected
snapshot. An adapter validates the expected revision and shared transition at
its acceptance boundary. Ordinary refusal leaves the prior state intact. The
file adapter uses a recoverable journal: process interruption can leave pending
writes that block inspection until explicit recovery. This is not a guarantee
against arbitrary hardware failure or distributed-clone divergence.

File and kernel adapters may use different snapshot hash domains. Their lifecycle,
contracts, receipts and currency must agree without falsifying storage-specific
origin identities. Whole-snapshot CAS can reject unrelated writers. Any narrower
precondition needs a separate explicit versioned operation with input fencing.
Snapshot identity is not request deduplication: a no-op can retain it while the
kernel appends an acceptance event. A lost reply is uncertain, with no automatic
application replay in the experimental client.

### C13 — Read and mutation boundaries

Read operations do not initialize a project, create ledger locks/journals, run Git,
execute evidence, contact services or activate probes. Diagnostic `doctor`, bounded
actionable `context`, complete typed `snapshot`, and exact frontier are distinct
projections. Context truncation is explicit and cannot silently redefine readiness.
The current 32,768-byte/12-item context limit is a presentation contract, not a
limit on the mathematical task universe.

Mutations are explicit and bounded to declared tasking/transaction paths. Plans
expose preimage/postimage digests; applying still requires CAS. Recovery preserves
external edits by refusing conflicts rather than deleting evidence. The wrapper's
explicit pinned acquisition/cache boundary is separate from core read semantics;
offline mode disallows acquisition. No command implies a source-control mutation.

## Compatibility and authority matrix

| Family | Current identities and opt-in boundary | Proposed v1 treatment |
| --- | --- | --- |
| Task/semantic model | core-draft-1 and core-draft-2; semantic-contract/1, /2 and profile-bound /3 | Stabilize the semantic rules above. Keep every historical interpretation; a future v1 envelope must be separately admitted, never an alias that rehashes old records. |
| Planning | planning-draft-1; planning-draft-2 plus audited revision/assessment/history envelopes | Native semantic concepts and independent audit/assessment binding. Exact filesystem layout is adapter-owned. Legacy baseline adoption never invents old amendments. |
| Task evidence/history | receipt alpha1/alpha2; reconciliation /1, /2, /3; task-revision /1, /2, /3; history /1; transitive-input /1 | Preserve named time/profile/import distinctions and old identities. Version new envelopes explicitly. Native v1 does not retroactively strengthen an assertion. |
| Effective profile | effective-profile, provider-pin, profile audit/change/revision/history and revision-semantics /1 | Core-readable authority/pin boundary. Concrete capability namespaces, binary acquisition and trusted provider code remain separate. |
| Import | import-manifest/admission /1; import-review /1 and /2 | Preserve first-class provenance/review semantics; concrete source dialect adapters remain explicit compatibility paths. |
| File storage | repository/policy alpha1; native alpha1 through alpha5; transaction/file-revision/init-plan alpha1 | Versioned reference adapter. Alpha2 adopts history, alpha3 import, alpha4 planning history and alpha5 profiles; combined capability histories are retained. Older writers reject unsupported markers. None means native v1. |
| Composition/read APIs | init/seed alpha1, CLI alpha1, context alpha1, doctor/snapshot alpha1–alpha3, planning/profile view envelopes | Independently versioned CLI composition contracts. Kotlin APIs remain internal. JSON/exit-code guarantees are reviewed separately from core hashes. |
| Remote/IDL | kernel snapshot/state/transition/apply/result/event/page/error alpha1; wire-idl and Smithy projection alpha1 | Experimental adapter/client evidence. No frozen HTTP API, production service, authentication, tenancy or stock Smithy serializer promise. |
| Distribution | exact tool version, wrapper 3/lock 2, native and JVM manifests; optional canonical-artifact RPM | Product delivery contracts. A tool release number does not rename task/history protocols or migrate a repository. |

The literal source inventory in [candidate.json](candidate.json) distinguishes
envelopes and hash domains from their family descriptions. It includes the actual
source anchors, including dynamically selected doctor/snapshot result versions;
family shorthand in the table is not a new runtime protocol identifier.

## Limits and the eventual freeze decision

This candidate does not require production service features for every valid task
graph. The current bounded kernel deliberately refuses persisted effective profiles,
uses loopback transport and finite capacity, and performs no automatic retry.
File/YAML representation limits, finite digests and locators remain implementation
bounds; they do not supply a mathematically unbounded machine.

Before a stable native-v1 release, an explicit reviewed decision must name the
exact accepted candidate, concrete v1 record/evidence envelope mapping, backward
reader/writer support and published conformance artifact identities. Any new
envelope implementation needs its own admitted task and compatibility tests.
That decision is still open. This task makes the proposal and its gaps reviewable;
alpha.3 ships the existing tested alpha semantics and does not implement or freeze v1.
