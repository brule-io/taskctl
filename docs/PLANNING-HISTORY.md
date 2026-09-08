# Audited roadmap and epic history

This is development after `0.3.0-alpha.2`, not a released upgrade or a native-v1
freeze. A repository needs a binary that supports the contracts below before
opting in. Existing release pins, initialization and ancestral imports retain
their shipped storage and record dialects until explicitly upgraded.

Roadmaps and epics remain orthogonal planning indexes over tasks. This extension
of the native draft adds immutable amendments, explicit archival and scope-bound
assessments. Planning membership does not introduce task prerequisites, ownership,
lifecycle transitions or inherited task requirements. Archiving a roadmap leaves
its identity and members available to inspection; its member tasks retain their
ordinary readiness. Completing all members never asserts that a scope is accepted.

## Explicit adoption and identities

On a repository with tracked task history, inspect its exact revision and plan:

```text
taskctl doctor --format json
taskctl planning track --expect-revision sha256:... --plan
taskctl planning track --expect-revision sha256:...
taskctl planning history ROADMAP.delivery --format json
```

Adoption records the inspected ledger revision as the planning origin and creates
one baseline per existing roadmap/epic. It preserves each observed planning
record's original `tasking/planning-draft-1` dialect, including imported projections.
Existing import manifests retain the adapter/version, exact source revision,
original documents and historical evidence classification. A baseline records an
observation now; it does not invent amendments, assessments or native ancestry.
An empty planning universe is valid and can be tracked before records are seeded.

The storage marker becomes **`taskctl.native/alpha4`**. It requires both task and
planning history and can retain alpha3 imports. Older writers reject this marker.
The repository configuration envelope remains `taskctl.repository/alpha1`.
`init`, `adopt`, `track` for task history and `import apply` keep their existing
behavior; none silently opts a consumer into planning history.

`PlanningId` is the closed sum of the distinct validated `RoadmapId` and `EpicId`.
`PlanningRevisionId` and `PlanningAssessmentId` are separate validated nominal
types. A ledger `Revision` still identifies the entire storage snapshot for CAS.
Neither a planning revision nor an assessment is a task revision or a causal edge.

## Amendments and disposition

After adoption, newly seeded planning records and explicit amendments use
`tasking/planning-draft-2`. In addition to the old fields, it requires `disposition`
(`active` or `archived`) and an ordered `acceptance` list, which may be empty.
Old draft-1 encodings and interpretations remain unchanged and reject these new
fields. A new seed must be active; an ordinary amendment preserves disposition.

An amendment supplies the complete new record, the exact reviewed planning HEAD
and explicit actor evidence:

```json
{
  "protocol": "taskctl.planning-amendment/1",
  "reviewed_head": "sha256:<planning HEAD from planning history>",
  "record": {
    "protocol": "tasking/planning-draft-2",
    "kind": "roadmap",
    "id": "ROADMAP.delivery",
    "title": "Durable delivery",
    "intent": "Advance fulfillment and operational visibility.",
    "tasks": ["TASK.api", "TASK.receipt"],
    "disposition": "active",
    "acceptance": ["The complete fulfillment flow has been reviewed."],
    "required_extensions": [],
    "extensions": {}
  },
  "audit": {
    "protocol": "taskctl.planning-audit/1",
    "classification": "actor-assertion",
    "actor": "reviewer",
    "occurred_at": "2026-09-08T00:00:00Z",
    "reason": "Define the next bounded review scope.",
    "evidence": {"review": "Exact reviewed source/result locator."}
  }
}
```

```text
taskctl planning amend ROADMAP.delivery --file amendment.json --expect-revision sha256:... --plan
taskctl planning amend ROADMAP.delivery --file amendment.json --expect-revision sha256:...
```

Copy the complete inspected record when preparing an amendment, including opaque
extensions and required feature identities. The tool preserves the supplied typed
values; omission in a complete replacement is an authored change. The new revision
retains its parent, full record and audit. Actor, reason and evidence entries must
be nonblank. `occurred_at` uses the [validated timestamp profile](EVIDENCE-TIME.md);
actors cannot supply an authoritative `accepted_at`.

Archival/restoration use `planning archive` and `planning restore` with a
`taskctl.planning-disposition/1` file containing `planning`, `reviewed_head`,
`disposition` and the same `audit` object. The requested disposition must match the
command. These operations preserve every other field and all memberships; a
draft-1 record is explicitly promoted to draft-2 with empty criteria if necessary.
A repeated no-op disposition change is rejected. Neither operation asserts scope
acceptance or removes unavailable required capabilities.

## Assessments bind the exact reviewed scope

