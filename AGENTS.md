# taskctl contributor operating contract

The current authorized phase is productization in bounded milestones, recorded
in `docs/PRODUCTIZATION.md`: canonical GitHub source, immutable standalone
distributions, native greenfield bootstrap, then generator composition. Existing
code adoption and legacy import follow those working native paths. Publishing
this tool to brule-io/taskctl is authorized. Product repositories and DAEMON are
read-only specimens; actual consumer migrations require separate tasking.

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
Do not select a software license without the owner's decision. Historical source
witnesses include private project material; repository visibility is explicit.
