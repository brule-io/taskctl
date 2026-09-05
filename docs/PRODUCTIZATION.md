# Productization milestones

The initial release is an alpha. Publishing source or an archive does not freeze
native v1. This work follows the owner's order:

1. M1: canonical GitHub repository, contributor contract, CI and source history.
2. M2: versioned runtime archives, immutable checksums, pinned acquisition tests.
3. M3: native greenfield initialization and usable generated repository interface.
4. M4: reference generator consuming the supported bootstrap command contract.
5. M5: explicit adoption into existing code with no tasking.
6. M6: inspect/plan/apply import framework with durable provenance.
7. M7: historical adapters, starting with Fantastikt.

The immediate delivery is M1–M3, followed by M4. Existing consumers stay unchanged.
Each milestone's measured result and commands will be recorded here. A task-only
or empty graph is valid; scaffolding must not invent epics, roadmaps or work.

## Publication decisions

Canonical remote: `brule-io/taskctl`. Initially private unless the owner selects
public visibility. No project software license has been selected. This is a
separate owner decision; third-party licenses remain applicable to their components.

GitHub Releases is the distribution transport. Artifact hashes, release version
and platform belong to the launcher lock, never to task semantic contracts.
Use draft releases to assemble and verify all assets before immutable publication.
See [GitHub's immutable-release contract](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases).

## Module boundaries

- `core`: deterministic typed protocol, semantic contracts, task DAG and planning indexes.
- `repository`: native storage, initialization and bounded mutations (added with M3).
- `cli`: human/JSON commands using core and storage; no consumer build integration.
- `compatibility`: isolated historical dialect implementation.
- `conformance`: source witnesses, integration and architecture constraints.
- `packaging`: canonical launchers bundled with the tool, not generator-owned templates.

The Kotlin interfaces remain internal. The composable public boundary is a
versioned process command and typed JSON result, with explicit repository root.

## M1 result

Private canonical repository created and pushed at https://github.com/brule-io/taskctl.
The initial source baseline passes 73 tests locally. CI builds all three target
platforms. Its first run exposed the provider's exact JDK version spelling
(`21.0.11+10.0.LTS`); the pin was corrected without changing runtime selection.
No standalone release is claimed at M1. Source command: `./gradlew check`.

## Native alpha delivery

M2 packages Windows x86_64, Linux x86_64 and macOS arm64 with a bundled Java 21
runtime. CI verifies archive-byte reproducibility, digest-checked cold acquisition,
offline reuse, cached-file integrity, version mismatch rejection and a restricted
consumer PATH. Real GitHub transport is a separate release-smoke workflow before
immutable publication. Exact release asset identities are recorded in the release
manifest and committed proof receipt after publication.

M3 provides native `init`, `doctor`, `context`, `snapshot`, `frontier`, `show`,
`roadmap`, `epic`, `seed`, `verify`, `close` and `recover`. Run the extracted release:

```text
taskctl init --repo ./new-project --id brule.new-project --toolchain ./toolchain.lock
cd new-project
./taskctl doctor
./taskctl frontier
```

Windows uses the release/generated `taskctl.ps1`. The packaged test actually
generates a repository, uses only its local interface from an unrelated cwd,
reconstructs a checkout without ignored runtime files, admits a supplied plan,
checks revision conflicts, verifies/records assertion evidence, closes a task and
observes the dependent frontier. Existing source is refused by this greenfield
command. Source bytes and mtimes remain unchanged during reads.

M4's [reference generator](../examples/generator/README.md) invokes the same
versioned initializer, optionally passes a seed, then adds only its own source
skeleton. The packaged tests exercise its output. It has no forked templates or
tasking implementation. [The bootstrap contract](BOOTSTRAP.md) documents composition.

The native storage seam is `TaskLedger` / `FileTaskLedger`, with one core reducer.
Revision-aware dependency observations are supported internally without fabricating
bindings for existing identity-only edges. Nominal IDs have private constructors,
validated parsing and a CI guard. Native task/prerequisite/receipt state carries
typed IDs and contract digests. The hardened source build passes 82 tests.

Unresolved owner decision: project software license (none selected). Native v1
is not frozen. Group completion/archival, runtime capability providers, native
version migration and M5–M7 adoption/import remain subsequent work. No actual
consumer repository has been adopted or migrated.
