# Divergent import closure evidence

This evidence covers `TASK.migration.divergent` and the bounded mapping in
[DIVERGENT-IMPORT.md](../../DIVERGENT-IMPORT.md). It is observed conformance for a
fictional specimen, not a consumer migration or a published release.

[Validation](validation.json) records the exact PR candidate, CI checkout commit,
job results, artifact identities, test identities and source manifest identity.
The source fixture is independently authored at commit
`7426b442a1fc217894af2c6f9f77ce8bc05413af`; the adapter is
`conformance-object-work` version `1.0.0`. The unmodified canonical
[import admission](specimen-admission.json) retains exact opaque numeric tokens,
source bytes, historical contract projections and target records. Its embedded
review is explicitly a fictional conformance assertion.

[CI run 34167144767](https://github.com/brule-io/taskctl/actions/runs/34167144767)
passed on Linux x86_64, Windows x86_64 and macOS aarch64. Every platform ran 105
source tests, including three architecture policy tests, and matched 102 exact
behavioral test identities between the JVM and Native Image. Each packaged
process parity run passed 144 cases. The retained `ci` files include both build
identities, exact archive digests, corpus identities, command/exit/file parity,
cached bootstrap and read-only inspection of the producer ledger. The collector
also verified the downloaded archive bytes against the build metadata.

The imported semantic values and manifest identity agree across platforms.
Windows emits the source-file object members in a different order because its
path enumeration compares case differently. Validation records each exact
preimage digest and compares the admission using exact decimal values; all other
ledger files match byte for byte. Object member order is not a semantic identity.

Local Windows `gradlew check` passed before submission. Its local 144-case process
run used preexisting development archives from source `e1769e57f9da2d3c3bbdde4277c89aebb3c81467`
marked dirty, so that run is supporting evidence only. The retained CI artifacts
were rebuilt from the recorded clean PR checkout and provide the platform proof.

The lifecycle task receipt is a separate actor assertion about this observed work.
Historical source closure remains unverified narrative. The specimen deliberately
rejects claimed/blocked state and host/operator requirements; no unsupported
execution condition is lowered into optional annotations. Those limitations are
explicit inputs to the protocol checkpoint and prevent equivalent consumer
adoption until a preserving capability/profile model is proved.
