# Complex planning import conformance

`TASK.migration.loom` tests a general compatibility boundary using an independently
authored fictional legacy specimen. It does not migrate Loom or implement Loom's
release gates. Operational adoption and its preserving adapter remain consumer-owned.

The [source index](../conformance/src/test/resources/complex-planning/sources.json)
pins thirteen public source files at commit
`445b637b02c4451977e015af5d6301132f8db75c`, their SHA-256 digests and the test-local
`conformance-planning-indexes/1.0.0` adapter. `specimen.planning/1` is deliberately
non-native. It contains six tasks, three roadmaps and three epics. One task belongs
to two roadmaps and three epics; an empty roadmap is valid. `W-1` and `W-01` remain
different identities. Only explicit `after` relationships become prerequisites.
The tests pin the resulting manifest identity to
`sha256:7c6844313efb6601b1839356790abaabcd44e40df1369c17b62725ac55f9acee`.

Four tasks claim historical completion; one still has an unchecked criterion.
The manifest preserves those exact source assertions and distinct legacy contract
identities. Import creates no native receipts and makes no historical claim current.
Topological, explicit mapping reviews establish current contract/input correspondence.
Historical planning completion is retained as narrative provenance: imported scopes
remain active draft-1 records without invented acceptance criteria or assessments.

The same file ledger then explicitly adopts audited planning history. Amendments,
archival and assessments preserve task bytes, task history, receipts and import
witnesses. Roadmap archival preserves membership and task eligibility. An epic can
be explicitly assessed while members remain open; closing all members does not
automatically accept or archive any scope. Assessment evidence binds its exact
scope revision, member contracts and transitive inputs, including prerequisites
outside the scope. Later changes retain the earlier evidence as historical and
require an explicit fresh assessment after necessary task reviews.

Shared JVM/Native Image cases exercise reconstruction, stale CAS, overlapping
frontier filters, imported identity/provenance, unverified closure, amendments,
archival, scope assessments and direct/transitive drift. The JVM corpus emits a
canonical imported file preimage for the separate packaged process parity driver;
both executables then inspect and mutate identical disposable state. This adapter
is not registered in the product CLI. No runtime reflection allowance, migration
shortcut or alternate lifecycle implementation is introduced.

Unknown structural source fields, host/operator/claim/release-gate requirements,
unsupported lifecycle states, cycles, dangling membership and a misleading native
dialect label are rejected. Optional annotations remain typed opaque data. This
specimen supplies no evidence for executing stateful consumer policies, hydrating
workspaces, contacting providers, freezing v1 or deploying a service.
