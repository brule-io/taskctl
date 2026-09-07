# Consumer-owned adoption and public compatibility work

The public taskctl repository owns the protocol, reference implementation,
distributions, reusable adapters and conformance corpus. An individual
repository owns its operational adoption task, local conversion code, source
archive, review, integration changes, copied-tool retirement and closure evidence.
General defects discovered during adoption belong here with a bounded reproducer.
Private consumer source and operational evidence keep their original visibility.

Brule Message Bus exercised this boundary using the existing released
`0.3.0-alpha.2` typed import/bootstrap APIs and canonical native wrapper. Its
converter, 66 archived source blobs and full evidence are in the consumer's
`docs/tasking` directory, reviewed in
[consumer PR #25](https://github.com/brule-io/brule-message-bus/pull/25)
(access follows that repository's permissions). No producer runtime or protocol
change was needed. The PR merged as
`b329c4a08f613c52109a89c48824ffe1f392b5ab` after
[consumer CI](https://github.com/brule-io/brule-message-bus/actions/runs/34156371278)
passed repository verification, service packaging and container checks.
The consumer migration is closed with native evidence;
separate product work remains in that consumer ledger.

Observed consumer validation: five adapter tests; 226 baseline and integrated
pre-retirement tests; 182 retained tests after retiring 44 copied task-tool tests.
All 172 product and ten ADR tests remain. Published native cold acquisition,
offline cached reads, bounded CAS writes/rejections, verify/close, and clean-clone
reconstruction passed. These are recorded actor observations, not a claim that
every historical deployment or provider operation was rerun. The producer's
historical Brule tracking receipt points to the consumer evidence.

## Existing backlog and causal gates

The owner requested this ownership correction on 2026-09-07. Five still-open
consumer-specific contracts have been explicitly revised into public product
compatibility work. Stable identities retain their historical names; `history`
exposes the old contracts and their exact parent revisions. No deferred consumer
migration was marked complete, deleted, renamed, or falsely handed off as done.

| Historical identity | Current public responsibility |
| --- | --- |
| `TASK.migration.divergent` | A third, structurally divergent import/currency specimen in isolation |
| `TASK.migration.loom` | Complex orthogonal planning and historical-evidence conformance |
| `TASK.migration.dropzone` | Workspace/environment capability boundaries and preservation |
| `TASK.migration.daemon-descendants` | Related-ledger provenance and revision-observation conformance |
| `TASK.migration.daemon-meta` | Legacy authority identities and historical-contract conformance |

All five were open at the ownership correction. The existing prerequisite edges
are unchanged: divergent
compatibility precedes the protocol checkpoint; planning and capability cases
precede related-ledger cases, which precede historical-authority cases. The
native-v1 assessment still requires this chain and IDL/remote evidence. The
remote kernel still requires the explicit protocol checkpoint. An updated plan
is not evidence that its tests passed, nor approval to freeze the protocol.

Consumer migrations beyond those already completed remain deferred. Admit the
operational task in the target repository when that migration is selected; this
scope correction does not create an untracked parallel task queue or authorize
changes to those repositories. Historical consumer-specific planning memberships
remain as provenance; membership never creates transition authority.

[Revision audit](proof/consumer-ownership/revisions.json) records the five
contract changes and explicit reviews of their still-open dependent plans.
Historical receipts and revisions were preserved. Current task records use
CAS transitions; no HEAD or immutable history was edited directly.

[Validation](proof/consumer-ownership/validation.json) confirms unchanged
prerequisite edges, previously closed task records and historical evidence.
Seven read commands produced identical results through the existing packaged
[native](proof/consumer-ownership/selfhost-native.json) and
[JVM](proof/consumer-ownership/selfhost-jvm.json) implementations without writes.
`TASK.product.consumer-ownership` records and closes this organizational change.
The divergent compatibility specimen has since passed its bounded import and
three-platform JVM/native checks; see [the evidence](proof/divergent-import/README.md).
The next public frontier is the protocol checkpoint. The four later compatibility
tasks remain open, and unsupported stateful consumer adoptions remain gated.
