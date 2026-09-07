# Divergent import compatibility specimen

`TASK.migration.divergent` exercises an independently authored, fictional
object-record dialect through the existing typed import and file-ledger boundary.
It is not a consumer migration or a supported new CLI adapter. Production core,
protocol versions and lifecycle semantics are unchanged.

## Source and selected adapter

The source tree is in
`conformance/src/test/resources/divergent-import/source` at taskctl commit
`7426b442a1fc217894af2c6f9f77ce8bc05413af`. The neighboring `sources.json`
records its exact repository, prefix and SHA-256 witnesses. No consumer source,
business information or internal planning report was copied into this fixture.

The conformance-local `ObjectWorkAdapter` is explicitly selected as
`conformance-object-work` version `1.0.0`. It is absent from the product CLI
registry. Tests use the same typed `ImportManifest`, `ImportAdmission`, `Bootstrap`
and `FileTaskLedger` APIs available at the existing extraction boundary. This is
an internal API experiment, not a newly supported external library contract.

The source has nested contract/lifecycle objects, embedded open/closed state,
feature associations, no roadmap concept, complete hyphenated identities, large
exact numeric annotations and historical checked flags. This differs from the
Markdown/path-lifecycle donor exercised by the earlier import corpus.

| Source concept | Explicit projection |
| --- | --- |
| Complete WORK-N identity | `TASK.specimen.WORK-N`; preserve all digits including leading zeroes, with original identity retained. |
| Prerequisite WORK-N references | Native task prerequisites with the same injective mapping. |
| Feature association | Native epic task association; no roadmap is invented and no planning edge becomes a dependency. |
| Title, intent, requirements, check text | Native title, bounded intent, requirements and acceptance. |
| Optional labels and historical checked flags | Typed `legacy.object-work/v1` payload; no execution authority. |
| Embedded closed state and narrative | Historical import state and exact source witness; no native receipt or invented old input observation. |
| Current verification | Adapter declares the named `specimen` evidence class for new reviews/closure, without claiming a historical command ran. |

The adapter's historical contract digest is explicitly named
`specimen.work/1/adapter-contract/1` over the source contract object. It is an
adapter-defined historical projection, distinct from the new native semantic
contract. The manifest binds that projection, exact source, adapter/version and
target records. `source_native_protocol` remains null and historical evidence
remains `historical-narrative-unverified`.

## Conformance and process checks

Seven tests cover complete `WORK-1` versus `WORK-01` identities, precise integer
and decimal values, nested opaque data, exact manifest/source round trips, no
roadmaps, feature associations, unchanged source reads, bounded initialization,
normal reconciliation/closure and cold reconstruction. They also cover stale CAS,
wrong-order reconciliation, old receipts after a changed contract, transitive
currency after intermediate review, mismatched import reviews, source/version
drift, cycles, missing feature witnesses, malformed fields and unsupported states.

The JVM test emits `build/proof/divergent-native-preimage.json` using canonical
Bootstrap. The packaged process parity driver restores this exact imported
preimage for both implementations, then compares outputs, exit codes and resulting
files for doctor/context/snapshot/frontier/show/history, planning, reconciliation,
verify/close, stale CAS and transitive currency. No second implementation of
conversion exists in Python. The driver preserves opaque numbers when preparing
material contract edits instead of coercing them through binary floating point.

Native Image reuses the same behavioral test identities. Its only added resource
allowance is `divergent-import/.*`; no reflective/dynamic adapter loading or broad
reflection configuration is introduced. Existing distribution and cached-wrapper
checks remain in the CI matrix.

Run:

```text
./gradlew :conformance:test --tests '*DivergentImportConformanceTest'
./gradlew check :cli:installDist
./gradlew :cli:nativeCompile :native-tests:nativeTest --max-workers=1
python scripts/corpus_proof.py
python scripts/package.py --kind jvm
python scripts/package.py --kind native
python scripts/parity_test.py
```

The first command also generates the canonical process preimage. The current
CI workflow runs the complete sequence and the existing bootstrap/reproducibility
checks on Linux x86_64, Windows x86_64 and macOS aarch64. A successful compiler
invocation alone does not complete this task.

The completed [closure evidence](proof/divergent-import/README.md) records the
exact successful three-platform run, 102 shared behavioral identities and 144
packaged process cases per platform.

## Checkpoint findings and adoption limits

**Schema strings are not adapter identities.** A different structure with the
same schema label is rejected. Exact source revision/provenance, explicit adapter
selection and strict structural validation are all necessary. New adapters must
record any original-to-native identity mapping and prove it is injective.

**Historical closure is not current proof.** An unchecked historical criterion
does not erase a recorded closure; importing it also does not prove that the
criterion was satisfied. Original bytes remain available and new actor reviews
carry their own identity/classification. Current input correspondence is established
in causal order, and changed inputs remain visible transitively.

**Execution restrictions cannot become optional labels.** This adapter rejects
claimed/blocked states and required host/operator semantics. Native task lifecycle
and `ImportSource` currently admit open/closed state, and effective behavioral
provider/profile identity is not yet persisted through the full history/currency
seam. A schema-only preservation approach cannot promise equivalent readiness.
Before admitting a real consumer with these semantics, the protocol checkpoint
must explicitly resolve its preserving mapping/provider requirements or keep that
adoption blocked. This specimen does not quietly broaden import to accept it.

**Planning history is separate from membership.** This specimen demonstrates
zero roadmaps and independent feature association. It does not settle planning
amendment/archival/closure policy; that remains a checkpoint and later planning
conformance topic.

The bounded open/closed conversion works through existing core operations. The
result supports the extraction boundary while keeping unsupported adoption and
native-v1 decisions gated. It does not authorize consumer integration, copied-tool
retirement, a service implementation or protocol freeze.
