# 0.3.0-alpha.1 milestone evidence

## M1 — semantic model

The candidate exposes immutable task revisions/HEAD, versioned semantic contracts,
observed dependencies, transitive currency, and explicit reconciliation. Closed
historical tasks remain closed and keep their receipts. `status`, `affected ID`,
`history ID`, `revise ID --file ... --expect-revision ...`, and `reconcile ID --plan`
make the state inspectable. See [the command contract](SEMANTIC-0.3.md) for exact
review inputs and mutation-plan commands.

Local verification: `./gradlew.bat check :cli:installDist` passes 93 source tests,
including a 24-level diamond graph, persistent history/recovery, stale revisions,
review evidence, fenced Markdown examples and type/storage architecture policies.
The aggregate JVM and Windows Native Image suites passed the same 90 behavioral
tests. `python scripts/parity_test.py` passed 90 packaged process comparisons.
`python scripts/bootstrap_test.py --kind jvm` and `--kind native` each passed 23
source-free Windows consumer checks, including offline cached reconciliation.
Local proofs are in `docs/proof/productization/0.3.0-alpha.1/local/`; development
artifact metadata explicitly records dirty source and does not claim release status.

Protocol changes are explicit: core-draft-2 and semantic-contract/2; history,
task-revision, transitive-input, and reconciliation version 1 envelopes; native
repository alpha2 fences older writers. Old core-draft-1 digests and historical
receipts retain their meanings. Alpha1 repositories adopt history only through
explicit `track`, which binds the original ledger revision and leaves missing
observations unresolved. Native v1 is unfrozen; no incompatible decision remains
pending for this alpha slice. Cross-platform release acceptance belongs to M3/M4.

## M2 — self-hosting

`./taskctl.ps1 doctor`, `context`, `frontier`, and `status` work from an unrelated
working directory through a cached standalone candidate. The actual repository
reports 22 tasks, four roadmaps and two epics. `adopt --plan`/`adopt` preserved the
existing contributor contract and source files. New file-ledger and packaged
consumer checks cover adoption refusal/preservation and bounded writes.

There is no additional task protocol change. The adoption receipt and graph are
repository-local; this development bootstrap uses a temporary local candidate pin.
The final published native lock and a cold consumer proof will replace it in M4.
The cached candidate runs without the source checkout or a host JVM. The initial
pin is not a claim of cross-platform release availability.

## M3 — distribution hardening

In progress: exact JVM/native corpus and process comparisons on Windows x86_64,
Linux x86_64 and macOS aarch64; Apache-2.0 in artifacts and metadata; Fedora RPM
from the canonical Linux artifact. Version aliases and release provenance remain
required. No distribution is accepted from compilation alone.

## M4 — release

Pending: clean-source canonical archives, real-transport smoke tests, immutable
release attestations, canonical-artifact RPM proof, and the final self-host pin.
The existing RPM pipeline consumes a signed published canonical release; its
independent companion release follows archive publication and does not gate the
tar.gz/wrapper channel. M3/M4 are complete only after both delivery paths and the
final cold self-host consumer pass.
