# External implementation boundary

The authorized first slice runs an exact historical Fantastikt ledger through a
separately built and checksum-pinned `taskctl`. Task records, receipts, policy,
and durable state remain in the repository they describe. Implementation sources
and build dependencies live here.

## Scope and compatibility

The named adapter is `fantastikt-loom-agent-2026`, version `0.1.0-dev.1`, extracted
from Fantastikt commit `b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4`.
Its original Kotlin packages intentionally remain recognizable. The extraction
includes focused parser, graph, lifecycle, expected-frontier and transaction
tests. ADR tooling is outside this slice. `GitClient` exists only in test sources,
where it observes disposable repositories; it is absent from runtime libraries.

The original CLI is independently compiled from exact donor objects in
`.proof/oracle`. The external implementation is compared against that oracle,
not against an expected output manufactured by the external implementation.

Historical grammar remains strict: fields are ordered; dependencies use the
donor's block-list representation; task identities follow that adapter's grammar.
The legacy `realizes` field remains a legacy field. It has not been promoted into
the native core. Every command selects the adapter explicitly, normally through
the wrapper lock. A schema label alone never selects an import dialect.

The following intentional differences are tested:

1. Read commands create no consumer lock file. If the persistent writer lock
   exists, readers open it read-only and acquire a shared lock. If the first
   writer appears during a read, the result is discarded and retried under the
   lock. Writers retain exclusive locking and journaled recovery.
2. Ledger links that redirect paths are rejected. This is a cooperating local
   filesystem protocol, not protection against a hostile process racing filesystem
   mutations or coordination across independent Git clones.
3. `snapshot` emits a typed JSON projection and two distinct identities: a
   semantic snapshot identifier and an exact authored-record snapshot identifier.
4. Optional `--expect-snapshot` guards task and roadmap closure under the write
   lock. Interrupted closure reconstructs the original snapshot using its original
   record/backup. If that preimage is unavailable, guarded recovery fails explicitly.

## Write contract

Read commands (`info`, `doctor`, `list`, `show`, `frontier`, `plan`, `snapshot`)
leave consumer file contents and modification times unchanged. Bootstrap may
populate its separately configured cache. Reads do not execute product code,
invoke Git, build the consumer, or contact provider services.

A task closure may modify only:

```text
.agents/.lock
.agents/tasks/open/<selected-record>.md
.agents/tasks/closed/<selected-record>.md
.agents/.taskctl-close-<selected-ref>.closed
.agents/.taskctl-close-<selected-ref>.open
.agents/.taskctl-close-<selected-ref>.txn
.agents/.taskctl-close-<selected-ref>.rollback
```

Roadmap closure uses the corresponding roadmap directories and selected roadmap
reference. Atomic writing may temporarily create `.<filename>.<UUID>.tmp` files
beside those files. Required parent directories may be created. Completed
operations clean transaction artifacts; interrupted operations retain recoverable
state. A rejected mutation may create the persistent `.agents/.lock` before its
validation fails, but must not alter authored task/source/Git records.

The tool does not implicitly stage, commit, checkout, reset, fetch, or push. The
proof harness explicitly initializes disposable Git repositories, stages and
modifies unrelated sentinel files, then checks that runtime commands preserve
them and `.git` bytes. This is the meaning of source-control independence; task
mutations are expected to change the bounded `.agents` paths above.

Legacy receipts retain their original format and meaning. The proof never adds a
native semantic digest to an old receipt and never implies that its recorded
verification command was executed by `taskctl`.

## Distribution

`scripts/package.py` builds a Java 21 runtime image and packages runtime files,
application libraries, distribution identity, and a per-file SHA-256 manifest.
Windows x64 and Linux x64 are the tested targets. Archives are local proof
artifacts, not published releases.

The consumer lock is a strict, non-executable properties file:

```properties
lockFormat=1
toolVersion=0.1.0-dev.1
wrapperVersion=1
adapter=fantastikt-loom-agent-2026
windows-x86_64.url=file:///absolute/path/taskctl-windows.zip
windows-x86_64.sha256=<actual SHA-256>
linux-x86_64.url=file:///absolute/path/taskctl-linux.tar.gz
linux-x86_64.sha256=<actual SHA-256>
```

The proof report includes actual artifact identities. `build/proof/toolchain.lock`
contains the concrete two-host pin from the completed run. No `latest`, ranges,
moving branches, or lock evaluation is permitted.

Wrappers validate the locked archive digest and compare cached executable files
against the manifest read from that verified archive. Only manifest-listed jars
enter the classpath. Artifact version and adapter must match the lock. A mismatch
fails without silently downloading another version. Optional `TASKCTL_CACHE` is
an absolute cache directory; `TASKCTL_OFFLINE=1` prohibits cold acquisition.

Linux bootstrap uses standard shell tools (`curl`, `tar`, `sha256sum`, `sed`,
`grep`, and basic filesystem commands). Windows uses PowerShell and .NET; the
launcher explicitly preserves native argument quoting on Windows PowerShell 5.
The consumer requires no Java, Kotlin, Gradle, Python, or Git executable.

Local `file:` acquisition and warm offline operation are exercised. Authenticated
registry acquisition, publishing, signing, macOS packaging, and a generator/init
integration remain later work. HTTPS acquisition is present but has not been
validated against a private package registry in this slice.

## Exit condition

The external archives must reproduce frozen-ledger reads and an evidenced
closure, reject malformed/blocked/stale inputs, recover a fault-injected
transaction, preserve shell arguments, validate pins, work with an unusable
consumer build, and keep writes within the declared boundary. Core extension and
DAEMON history cases must already be in conformance before any native-v1 freeze.
No active consumer migration is part of this exit condition.