`taskctl planning assess EPIC.checkout --plan --format json` is a read-only input
plan. It returns the current planning HEAD, prior assessment HEAD, ordered criteria
and exact current member observations. Each observation contains a `task`, task
`revision`, semantic `contract` and transitive `inputs` digest. These are typed
planning observations, not additional prerequisite edges.

Submit a `taskctl.planning-assessment/1` file containing:

- `parent`: the prior assessment HEAD from the plan, or null for the first;
- `planning` and `reviewed_head`: the inspected scope identity and revision;
- `observations`: the complete current list from the plan;
- `outcome`: `accepted`, `not_accepted` or `unresolved`;
- `audit`: the explicit actor assertion described above;
- `criterion_evidence`: either no entries or one nonblank entry per ordered criterion.

```text
taskctl planning assess EPIC.checkout --file assessment.json --expect-revision sha256:... --plan
taskctl planning assess EPIC.checkout --file assessment.json --expect-revision sha256:...
```

An accepted assessment requires explicit nonempty scope criteria, evidence for
every criterion, exact current member observations, current task currency and
available required semantic capabilities. It does not require all members to be
closed: the authored criteria determine the assertion's meaning. Conversely, task
closure alone never creates an assessment. These are actor assertions; taskctl
checks their structure and binding without executing evidence text or independently
proving the actor's claim. The current minimal profile does not activate providers.
Unavailable required task/planning capabilities cannot be bypassed by acceptance.

Assessment **outcome** and **currency** are separate. A matching latest assessment
is current; a superseded assessment, changed planning HEAD or changed member
contract/input identity makes it historical. Unavailable capabilities or noncurrent
member currency make an otherwise matching assessment unresolved. All old evidence
is retained. A title-only task revision with identical contract/inputs does not
invalidate the scope assertion, although its original task HEAD remains a witness.
Changing scope membership, criteria, title or disposition changes the reviewed
planning HEAD and requires a new assessment to become current again.

`not_accepted` and `unresolved` outcomes never grant acceptance, even when their
input binding is current. A new assessment explicitly supersedes the prior HEAD;
it cannot silently erase or replace its immutable evidence.

## Storage, reads and mutation boundaries

New objects use independently versioned `taskctl.planning-revision/1`,
`taskctl.planning-assessment/1` and `taskctl.planning-history/1` contracts. They live
under `.agents/planning-history/{revisions,assessments}/<digest>.json` and
`heads.json`. Roots distinguish an observed baseline, native seed and exact import
manifest origin. The loader validates identities, parent chains, current record
projections, historical membership and observed task revision/contract references.
Direct record/history edits, missing objects and incorrect digest locators fail.

Every mutation requires whole-ledger CAS; amendment/disposition/assessment inputs
also name their exact reviewed planning HEAD. Stale ledger revisions fail with
exit 4, and invalid assertions with exit 2. Mutation plans disclose bounded writes.
Planning operations leave task records, task history and task receipts unchanged.
The existing transaction journal covers planning files, including interrupted-write
recovery and conflict refusal before any recovery write. No command runs Git,
network requests, environment probes or evidence commands.

`planning history` retains all scope revisions and assessments, including derived
currency. On audited repositories `snapshot` uses `taskctl.snapshot/alpha2` and
adds the complete `planning_history`; `doctor` uses `taskctl.doctor/alpha2` and
adds planning counts plus latest-assessment warnings. Historical/unresolved latest
assessments produce `health: attention`. Older repositories retain their alpha1
read contracts. The bounded `context` contract and task frontier remain unchanged.

The typed JSON writer also corrects a demonstrated loss of whole-valued decimals:
`DecimalValue(1E+3)` formerly serialized as the integer token `1000`. Decimal tokens
now retain their decimal type and exact `BigDecimal` scale, including negative and
zero scales. Fractional decimal and integer spellings and canonical hash algorithms
are unchanged. This is required for lossless opaque planning payloads, not numeric
coercion to accommodate a runtime.

## Verification boundary

The corpus includes immutable legacy planning byte witnesses captured with the
pre-change executable, unchanged legacy evidence/task revision hashes, typed ID
and audit guards, scope/input staleness, overlapping indexes, archival/restoration,
explicit negative assessments, exact opaque numbers and required-capability refusal.
File tests exercise cold reconstruction, bounded writes, stale CAS, tampering and
interrupted recovery. Packaged process cases repeat adoption, amendments, assessments,
archival, import preservation, read-only behavior, diagnostics and exit codes through
JVM and Native Image distributions. Release or closure claims require the actual
executed results; adding these cases does not itself establish cross-platform parity.
