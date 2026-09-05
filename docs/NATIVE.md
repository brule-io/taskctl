# Native repository contract (alpha)

The repository envelope is `taskctl.native/alpha1`; task records retain
`tasking/core-draft-1`, and roadmap/epic records retain `tasking/planning-draft-1`.
None is relabeled v1. The alpha gives the happy path an executable target while
group completion/archival and richer capabilities remain pre-freeze decisions.

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

Tasks carry intent, requirements, acceptance, explicit prerequisites, state,
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

Dependency edges have an internal `Dependency(upstream, observedContract)` shape.
An observed upstream contract digest is separate from both task identity and the
whole-ledger revision. Adapters can supply those bindings in a snapshot; the
shared frontier and transition reducer reject stale observations. Current
identity-only file records remain explicitly unbound. This does not manufacture
historical bindings or freeze a persisted edge schema; that evolution remains a
versioned native protocol decision.

File revisions identify exact authoritative file contents. They are optimistic
concurrency tokens, distinct from semantic task contract digests. Changing a
planning record changes a revision without changing an unchanged task contract.
Writes require the revision the caller inspected. Stale mutations fail with exit
4. Read commands do not create locks, caches or journals in the project.

## Plans and evidence

`seed --file PLAN --expect-revision REVISION` admits explicit records using
`taskctl.seed/alpha1`, an object with `contract`, `tasks`, `roadmaps` and `epics`.
New tasks must be open; duplicate identities, dangling references and cycles
are rejected. Initialization accepts the same optional seed contract.

`show TASK --format json` exposes its current `contract_digest`. A closure input:

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

`verify TASK --receipt FILE` checks supplied evidence against the current contract
and closure guards without writing or running project commands. `close` performs
the same validation under revision control, changes only the task state and adds
an immutable, content-addressed receipt under `.agents/receipts`. Assertions stay
labeled as assertions; taskctl does not independently prove they are true. Changing
acceptance invalidates old evidence for the new contract; the old receipt remains.
All tasks closed does not claim epic acceptance or close an ongoing roadmap.

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
`TASKCTL_OFFLINE=1` disallows acquisition. Human-authored changes should be followed
by `doctor`; richer amendment and planning-completion commands remain future work.
