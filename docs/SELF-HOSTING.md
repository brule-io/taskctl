# taskctl develops through taskctl

The initial canonical adoption had 22 real tasks, four durable roadmaps, and two
cross-roadmap epics. The graph covers semantic hardening, delivery, first migration
pressure tests, the protocol checkpoint, later complex migrations, and the bounded
remote/IDL pressure tests. Dependencies gate the later phases. Planning membership
does not create dependency edges.

Start with the repository wrapper:

```sh
./taskctl doctor
./taskctl context
./taskctl frontier
./taskctl status
./taskctl roadmap ROADMAP.delivery
./taskctl epic EPIC.alpha3
```

On Windows, use `./taskctl.ps1` or `./taskctl.bat`. These are the canonical wrapper
templates. Implementation remains outside the repository and is selected by the
exact `.taskctl/toolchain.lock`. No copied Kotlin/Python task implementation is
introduced into `.agents`.

The initial adoption used the packaged 0.3 candidate and its verified local cache,
preserving the existing AGENTS.md. The initial candidate archive SHA-256 was
`bed2012c86030012e150f7340d9da163d7c058800d2db74767efca62e7e228e1`.
This was an explicitly dirty development build, not a published release claim.
The release milestone replaced that temporary local pin with the canonical
released native lock from v0.3.0-alpha.1. That historical lock's SHA-256 is
`d4c4893b97b5b3837c2d633236aff7e77fcb186b16133b4001d59bb74d92b2b7`, and the tool's
source is `73fa5fe61ad3ba2488800dc3456ad2838d2dbe71`.
That pin necessarily follows the source commit used to build
the first compatible released artifact; its publication and dogfood receipts name
the separate source/release/pin identities. No self-referential artifact hash is
claimed.

The [publication and cold-consumer proof](proof/productization/0.3.0-alpha.1/README.md)
records an initially empty cache, no source checkout or host JVM, and successful
credential-free offline inspection after acquiring the released native artifact.
Those original downloads required an explicit contents-read credential; no
credential was committed. Since public publication on 2026-09-07, the same pins
acquire releases anonymously. The default cache on this host has also been populated.

Task closure is recorded only after its acceptance is met. Receipts describe
actual checks and source identities as actor assertions. Distribution parity,
release transport, RPM transaction tests and final consumer acceptance remain
separate evidence requirements. Public conformance belongs here; operational
consumer migrations retain their owning repositories and private coordination.

The current pin is the published native **v0.3.0-alpha.3** lock, SHA-256
`b90b8d4af8bc16e7df54f03d42dc52f45ff268ed3dde44978ccef5d1ab91cbb3`, from source
`084cea04271764f6d666c0db6aa0da517abce7a6`. Its
[publication and acceptance proof](proof/productization/0.3.0-alpha.3/README.md)
records all-platform JVM/native behavior, real-release transport, an anonymous
source-free consumer and the producer pin update with preserved task identities.

At the historical Fantastikt handoff, 12 tasks were closed and all 22 were current;
the frontier then selected `TASK.migration.brule`.
[Consumer migration evidence](MIGRATION-FANTASTIKT.md) includes normal canonical
usage before copied-tool retirement and reconstruction from committed files.
The preserved observations distinguish progress during release and migration from
the [final producer graph](proof/migration/fantastikt-0.3/producer-final.json).

Public Apache-2.0 publication is recorded in the closed `TASK.release.public`.
That historical checkpoint had 23 current tasks and 13 closures. Its
[public-access proof](proof/publication/public-2026-09-07/README.md)
records anonymous native/RPM downloads, bootstrap and hosted consumer verification.

The alpha.3 follow-through closes all 35 admitted public tasks with current
currency, including the separate RPM and producer acceptance. Use `frontier` and
`status` for subsequent work; these dated counts are not a live task index.
Native-v1 remains an unfrozen candidate.
