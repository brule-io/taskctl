# Native repository contract (alpha)

The 0.3 initializer creates history-backed
`taskctl.native/alpha2` repositories. New tasks use `tasking/core-draft-2`;
roadmap/epic records use `tasking/planning-draft-1`. The configuration envelope
itself remains `taskctl.repository/alpha1`. These are separate versioned contracts,
and none is native v1. [SEMANTIC-0.3.md](SEMANTIC-0.3.md) documents the canonical
semantic projection and exact revision/reconciliation commands.

Existing `taskctl.native/alpha1` repositories and `tasking/core-draft-1` records
remain readable with their original meanings. Explicit `track` adopts history
and changes the repository marker to alpha2; it does not change old record dialects,
invent missing observations, or relabel old evidence. Older writers reject alpha2.
Planning history, group completion/archival and persisted provider capabilities
remain [pre-freeze decisions](PRE-V1.md).

Reviewed ancestral import in 0.3.0-alpha.2 uses `taskctl.native/alpha3`, with
content-addressed source manifests and origin-bearing revision/2 objects. Older
writers reject this marker. Historical closures remain separate from native
receipts and current review assertions. See [IMPORT.md](IMPORT.md); ordinary
initialization and existing revision/contract digests retain their meanings.

`.agents/config.toml` declares repository identity, protocol and profile. The
initial `minimal/alpha1` profile has no behavioral providers. `policy.toml` names
the same profile. These alpha TOML contracts deliberately accept only their
documented quoted scalar fields; unknown fields are errors.

Task, roadmap and epic documents live under `.agents/tasks`, `.agents/roadmaps`
and `.agents/epics`. These directories may be empty or absent in a clean checkout.
The generated YAML files use JSON syntax, a strict YAML subset with exact typed
values. Authored YAML is supported. Locators combine a readable identity prefix
and a hash; exact IDs inside records are authoritative. Do not infer identity or
state from a filename. File reads reject symbolic links in the ledger path.

Tasks carry intent, requirements, acceptance, named verification requirements,
explicit prerequisites, state,
`required_extensions` and `extensions`. Roadmap member order is presentation;
epics associate task sets. Membership does not add causal edges. The entire task
graph is validated before a frontier is filtered by roadmap/epic.

## Ledger seam

Core defines the internal `TaskLedger` interface: snapshot, task lookup, frontier
query and `apply(expectedRevision, transition)`. `FileTaskLedger` owns layout,
consistent reads, a cooperative writer lock and recoverable bounded writes.
`LedgerTransitions` is the shared pure reducer. An in-memory adapter exercises
the same operations in conformance; no service or HTTP adapter is implemented.
The interface is not a supported public Kotlin API in this alpha.

Native `TaskId`, `RoadmapId`, `EpicId`, `Revision`, `TaskRevisionId`, `ContractDigest`
`InputDigest` and `ImportId` have private
constructors and symmetric `parse` / `parseOrThrow` boundaries. Task records,
prerequisite edges and receipts carry nominal types, not interchangeable strings.
`RecordId` is the closed sum of task/roadmap/epic identities for mixed results.
Native record IDs have their kind prefix and preserve the complete case-sensitive
suffix (`TASK.process.006.workflow-canon-backport`, `TASK.M2.nginx`). They do not
inherit the legacy adapter's three-digit grammar. Digests/revisions admit exact
SHA-256 identities. Source archaeology retains raw source strings until admission.
The architecture gate rejects reopening value-class constructors.

Dependency observations use `Dependency(upstream, observedContract,
observedRevision, observedInputs)`. A task record's `requires` names stable IDs;
its immutable revision stores the observed upstream task revisions, contract
digests and transitive input digests. These identities serve different purposes:

| Identity | Meaning |
| --- | --- |
| `TaskId` | Stable task identity |
| `TaskRevisionId` | Immutable record, observations, parent and review |
| `ContractDigest` | Canonical semantic task contract |
| `InputDigest` | Contract plus transitive prerequisite inputs |
| Ledger `Revision` | Exact snapshot used for optimistic concurrency |

