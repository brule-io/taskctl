# Remote kernel closure evidence

The bounded `TASK.remote.kernel` experiment is described in
[REMOTE-KERNEL.md](../../REMOTE-KERNEL.md). It uses the shared core reducer and
minimal profile through explicit PostgreSQL and loopback HTTP adapters. This is
an experiment with disposable databases, not a deployed or released service.

Candidate `241ac85312ec2b51dcb32c4a2cb795a888aefbc8` passed the [kernel run](https://github.com/brule-io/taskctl/actions/runs/34180495189) at exact checkout
`4336445ff00deee04c91e90554f7317c9a679606`. [Kernel validation](kernel/validation.json) records
three unit and ten integration test identities, source parents and the verified
kernel JAR digest. The [harness record](kernel/kernel-harness.json) and
[runtime observations](kernel/kernel.json) retain the database/build identity and
contention measurements. Three rounds of four unrelated writers each produced
one accepted transition and three stale-revision rejections, with zero automatic
application retries. Elapsed observations are measurements of this CI run only.

The same candidate passed [product CI](https://github.com/brule-io/taskctl/actions/runs/34180495098) at checkout
`4336445ff00deee04c91e90554f7317c9a679606`. All three supported platforms passed 181 source-corpus
tests, 178 identical JVM/native behavioral identities and 289 packaged process
cases. [Product validation](product/validation.json) binds source parents, jobs,
exact identities and digest-verified downloaded artifacts. Offline bootstrap,
archive reproducibility and mutation-free producer reads passed too. Kernel
dependencies remain outside the CLI/native runtime. Earlier independent local
Windows kernel runs also passed all 13 cases; closure relies on the exact final CI.

File, direct PostgreSQL and HTTP agree on lifecycle, task revisions, currency,
receipts, historical imports and planning. Snapshot identity remains whole-ledger
CAS; storage-specific planning origin identities are retained. SQL rollback after
snapshot update and before event insert leaves no partial acceptance. Tests cover
lost replies after commit, no-op events, backward clock movement, corrupted-event
audits, strict status diagnostics, unavailable capabilities and atomic capacity
refusal. Actor occurrence/legacy times cannot forge storage acceptance metadata.

The independent legacy fixture originates at
`dfa6a036e9e016f3c2b21fe6709d644ea6428722`. The malformed UTF-8 HTTP regression
at `20930ec16208b3db24420c5e57346510e79c7ad3` and its completed
[failing log](utf8-regression-red.log) retain the connection-close defect before
the correction; the final integration case proves a 400 response and no mutation.
No historical evidence is rewritten or retrospectively described as native v1.

Limits remain explicit: minimal unadopted profile, loopback-only experimental
transport, bounded snapshot/event sizes and event count, no authentication,
request deduplication, automatic retries, scalability promise, service launcher,
tenancy, billing, RBAC or UI. No consumer adoption, deployment or protocol freeze
is implied. The producer's alpha.2 pin uses a legacy actor assertion receipt;
it does not invent an authoritative acceptance time for that historical format.
