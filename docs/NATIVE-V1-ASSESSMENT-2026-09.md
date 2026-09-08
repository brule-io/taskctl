# Native-v1 readiness assessment

Decision date: 2026-09-07 PDT. Owning task: `TASK.protocol.v1-candidate`.
Disposition: **defer native-v1 freeze; continue with an explicit candidate specification and a new alpha distribution**.

The model has survived all four required entry paths and the admitted complex
compatibility experiments. The results support continued productization without
another core-model redesign. They do not yet define the exact promises of a
stable `brule.task/v1` release. Completing this assessment records that decision;
it does not rename any alpha envelope, upgrade a consumer, or freeze the protocol.

## Evidence reviewed

The reviewed assembled source is
`b0bd0de6cb6e5e28df1f3fe5f7f26893da0c3fc0`. Its canonical ledger has 31 tasks:
30 closed/current and this assessment open/current. All five direct prerequisites
and their transitive gates are closed/current. The exact task HEADs, contracts,
matching historical receipt identities and read boundary are recorded in the
[dated ledger observation](proof/v1-assessment/ledger-observation.json).
Those receipts are actor assertions with retained evidence references, not new
independent certifications of their contents.

| Entry path or pressure | Evidence | What it establishes and its limit |
| --- | --- | --- |
| Greenfield initialization | [Packaged process proof](proof/assertion-boundaries/product/ci/parity-linux-x86_64.json) and the matching Windows/macOS records; [bootstrap contract](BOOTSTRAP.md) | Planned initialization, empty/seeded planning, wrapper acquisition, cold/offline operation, diagnostics, CAS and bounded writes work through both implementations. This is executable fixture evidence. |
| Existing code without tasking | The same three-platform `adopt` process cases; [producer self-hosting](SELF-HOSTING.md) | Adoption preserves existing source and AGENTS instructions, rejects collisions, and does not run implicit Git operations. The producer itself reconstructs through its pinned distribution. |
| Historical import | [Fantastikt](MIGRATION-FANTASTIKT.md), [Brule ownership and acceptance](CONSUMER-OWNERSHIP.md), [divergent specimen](proof/divergent-import/README.md) | Two real consumer adoptions and a separately authored dialect exercise source provenance, explicit adapter/version selection, immutable manifests and historical evidence classification. The later specimens do not migrate another consumer. |
| Remote-backed lifecycle | [Kernel proof](proof/remote-kernel/README.md), [generated-client proof](proof/idl-projection/README.md) | File, PostgreSQL and loopback HTTP share one reducer. Transaction rollback, cold reconstruction, strict CAS, receipts, events, lost responses and a typed Python projection pass. This is a bounded minimal-profile experiment, without service operation or a public SDK promise. |
| Orthogonal roadmap/epic history | [Complex planning](proof/complex-planning/README.md), [planning history](proof/planning-history/README.md) | Membership, feature association and the prerequisite DAG stay independent. Amendments, archival and explicit scope assessments retain history without inferring group completion. |
| Required workspace/environment semantics | [Workspace capabilities](proof/workspace-capabilities/README.md) | Exact trusted pins, contributed edges/readiness/evidence and explicit observations work in fictional specimens. No production provider, live probe, host authorization or stateful consumer converter is supplied. |
| Related ledgers and authority identity | [Related ledgers](proof/related-ledger/README.md), [historical authority](proof/historical-authority/README.md) | Explicit repository/full-task/contract/input observations preserve cross-repository distinctions and historical authority. No automatic cross-repository freshness, source hydration or historical native-v1 claim is made. |
| Type and construction boundaries | [Value ownership](proof/value-boundaries/README.md), [assertion inputs](proof/assertion-boundaries/README.md) | Demonstrated mutable-alias defects are corrected before durable acceptance. Exact Value data, nominal identities and typed decoders remain mandatory. Draft DTOs are not universally immutable and concurrent caller mutation is unsupported. |

The assertion candidate passed 203 source-corpus tests, 200 identical JVM/native
behavioral identities and 289 process cases per platform; its final assembled
source also contains the six historical-authority cases. The separate kernel and
IDL suites remain separate evidence, not native test counts. Source and archive
identities are in each linked proof; no test count implies protocol completeness.

## What the evidence supports

Identity, semantic contract binding, revision-aware prerequisite observations,
lifecycle/currency independence, retained historical evidence and the narrow
extension boundary remain the stable design center. Tasks are atomic executable
nodes. Roadmaps are durable lanes and epics are capability scopes; neither owns
the task DAG. Empty planning indexes remain valid.

Core owns the top-level record namespace and validates the complete graph and
transitions. Optional unknown extension data stays typed, lossless and inactive.
Required feature identities remain readable without their provider. Trusted exact
providers contribute deterministic predicates and evidence checks; they receive
no lifecycle authority. External probes and observations remain explicit.

`TaskLedger.apply(expectedRevision, transition)` retains whole-snapshot CAS.
The kernel's measured three rounds each accepted one of four competing writers
and rejected the other three as stale, without retries. That behavior is a
documented baseline. A narrower operation would need a different, versioned
precondition with input fencing; performance wishes cannot reinterpret Revision.

Canonical semantic digests deliberately ignore nonsemantic layout and normalized
decimal scale while preserving numeric category. Stored opaque payloads retain
their exact typed representation. Actor occurrence, legacy time text and storage
acceptance time have separate meanings. Neither a digest nor a valid receipt
proves an actor's statement true.

## Why freeze is deferred

1. The implementation has explicit, independently versioned alpha record,
   history, planning, profile, import, read and transport envelopes. There is no
   single reviewed candidate that names which of these are part of native v1,
   which remain adapter details, and what each compatibility promise covers.
   Consolidating that boundary is specification work, not another universal schema.
2. The latest public distribution is `0.3.0-alpha.2`. The new read models, typed
   occurrence envelopes, planning/profile history and boundary corrections have
   development CI evidence, but are not yet an immutable consumer release.
   A new alpha should expose those changes with accurate notes and the existing
   native/JVM acquisition, parity and provenance contract.
3. Freeze is an explicit decision after reviewing that candidate and release
   evidence. It must not be inferred from an empty frontier or from closing this
   assessment. No owner freeze decision has been recorded here.

Production remote authentication, tenancy, request deduplication, broad language
support and high-throughput CAS are outside this assessment. They are not silently
promoted to universal-core requirements or prerequisites for every valid task
universe. The bounded kernel establishes the seam; it does not authorize deployment.

## Bounded follow-through

The [CAS admission](proof/v1-assessment/followthrough-admission.json) creates
`TASK.protocol.v1-spec-candidate` to inventory existing versioned boundaries,
map invariants to exact conformance identities and document compatibility without
changing semantics. `TASK.release.0.3.0-alpha.3` follows that candidate;
`TASK.release.0.3.0-alpha.3-acceptance` covers the producer pin and disposable
consumer acceptance. `TASK.distribution.rpm-alpha.3` independently follows the
published native release. Preserve the JVM reference, native preference, exact
digest pins, offline acquisition and the separate RPM packaging path. All prior
task HEADs were preserved; these admissions claim no implementation completion.

Any eventual freeze requires its own explicit reviewed decision. Consumer
adoptions retain their owning-repository tasks and exact source/provider gates;
these public proofs do not satisfy operational migration contracts. The dated
[earlier checkpoint](PROTOCOL-CHECKPOINT-2026-09.md) remains historical evidence
and is not rewritten to pretend these implementations existed at that time.
