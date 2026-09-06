# 0.3.0-alpha.1 semantic hardening

Native v1 remains unfrozen. The four milestones are revision/currency semantics,
self-hosting, distribution/license hardening, and a verified consumer release.
Product migrations and service implementation follow these milestones.

## Decisions under test

Task identity, immutable task revision, semantic contract, and whole-ledger CAS
revision are different identities. A task revision stores its parent, full typed
record, observed dependencies and explicit reconciliation, if any. HEAD selects
one revision. File storage journals HEAD and new immutable objects together.

Currency is independent of open/closed lifecycle. It is current, affected, or
unresolved. A dependency observes the upstream task revision, contract digest,
and transitive input digest. That last digest includes the upstream contract and
its prerequisite input digests. It keeps C affected after A changes and B is
reconciled without changing B's own contract. Reconciliation of B cannot silently
acknowledge C. Returning to exactly the same semantic inputs restores currency;
this is contract equivalence, not a comparison of wall-clock revision numbers.

Revalidation requires an explicit actor assertion with rationale and evidence,
the exact reviewed task HEAD, and exact current upstream observations. Unknown
or affected upstream inputs cannot be acknowledged as current. Unresolved,
revise, and successor outcomes record review but do not clear currency. Successor
records must exist and identify real follow-up work; creation is a separate CAS
seed operation. A material revision of a closed task preserves its closed state
and historical receipts, while requiring new revalidation of the changed
contract. No command reopens a historical task automatically.

`tasking/core-draft-1` retains its original digest projection and evidence
meaning. `tasking/core-draft-2` uses `taskctl.semantic-contract/2`, framed canonical
typed values and SHA-256. Title, source layout, comments, optional extension data,
and lifecycle are incidental. Bounded intent, prerequisites, requirements,
acceptance, named verification evidence requirements, activated extension data
and provider pins are contractual. Markdown soft wrapping and bullet/fence
spelling normalize; code examples remain literal contract content. Requirements
and acceptance order remains significant. No schema claims all prose is cosmetic.

Old native repositories remain readable with their original receipt semantics.
They have no invented historical observations. Explicit history adoption must
record the prior ledger revision and leaves unobserved edges unresolved.

The reducer owns all transitions and history/currency rules. Storage owns paths,
locking, atomic writes and recovery. No database, HTTP authority, or provider
escape from the reducer is introduced. Roadmaps and epics remain independent
planning indexes and never introduce prerequisite edges.

## Commands

All commands below accept `--repo PATH` and `--format json`. Repository-pinned
wrappers select their own repository regardless of the calling working directory.

```
taskctl status
taskctl affected [TASK.id]
taskctl show TASK.id
taskctl history TASK.id
taskctl revise TASK.id --file revised-record.json --expect-revision sha256:...
taskctl reconcile TASK.id --plan
taskctl reconcile TASK.id --file review.json --expect-revision sha256:...
```

`show` exposes the task HEAD and semantic contract, plus whole-ledger CAS revision.
`affected TASK.id` selects affected/unresolved work with that task in its cause
path. `status` includes current work. `history` shows immutable revisions and old
receipts. Currency does not corrupt the graph: doctor remains usable; tracked
affected/unresolved work is excluded from frontier and cannot close.

`reconcile --plan` is read-only and returns the exact HEAD and upstream observations
to review. A review file has this shape (copy identities from the actual plan):

```json
{
  "protocol": "taskctl.reconciliation/1",
  "classification": "actor-assertion",
  "task": "TASK.id",
  "reviewed_head": "sha256:<64 hex digits>",
  "observations": [],
  "outcome": "revalidated",
  "actor": "reviewer",
  "recorded_at": "2026-09-06T00:00:00Z",
  "rationale": "Why this contract is still satisfied against these inputs.",
  "evidence": {"integration": "Exact check/result and source revision."},
  "successor": null
}
```

Nonempty `observations` contain `upstream`, `revision`, `contract`, and `inputs`,
all copied from the inspected plan. Revalidated evidence must supply every named
`verification` requirement. The other outcomes are `revise`, `successor`, and
`unresolved`; none acknowledges the task as current. `successor` identifies an
already admitted distinct open task. Stale ledger revisions fail with exit 4;
invalid contracts/reviews with exit 2. Actor assertions are not independent test
attestations, and taskctl never executes the commands described in evidence.

Add `--plan` to `seed`, `revise`, `close`, `track`, or `reconcile --file` to validate
the operation and inspect exact file preimage/postimage digests before writing.
Writer locks/journals/staging files are separately listed. Applying still requires
the same ledger CAS revision. Read commands do not create any lock/cache/journal.

## Compatibility and adoption

`init` creates `taskctl.native/alpha2` history-backed repositories; `adopt` uses
the same initializer on existing code without tasking. Both accept `--plan`,
`--id`, `--toolchain`, and optional `--seed`. Adoption refuses existing tasking or
launcher collisions and preserves existing AGENTS.md; its generated operating
instructions go into `.agents/README.md` in that case. No Git command runs.

An alpha1 repository keeps its identity-only execution rules until explicit
`taskctl track --expect-revision ...` adopts immutable history. Its currency is
reported honestly as unresolved where old observations are absent. Tracking
does not reinterpret core-draft-1 receipt digests or invent prior revisions.
It atomically changes the repository protocol marker to alpha2 so older writers
cannot bypass the new history/currency rules.
`tasking/core-draft-2` adds `verification` and the new semantic projection. A
record changes dialect only through an explicit revision, with its old record
retained in history. Old taskctl binaries reject alpha2 repositories/records;
upgrade the pin when adopting these features. There is no repository-wide import.

Current task files are projections: changes to incidental source formatting and
optional annotations are tolerated, while edits to recorded semantic contracts
or lifecycle are rejected until made through the corresponding transition.

Native semantic Markdown is parsed as CommonMark. Fenced examples containing
headings, checklists or `---` remain literal code. The historical section adapter
now excludes backtick and tilde fences (up to three leading spaces), including
unclosed fences and shorter/incorrect closing markers, from headings and checklist
state. Existing exact-source provenance witnesses remain unchanged.
