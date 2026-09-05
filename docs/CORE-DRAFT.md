# Core invariants and typed values

This executable experiment uses `tasking/core-draft-1`. It does not freeze or
accept a native v1 protocol. Its purpose is to make key architectural decisions
testable while the historical adapter supplies the first working CLI.

Roadmaps and epics are now core concepts alongside tasks. Optional record
presence is different from optional protocol meaning. The separate
`tasking/planning-draft-1` record codec and `DraftUniverse` model extend the draft
without changing existing `tasking/core-draft-1` task contracts. See
[the evidence and native-v1 design correction](PLANNING-MODEL.md).

## Native task and planning universe

Tasks alone represent atomic executable state transitions in the causal DAG.
Roadmaps are durable named lines of advance over tasks. Epics are bounded
feature/capability scopes associated with tasks. These are three orthogonal
indexes: task prerequisites, roadmap membership, and epic association.

A task can be referenced by zero or more roadmaps and epics. An epic may span
roadmaps; a roadmap may contain work for several epics. Native core does not
persist a roadmap's epic owner or require a tree. A repository can have no
roadmaps, no epics, or empty named planning records. Profile-specific admission
requirements remain explicit policy.

Membership is authored once on each planning record; reverse task membership
and roadmap/epic intersections are derived. Roadmap task order is presentation,
not a prerequisite edge. `DraftUniverse.frontier` shares deterministic task
evaluation with closure and filters only after evaluating the full graph.
Unmet prerequisites and cycles outside the selected view cannot be hidden by
the selection. Strong task/roadmap/epic ID types distinguish index operations.

Regrouping, roadmap reordering, and epic scope edits change the universe snapshot.
They do not automatically amend member tasks' acceptance or invalidate evidence
for their unchanged executable contracts. Nor do completed tasks automatically
prove an epic's scope or close a roadmap. Explicit group completion/archival and
group evidence binding remain decisions to complete before native v1 freezes.

## Typed decoding boundary

Protocol values form a sealed algebra:

```kotlin
sealed interface Value
data class ObjectValue(val fields: Map<String, Value>) : Value
data class ArrayValue(val values: List<Value>) : Value
data class StringValue(val value: String) : Value
data class IntegerValue(val value: BigInteger) : Value
data class DecimalValue(val value: BigDecimal) : Value
data class BooleanValue(val value: Boolean) : Value
data object NullValue : Value
```

SnakeYAML's composed node tree is quarantined in `YamlValues`. The decoder checks
node/tag kinds, string mapping keys, duplicates, aliases, and scalar syntax while
constructing typed values. It does not call the library's generic object loader.
No unchecked cast or top-type container exists in that path. Integer and decimal
precision are retained; there is no conversion through `Double`.

`Canonical`, `Json`, Markdown semantic projections, extension payloads, snapshots,
and import manifests accept this algebra. Exhaustive `when` expressions make
adding a new value kind a compile-time obligation at each encoder.

`ArchitecturePolicyTest` uses Kotlin PSI to reject explicit `Any`/`Any?`, generic
uses of those types, top-type import aliases, and warning suppression outside the
reviewed codec. It rejects suppression aliases and constant-valued suppression
arguments too. Comments and example strings do not trigger the rule. Warnings
are errors during Kotlin compilation. CI invokes `check`, which includes this
policy and its positive/negative fixtures.

`@UntypedBoundary("reason")` is eligible for an escape only in the explicitly
allowlisted `YamlValues.kt` codec and only inside function bodies; public
signatures remain typed. The current decoder needs no escape. Adding another
allowlisted boundary requires a deliberate policy edit. This is a syntax policy
plus typed API enforcement, not a claim of whole-program inferred-type analysis
or an external-provider sandbox.

## Core namespace and extension authority

Core owns top-level fields. Specialized payloads belong under a versioned,
namespaced `extensions` mapping, with separately readable `required_extensions`.
Unknown core fields fail. Unknown optional extension values remain uninterpreted.
The source-preserving document retains their original spelling, ordering,
comments, whitespace, numeric representation, and Unicode during a core title
edit. Semantic decoding and lossless source preservation are separate concerns.

Installing a provider does not activate it. A profile binds active identities to
exact pins. Required missing, unpinned, or mismatched support permits inspection
but blocks the tested closure decision. A schema-only validator is still a pinned
semantic capability when policy makes its constraints executable.

Providers contribute validation/readiness blockers, prerequisites, evidence
requirements, and evidence verification. Core still checks identity uniqueness,
missing dependencies, contributed-edge cycles, task lifecycle, prerequisites,
contract binding, and evidence presence. Providers cannot supply an overriding
"allow closure" result. The environment-probe interface is separate and is not
called by deterministic evaluation. Loading arbitrary provider binaries and the
complete production evidence lifecycle are future work.

## Semantic contract identities

`tasking/core-draft-1/semantic-contract/1` hashes a typed semantic projection:
identity, title, bounded intent, prerequisites, requirements, acceptance, required
feature identities, active profile pins, and their semantic extension payloads.
Lifecycle state and optional annotations do not alter that contract. Active
capability changes do. Missing semantic payloads retain an explicit null value.

Markdown is parsed into a CommonMark AST. Ordinary wrapping, bullet marker choice,
and checklist progress are presentation. Nested requirements, code, link targets,
and hard line breaks remain meaningful. Progress markers are stripped only from
actual checklist nodes, never from code examples. This is a defined structural
canonicalization, not an attempt to prove natural-language equivalence.

The draft binary-independent framing uses type tags, string UTF-8 byte lengths,
array order, and JVM ordinal ordering of object keys. Integer and decimal types
are distinct; decimal trailing zeros are normalized. A domain/version prefix
prevents identities from being reused under a different canonicalization rule.
This is not labeled RFC 8785.

A receipt identifies the task and contract it addresses. An old receipt continues
to address that old contract after requirements change, while failing the current
contract check. A matching digest is not itself proof that an evidence assertion
is true. Full native receipt storage, actor/time/operation binding, and production
transition persistence remain part of the later protocol milestone.

The legacy adapter's preview digest uses its own versioned domain. Its exact
record-snapshot digest hashes authored record bytes and locations for stale-write
detection. That byte snapshot is deliberately distinct from a semantic contract.

## Historical import provenance

The DAEMON preview adapters require explicit adapter/version, exact 40-character
source revision, repository/path, and verified source SHA-256. Their manifest
records resulting full task identities and semantic contract identities and has
its own deterministic manifest ID. Changed provenance changes the manifest even
when the semantic contract is identical.

Two canonical OS tasks sharing `TASK.process.006` remain distinct because the
complete filename stem supplies the historical identity. Net's `TASK.M2.nginx`
retains its closed placement and narrative closure while preserving the original
unchecked criteria. It is labeled `historical-narrative-unverified` with no native
protocol or fabricated native receipt. The selected previews are bounded to the
sampled structures; they are not general DAEMON migration adapters.

Before freezing native v1, broaden the corpus to the remaining DAEMON dialects,
Loom realization/release rules, both incompatible Dropzone forms, and complete
evidence/ownership transitions. Core promotion remains exceptional, but the test
is uniform reconstruction of durable tasking semantics across the system's
supported uses, not mandatory occurrence in every valid graph. Roadmaps and
epics satisfy that test; primary-lane selection, ownership, scheduling,
concurrency, release gates and realization rules remain capabilities/policy.
