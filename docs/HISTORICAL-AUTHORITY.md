# Historical source authority and later native contracts

This conformance slice uses the already preserved DAEMON witnesses in
`conformance/src/test/resources/daemon`, with their original source index. It does
not hydrate a workspace, migrate operational DAEMON tasks, or turn old prose into
a retrospective native receipt.

The two OS records retain their distinct full `TASK.process.006.*` identities at
source revision `68e32a8b87932f6d69d34cc28f50b9b4102f1a79`. The net triage record
retains `TASK.M2.nginx` at `46c13b1d3a5223096d9d90a2ea0a4849bc9e7bea`.
Their selected preview adapters remain `daemon-workspace-prose/2026` and
`daemon-net-triage/2026`, both version `0.1.0-dev.1`. Exact paths and byte hashes
remain in `sources.json`. The source repositories retain their own visibility.

The fixture catalog scopes lookup by source repository and complete identity.
It refuses a numeric-prefix abbreviation, a missing authority and a different
authority. Per-source preview manifests remain distinct; this test-local catalog
does not add an automatic cross-repository resolver to core. Source bytes,
adapter/version and exact Git revision are checked by the existing bounded
preview adapter. Historical `closed` state, unchecked acceptance and narrative
remain `historical-narrative-unverified`, with no native source protocol.

The operational specimen creates a **new archive-observation task**. Its core
requirements bind the exact source repository, full identity, path, revision,
byte digest, selected adapter and historical contract digest. Optional namespaced
data preserves the source metadata and closure narrative. This task's acceptance
is limited to cataloging those assertions; it does not assert that their original
business claims were independently proved. There is no import admission for the
old business work in this new ledger.

A historical contract digest cannot satisfy a receipt for that new task. A new
actor assertion may close the archive-observation contract. Strengthening the
contract later to require independent business verification makes that receipt
historical evidence for its original contract only. The original receipt and
revisions survive unchanged, the task remains lifecycle-closed but affected, and
an explicit unresolved review holds downstream work. No evidence is invented to
clear that hold.

Six shared JVM/native cases cover scoped identity, exact provenance/classification,
old-contract receipt refusal, later contract changes and unresolved transitive
currency, optional-versus-required authority annotations, stale CAS/review refusal,
cold reconstruction and unrelated file bytes/mtimes. An opaque annotation cannot
grant execution authority. Requiring an unavailable provider blocks readiness and
closure while preserving inspectable data. All writes are confined to disposable
specimens; source Git repositories and live services are not accessed.

This closes a public compatibility proof only after its conformance and parity
evidence is recorded. Consumer-specific authority providers, source hydration,
copied-tool retirement, adoption and native-v1 freeze remain separate decisions.
