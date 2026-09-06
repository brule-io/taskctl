# Fantastikt migration acceptance

Fantastikt's `development` branch now uses canonical native taskctl
[v0.3.0-alpha.2](https://github.com/brule-io/taskctl/releases/tag/v0.3.0-alpha.2).
`TASK.migration.fantastikt` is closed. The producer frontier is
`TASK.migration.brule`, which remains unstarted. Native v1 is not frozen.

## Exact identities

| Identity | Value |
| --- | --- |
| Ancestral Fantastikt commit | `b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4` |
| Migration implementation commit | `94f3176a78e3ce5a2c7d76c399c055ae69a02b46` |
| Consumer closure and accepted commit | `9b8b4c91aa3991c8a82dfa7d09591fcfbd53c74e` |
| Adapter / conversion version | `fantastikt-loom-agent-2026` / `0.2.0` |
| Import manifest | `sha256:a4bc24ebede998705855d39457ffd3b4d53b49939446f803b93dfb094d8e4fa4` |
| Producer release source | `8933bac1c090d175dfc035d7d52a40d1101f5333` |
| Native toolchain lock SHA-256 | `a73195736ee727f70608cfdcde58e4f07491abf5635e7fbffcc8725cad254f99` |

The original checkout was clean at the ancestral revision. Migration ran in an
isolated Git worktree, then `development` fast-forwarded to the two accepted commits.
The changes are committed locally in Fantastikt; no consumer remote push is claimed.
Git operations were explicit operator actions, never implicit taskctl behavior.

## Historical meaning and present evidence

All 53 exact source witnesses are retained in the import admission and consumer
archive, `docs/tasking/legacy-b932b0e/.agents`. The
[mapping review](proof/migration/fantastikt-0.3/mapping-review.json) preserves 40 task
identities, 29 historical closures, their contracts, dependency relationships and
independent roadmap/epic projections. The legacy dialect is identified explicitly;
it is never presented as native v1.

Import created no native receipts. Forty subsequent migration-equivalence actor
reviews bind the new contracts and current input observations. They assert reviewed
mapping and currency, not that historical external operations were repeated.
Native alpha3 fences older writers; origin-bearing task-revision/2 objects retain
import provenance while earlier revision/1 and contract digests keep their meanings.

The new `TASK.tasking.001` and its own roadmap/epic track integration work separately.
Its native receipt binds the implementation commit and actual verification.
The [accepted consumer state](proof/migration/fantastikt-0.3/final-acceptance.json)
contains 41 tasks, 30 closed and all current. There is exactly one native receipt,
for integration, and none fabricated for the 29 ancestral closures.
Only `TASK.draft-mvp.038` (operator configuration) remains ready.

## Usage before retirement

The unchanged consumer baseline passed 187 tests. Integrating the canonical wrapper
into `verify` also passed 187 tests while copied tooling was still present. The
[released consumer proof](proof/migration/fantastikt-0.3/canonical-before-retirement.json)
then demonstrated actual cold GitHub acquisition, eight inspections with unchanged
repository bytes/mtimes, credential-free offline execution, an evidenced native
reconciliation and stale-CAS rejection. The consumer PATH contained no Java, Gradle,
Git or Python. The exact published artifact, not the earlier dirty candidate,
established the retirement gate.

Only then were 23 copied task-tool files and their 44 duplicate tests retired.
Shared ADR parsing, diagnostics and lifecycle implementation remain, with all ten
ADR tests. Root launchers and `.taskctl/toolchain.lock` now select the canonical
release; AGENTS, README and Gradle verification use that interface. Product code
and SPEC Git blobs are unchanged. A narrow LF checkout rule fixes an existing
Windows-sensitive JSON test fixture without changing its contents.

The [full rebuilt verification](proof/migration/fantastikt-0.3/after-retirement-tests.json)
passed **143 tests: 133 product and 10 ADR**, with zero failures, errors or skips:

```text
gradlew --no-daemon :tooling:workflow:clean verify --rerun-tasks --max-workers=2
```

The PostgreSQL tests used Testcontainers against the existing Docker host `aibox`
through a temporary SSH tunnel because the local Docker engine was unavailable.
The tunnel was removed after testing. No project Docker configuration was changed.
The consumer retains complete baseline, integrated and final logs under
`docs/tasking/migration-0.3`; the final log hash is recorded in the test report.

## Reconstruction and release coverage

[Reconstruction from committed files](proof/migration/fantastikt-0.3/cold-committed-checkout.json)
passed eleven offline inspections without `.git`, ignored runtime or build outputs,
using the previously verified user cache. Repository bytes and mtimes were unchanged.
The original checkout's pinned `info`, `doctor` and `frontier` also passed, and its
Git state is clean. Cold artifact acquisition and cold repository reconstruction
are recorded as separate checks.

[Alpha.2 publication proof](proof/productization/0.3.0-alpha.2/README.md) records
Windows x86_64, Linux x86_64 and macOS arm64 success: 95 shared tests on JVM and native,
three additional JVM source-policy tests, 111 process comparisons, 25 bootstrap
checks per implementation and real GitHub release transport. All 28 publication
assets were verified against the signed immutable release attestation.

The [producer's final graph](proof/migration/fantastikt-0.3/producer-final.json) has
22 current tasks and 12 closures. The review's remaining design issues are retained
on [the pre-v1 board](PRE-V1.md). This migration adds no service or further consumer
migration; the optional alpha.1 RPM remains independently available.
