# Related ledger observations

This conformance specimen uses two independently authored fictional legacy
ledgers. Exact source files originate at public commit
`da0bd033a3cce21af5eeebeb78d70bbf069d4f16`; `sources.json` binds each byte digest.
The test-local `conformance-related-ledger` adapter is explicitly version `1.0.0`
and accepts only `specimen.related/1` with its exact bounded structure. Source
repository/revision, logical ledger, complete task identity, old contract, selected
adapter and resulting manifest are retained. A label never authorizes dropping an
unknown stateful restriction or treating historical source as native v1.

Each ledger contains W1 -> W2 -> W3. Both legitimately use the same native task
IDs and executable contracts. Their repository identities and import manifests
remain distinct. Historical `done` flags retain unchecked source checks and have
no fabricated native receipt; explicit mapping/input reviews precede native work.
No consumer checkout is read, hydrated or migrated by these tests.

## Manual observation boundary

A local observation task binds the external repository ID, task ID, observed
semantic contract and transitive input digest in its **core requirements**.
Exact source Git revision, ledger snapshot and task HEAD remain explicit witness
metadata. Local prerequisites stay local; the adapter does not create a remote
resolver or cross-repository transition authority.

The tests deliberately change an upstream ledger and prove the observing ledger
remains unchanged and locally current until an explicit refresh. Local currency
means current against its recorded observation, not fresh against external state.
An actor must recheck the external witness before relying on it. The fixture's
refresh is an explicit CAS revision followed by evidenced reconciliation.

An upstream root change can leave W2's own contract unchanged while changing its
transitive input digest. Refreshing that observation changes the local contractual
input and makes downstream work affected. Reviewing the local intermediate does
not silently acknowledge its consumers. Their explicit reviews retain every old
revision, import manifest and receipt. A cosmetic source HEAD change alone keeps
semantic currency and the originally observed dependency HEAD intact. Reusing the
same task ID from a different repository changes the observation contract.

Six shared JVM/native file-ledger cases cover these behaviors, source mapping,
stale CAS/review rejection, cold reconstruction and unchanged bytes/mtimes in the
other ledger and unrelated product files. Existing packaged process parity remains
the distribution check; this work adds no command or implicit Git/network operation.

## Reviewed import identity guard

The source witness at `87afe08` exposed a separate admission defect: mutating a
borrowed file map after creating its review could produce an import plan using a
cached manifest ID for different bytes. The completed
[failing baseline](proof/related-ledger/import-borrowing-red.log) retains the result.
Kotlin read-only collections do not guarantee that the caller has no mutable alias.

Import admission now re-establishes the typed serialization boundary in the shared
reducer and history validation. Decoding checks the complete current source,
provenance and projection; its recomputed identity must equal both the manifest
identity and explicit review. Three shared regressions cover changed source bytes,
projected records/provenance and the valid path with a newly constructed manifest
and new review. Invalid admission fails before an initialization plan or ledger
writes. Existing valid manifest IDs and historical receipt bytes remain unchanged.
This is an admission guard, not a claim of deep immutability for every Kotlin
collection or support for concurrently mutating input objects during a call.

The public task is compatibility evidence only. Consumer adoption and preserving
consumer-specific providers remain with their owning repositories. No DAEMON
hydration, operational authority, service deployment or native-v1 freeze follows.