`TaskHistory` selects HEADs and retains immutable revisions. The file adapter
persists `.agents/history/heads.json` and content-addressed objects under
`.agents/history/revisions/`. It validates revision identities, references,
projections and historical evidence. Missing old observations remain unresolved.
An upstream semantic change affects downstream work even when an intermediate
task's own contract is unchanged. Lifecycle and currency are independent: closed
tasks retain their old receipts while affected by current inputs. `status`,
`affected`, `history` and `reconcile --plan` expose the explanation and identities.

File-ledger revisions identify exact authoritative file contents. They are optimistic
concurrency tokens, distinct from semantic task contract digests. Changing a
planning record changes a revision without changing an unchanged task contract.
Writes require the revision the caller inspected. Stale mutations fail with exit
4. This conservative whole-ledger CAS policy belongs to the file adapter; a future
remote adapter's conflict granularity is a separate contract decision. Read commands
do not create locks, caches or journals in the project.

## Plans and evidence

`seed --file PLAN --expect-revision REVISION` admits explicit records using
`taskctl.seed/alpha1`, an object with `contract`, `tasks`, `roadmaps` and `epics`.
New tasks must be open; duplicate identities, dangling references and cycles
are rejected. Initialization accepts the same optional seed contract.

`show TASK --format json` exposes its `contract_digest`, task `head`, ledger
`revision`, lifecycle and currency. A closure input:

```json
{
  "protocol": "taskctl.receipt/alpha1",
  "classification": "actor-assertion",
  "task": "TASK.api",
  "contract": "sha256:<digest from show>",
  "actor": "your-name",
  "recorded_at": "2026-09-05T00:00:00Z",
  "evidence": {"integration": "Observed result and durable evidence locator"}
}
```

`verify TASK --receipt FILE` checks supplied evidence against the current contract,
named verification requirements, currency and closure guards without writing or
running project commands. `close TASK --receipt FILE --expect-revision REVISION`
performs the same validation under revision control, changes task state, appends
an immutable task revision and adds a content-addressed receipt under
`.agents/receipts`. Assertions stay
labeled as assertions; taskctl does not independently prove they are true. Changing
acceptance invalidates old evidence for the new contract; the old receipt remains.
All tasks closed does not claim epic acceptance or close an ongoing roadmap.

Use `revise TASK --file RECORD --expect-revision REVISION` for material contract
changes; lifecycle cannot change through revision. Use `reconcile TASK --plan`
to inspect exact HEAD and current input observations, then submit an explicit
review through `reconcile TASK --file REVIEW --expect-revision REVISION`.
Only an evidenced `revalidated` outcome can acknowledge current inputs; `revise`,
`successor` and `unresolved` outcomes retain unresolved currency. Reviews and old
receipts remain distinct historical evidence. `recorded_at` currently requires a
nonblank actor-supplied string; typed occurrence/acceptance time is not yet promised.

Add `--plan` to a mutating command to inspect exact bounded writes and preimage/
postimage digests. Applying still requires the inspected ledger revision.

## Recovery and boundary

Task transitions journal before/after images under `.agents/runtime`, then finish
the bounded writes. An interrupted transaction blocks inspection until explicit
`recover`. Recovery accepts only unchanged preimages or already-applied images;
an unrelated edit causes a conflict. Initialization has a separate bounded journal
under `.taskctl` and the same `recover` entry point. Never erase a conflicting
journal to force success. These guarantees cover process interruption on supported
local filesystems, not arbitrary hardware failure or distributed clone consensus.

No native operation invokes Git. No native read invokes a network request or
external probe. The wrapper may acquire its pinned archive into an external cache;
`TASKCTL_OFFLINE=1` disallows acquisition. Task files are current projections:
incidental formatting and optional annotations may change without invalidating
HEAD, but direct semantic/lifecycle edits are rejected. Use task transitions for
those changes and `doctor` to check the result. Planning records remain add-only
through the CLI; planning amendments/audit and completion need a future contract.
