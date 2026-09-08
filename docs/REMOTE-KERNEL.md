# Bounded PostgreSQL/HTTP ledger experiment

This development experiment implements `TaskLedger` with PostgreSQL and loopback
HTTP after the checkpoint, typed evidence time and planning-history work. The
`kernel` module is a JVM test component. It has no service entry point, deployment,
CLI distribution, tenant model, billing, RBAC or product UI. File storage remains
the normal native implementation. The experimental wire is not frozen v1 or its IDL.

`PostgresTaskLedger` and `HttpTaskLedger` use the same `LedgerSnapshot`, `Transition`,
`LedgerTransitions.evolve`, frontier, currency and evidence rules as file storage.
Existing typed codecs handle tasks, receipts, revisions, imports and planning.
No second lifecycle reducer or untyped protocol state is introduced. The syntax
policy scans the kernel too. This experiment supports the unadopted minimal profile;
persisted effective profiles and profile activation are explicitly refused. Unknown
optional extension values survive exactly; required unsupported capabilities retain
the core's storage/inspection versus execution distinction.

## Snapshot identity and atomic acceptance

`Revision` remains the identity of the complete ledger snapshot, not a task ETag,
event sequence or wall clock. The kernel uses the explicit
`taskctl.kernel-snapshot/alpha1` hash domain over its complete typed state. File
revisions bind the file representation, so the two storage adapters need not issue
equal snapshot IDs. Old task/import identities are preserved. Planning baselines
bind each adapter's actual observed ledger revision; their different origin IDs
are retained and validated rather than normalized away.

Application state is stored as canonical JSON in PostgreSQL **TEXT**. The core's
value algebra preserves large integers and decimal type/scale without `jsonb`
numeric rewriting. A writer opens a READ COMMITTED transaction, locks the one ledger
row with `SELECT ... FOR UPDATE`, checks the exact expected snapshot revision, runs
the shared reducer, and writes the snapshot and acceptance event in one transaction.
PostgreSQL holds that row lock until the transaction ends; a waiting writer reads
the updated row before making its CAS decision.
[PostgreSQL locking reference](https://www.postgresql.org/docs/17/explicit-locking.html).

An accepted event contains its sequence, parent event ID, inspected snapshot ID,
complete typed transition, resulting snapshot ID, changed record identities and
typed `AcceptedAt`. Its digest has a separate `taskctl.kernel-event/alpha1` domain.
Acceptance time is sampled from the accepting boundary's clock after semantic
validation and is durable only if the entire transaction commits. It is not an
assertion of the exact physical commit instant, actor authentication or task truth.
Actor `OccurredAt` and legacy recorded strings remain inside their unchanged
evidence envelopes. Actor requests cannot supply storage acceptance metadata.

Event sequence supplies acceptance order even if the wall clock moves backwards.
A valid no-op may retain the same content-based snapshot revision while recording
another event; this deliberately preserves the distinction between snapshot identity
and operation identity. The kernel has no request deduplication/idempotency protocol.
An uncertain HTTP outcome must be inspected; the adapter performs no application
retry. Reusing a stale revision for a material change is rejected. A no-op retry can
produce another event because unchanged snapshot identity is not a deduplication key.

Explicit `audit()` reads one consistent PostgreSQL snapshot and replays every
bounded event through the same reducer, verifying the chain, transitions, changed
identities, resulting revision and final stored state. Ordinary reads do not append
events, acquire writer locks, run DDL or create a ledger. Schema initialization and
ledger creation are separate explicit programmatic operations.

## Transport and capacity

`KernelHttpServer.start` binds an ephemeral `127.0.0.1` port for a scoped test
lifetime; `close()` stops the listener and workers. `HttpTaskLedger` accepts that
explicit loopback endpoint and closes its client resources. There is no background
service launcher or consumer configuration change.

The experimental routes are GET `/kernel/alpha1/snapshot`, POST
`/kernel/alpha1/apply`, and GET `/kernel/alpha1/events?after=N`. Event reads return
at most one complete event per page. The wire accepts the exact JSON emitted by
the typed writer, with strict versioned fields, valid UTF-8 and no YAML syntax.
CAS conflicts return 409; invalid transitions/envelopes return 400; unsupported
methods, paths, content types and oversized request bodies have distinct statuses.
Database failure details are not included in HTTP responses.
Malformed UTF-8 is rejected as a 400 validation error before any mutation. Its
decoder exception is separated from transport I/O failure; the real HTTP regression
at source `20930ec16208b3db24420c5e57346510e79c7ad3` first reproduced the earlier
connection-close behavior. The [completed baseline log](proof/remote-kernel/utf8-regression-red.log)
records that failed HTTP case; the corrected complete suite is included in closure
evidence.

This is intentionally bounded: 250,000 UTF-8 bytes per stored snapshot/event/command,
1,000,000 bytes per HTTP body, and 512 accepted events per experimental ledger.
Commands that would exceed storage capacity fail before a commit. These are adapter
capacity limits, not new core validity rules or a scalability claim. Database lock,
query, connection and HTTP timeouts bound the conformance harness. The loopback
endpoint is not a hardened public API.

## Running and reviewing the proof

```text
./gradlew :kernel:test
python scripts/kernel_test.py
```

The harness runs the digest-pinned PostgreSQL image in a uniquely named disposable
container bound only to loopback. A `finally` block verifies and removes that exact
test container after success or failure. It creates no host data volume and retains
no database service. CI can supply its own explicitly marked disposable database
with `--existing-disposable`; it must set the four `TASKCTL_KERNEL_*` environment
values shown in the kernel workflow. Never point this test harness at a live database.

The PostgreSQL JDBC dependency is pinned to `42.7.13` in the kernel dependency lock.
It is isolated from the CLI/native artifacts and remains under the driver's BSD
license; its upstream source and release information are available at
[pgJDBC](https://jdbc.postgresql.org/download/). Existing Apache-2.0 project licensing
and distribution notices remain in force.

The conformance compares complete task history, contracts, dependencies, currency,
frontier, receipts and imported provenance across file and HTTP storage, and checks
each adapter's full result against the shared reducer. Receipt collection comparison
sorts only enumeration order: files enumerate digest-named receipts, while the kernel
retains insertion order. Planning origin/HEAD IDs are validated per adapter. Tests
cover scope amendments, archival and assessments, old evidence after direct/transitive
drift, current reviews, stale CAS, rollback after snapshot SQL but before event insert,
cold HTTP reconstruction, strict diagnostics and unavailable capabilities.
They also drop a response after a real commit, detect corrupted event data, retain
older untracked native assertions through explicit history adoption, and reject a
core-valid operation atomically when this adapter's storage budget would be exceeded.
The fictional historical import witness is pinned to public source commit
`dfa6a036e9e016f3c2b21fe6709d644ea6428722`; it is not a consumer repository export.

Contention measurements use three rounds of four unrelated writers with the same
inspected revision. Results record accepted versus stale attempts and elapsed time;
there are no automatic application retries. This establishes a baseline for a later
explicit CAS-granularity decision. It does not quietly change `Revision` semantics
or imply production throughput. Exact observed results are recorded before the
bounded task is closed; broader service/operations work remains separately gated.
