# IDL projection closure evidence

Candidate `49c07874b92f32350f58ebb656a63ef98349dd15` passed the dedicated [IDL integration run](https://github.com/brule-io/taskctl/actions/runs/34183878449)
at checkout `382291006adc7053b7d28ad6734d6e7bc66b2ebc` and the separate
[three-platform product run](https://github.com/brule-io/taskctl/actions/runs/34183878409) at `382291006adc7053b7d28ad6734d6e7bc66b2ebc`.
The [projection and limits](../../IDL-PROJECTION.md) describe this bounded experiment.

[IDL validation](idl/validation.json) records four JVM projection tests, two real
PostgreSQL/HTTP generated-client tests and eight Python tests. The checked artifact
digests and JAR are bound to that source. The independently completed
[local Windows run](local/idl-harness.json) used the exact clean candidate, matched
the JAR/generated hashes, passed the same tests and removed its disposable container.
No deployed service or published SDK was involved.

[Product validation](product/validation.json) records 190 source-corpus tests,
187 identical JVM/native behavioral identities and 289 packaged process parity
cases per supported platform. Downloaded archive digests, exact test identities,
offline bootstrap, reproducibility and mutation-free producer reads were checked.
The IDL tests are separate from this shared native corpus; IDL dependencies do
not enter the CLI or native runtime.

The custom Kotlin AST produces a Smithy 2.0 JSON AST validated by the real Smithy
1.73.0 assembler and a Python client with generated nominal envelopes and methods.
Reproducibility checks reject checked-in artifact drift. Large integers, decimal
category/scale, Unicode, nullability, exact JSON and core-framed digest witnesses
remain typed. The real HTTP round trip covers seed, orthogonal roadmap/epic data,
closure and historical receipts, stale CAS, invalid/premature close, event chains,
storage acceptance time and cold reconstruction. Lost reply after real commit
produces one request and one event with no automatic application replay.

Task-specific subtrees remain closed Value data; the server owns their lifecycle
validation. This is a custom kernelJson protocol, not a stock Smithy serializer
or a second task reducer. The client is intentionally loopback-only and bounded.
There is no authentication, production service, language matrix, consumer
adoption, new repository or native-v1 freeze claim. The published alpha.2 producer
pin records closure as a legacy actor assertion without invented acceptance time.
