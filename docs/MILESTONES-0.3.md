# 0.3.0-alpha.1 milestone evidence

The M1–M4 sections preserve alpha.1 evidence. The subsequent alpha.2 import release
and first completed consumer migration are recorded in
[the Fantastikt migration report](MIGRATION-FANTASTIKT.md) and
[alpha.2 publication proof](proof/productization/0.3.0-alpha.2/README.md).

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
repository-local. The initial development pin has been replaced with the published
native 0.3.0-alpha.1 lock, SHA-256
`d4c4893b97b5b3837c2d633236aff7e77fcb186b16133b4001d59bb74d92b2b7`.
A cold Windows consumer containing only tasking state and launchers passed 14
checks without a source checkout or host JVM. Version reporting leaves the cache
absent; release acquisition and then credential-free offline reads succeed without
changing repository bytes or mtimes. Evidence is in
`docs/proof/productization/0.3.0-alpha.1/cold-selfhost-windows.json`.

## M3 — distribution hardening

Complete. [Source CI](https://github.com/brule-io/taskctl/actions/runs/34052801534)
passed 93 source tests, the same 90 behavioral tests on JVM and Native Image,
90 packaged process comparisons, 23 consumer checks per implementation, and seven
self-host read commands per implementation on Windows x86_64, Linux x86_64 and
macOS aarch64. Native is the default lock; JVM remains an explicit reference lock.
Artifacts include Apache-2.0, third-party notices and the task/reconciliation guide.

[Fedora CI](https://github.com/brule-io/taskctl/actions/runs/34054585202) passed 11
install/runtime/upgrade/erase checks in a clean Fedora 44 container. The executable
matches the canonical Linux artifact byte for byte. Transactions made zero Internet
socket calls and preserved project/user files, mtimes and modes; cached repository
commands still worked after erase. The source RPM rebuilt offline with matching
payload and dependency metadata. Rpmlint: zero errors, five documented warnings.
The first test exposed the minimal container's `nodocs` default; the test now enables
the normal documentation payload without changing package behavior or system config.

```sh
taskctl --version
taskctl -V
taskctl version --format json
taskctl info --format json
gh release verify rpm-v0.3.0-alpha.1-1 --repo brule-io/taskctl
sudo dnf install ./taskctl-0.3.0-alpha.1-1.fc44.x86_64.rpm
taskctl help
sudo dnf remove taskctl
```

See [RPM usage](FEDORA-RPM.md) for download/checksum and init/upgrade commands, and
[RPM evidence](proof/productization/rpm-0.3.0-alpha.1-1/README.md) for exact identities.
These delivery changes add no protocol semantics. Native requires no host JVM;
the reference archive bundles Java. System `taskctl` and pinned `./taskctl` remain
independent. No license decision is outstanding; no CLA or extra trademark grant
was added. No COPR or official Fedora inclusion is claimed.

## M4 — release

Complete. [0.3.0-alpha.1](https://github.com/brule-io/taskctl/releases/tag/v0.3.0-alpha.1)
was published from clean source `73fa5fe61ad3ba2488800dc3456ad2838d2dbe71` after
[release transport CI](https://github.com/brule-io/taskctl/actions/runs/34054149302)
passed 23 consumer checks per implementation and 90 process comparisons on every
platform using real GitHub downloads. The signed immutable release binds all 28
assets and the source tag. The independent RPM companion release binds 11 assets
and packaging source `1b7a37e88538721f42bfb322dae37e423cc5f1f7`.

```sh
gh release verify v0.3.0-alpha.1 --repo brule-io/taskctl
./taskctl doctor
./taskctl context
./taskctl frontier
./taskctl status
```

Use `./taskctl.ps1` on Windows. The canonical repository commits the published
native toolchain lock; the cache on this host is populated. A cold reconstruction
outside the source checkout passed, and final read-only evidence follows closure
of the release tasks. [Publication proof](proof/productization/0.3.0-alpha.1/README.md)
includes exact hashes, raw signature verification, CI/transport/cold-consumer results
and the final frontier. The release is usable without source, Java or Gradle.

No additional protocol change was needed for release. Compatibility remains as
described under M1. Native v1 is still unfrozen, and repository visibility remains
private: fresh acquisition needs read access; a verified cache works offline without
credentials. Release signatures attest publication of provenance/evidence, not an
independent observation of every build step. No incompatible decision blocks this
alpha. The next planned task is Fantastikt migration, followed by Brule Message Bus
and the divergent specimen before the protocol checkpoint. No product migration
or service implementation was performed in M1–M4.
