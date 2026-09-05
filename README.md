# taskctl

A standalone tasking tool for humans and coding agents. Task records hold bounded
intent, acceptance, causal prerequisites and closure evidence. Roadmaps describe
durable lines of advance; epics associate work by capability. Project state stays
in the project repository; the implementation comes from an exact pinned release.

**Alpha productization in progress. Native v1 is not frozen.**

## First run

Greenfield bootstrap is the next delivery: obtain a standalone release, initialize
a project, then run its local `taskctl doctor` and `taskctl frontier`. No system
Java, consumer Gradle build or source checkout will be needed. Measured availability
and commands are recorded in [product milestones](docs/PRODUCTIZATION.md).

## Mental model

- Tasks are executable transitions and nodes in a prerequisite DAG.
- Roadmaps are named task projections; display order does not grant readiness.
- Epics are capability scopes spanning tasks across roadmaps.
- Receipts bind evidence to the exact semantic contract it addresses.
- Namespaced extensions add specialized data and narrowly scoped behavior.

There is no mandatory epic/roadmap/task ownership tree. Empty planning indexes
are valid. Unknown optional extensions survive core edits; unavailable required
behavior blocks executable decisions.

## Build and contribute

Source builds require Java 21. Run `./gradlew check :cli:installDist` (Windows:
`./gradlew.bat`). CI checks Windows, Linux and macOS; release support is claimed
only after packaged acquisition tests pass. Kotlin compiler warnings and the
no-top-type architecture policy fail the build.

Read [AGENTS.md](AGENTS.md), [the draft contract](docs/CORE-DRAFT.md),
[planning semantics](docs/PLANNING-MODEL.md), and [milestones](docs/PRODUCTIZATION.md).
The [extraction proof](docs/PROOF.md) records design provenance.

## Existing repositories

Safe adoption into existing code follows greenfield bootstrap. Historical import
is the advanced path after that, retaining exact identities, uncertainty and
adapter/source provenance. No existing consumer is migrated by this phase.

## License

The owner has not selected a project software license. See [NOTICE.md](NOTICE.md).
