# taskctl contributor operating contract

Fantastikt migration and its 0.3.0-alpha.2 import distribution are complete.
Read `docs/SEMANTIC-0.3.md`, `docs/IMPORT.md`, `docs/MIGRATION-FANTASTIKT.md`
and the repository-local task graph. Start with `./taskctl doctor`, `context`,
`frontier`, and `status` (Windows: `./taskctl.bat` or `./taskctl.ps1`). Development
from adoption onward is tracked here. Publishing to brule-io/taskctl is authorized.
The frontier now names Brule Message Bus migration; it has not begun. Broad migrations and service work
remain gated by the recorded protocol checkpoint. No service implementation yet.

Core owns the top-level record namespace. Specialized data lives only under
`extensions`, with independently readable required extension identities. Unknown
optional payloads must survive unchanged. Required providers cannot bypass core
identity, graph, acceptance, or lifecycle validation.

Tasks are the executable DAG nodes. Roadmaps and epics are native durable
planning concepts with orthogonal task membership/association. Do not impose a
tree or infer prerequisite edges from planning order. Empty planning indexes
are valid; concurrency, priority and release gates remain policy capabilities.

This repository contains native draft and historical compatibility implementations,
not a frozen native v1 protocol. Preserve source provenance and document intentional
differences. Read commands must leave the consumer unchanged. Task mutations may
write only declared ledger/transaction paths and must never run implicit Git
operations. Run `./gradlew check` and packaged bootstrap integration tests for
supported platforms before release. Never claim an unexecuted platform check.
The owner selected Apache-2.0 for the protocol and reference tooling. Preserve
LICENSE and third-party notices in all distributions. The owner authorized public
Apache-2.0 publication of brule-io/taskctl on 2026-09-07. Historical source witnesses
retain their exact provenance; their source repositories keep their own visibility.

Lifecycle and currency are independent. Affected closed tasks keep their historical
receipts. Use `revise` and explicit `reconcile` evidence with CAS; never hand-edit
HEAD/history to clear affected work. New tasks use `tasking/core-draft-2` and named
verification evidence requirements. Add `--plan` to inspect mutation file digests.
See `.agents/README.md` for the generated tasking operating contract.
