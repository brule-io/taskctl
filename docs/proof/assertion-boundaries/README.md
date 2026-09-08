# Assertion boundary closure evidence

Candidate `394beeb465ff5997975ec11484d0e44637be33d7` passed [three-platform product CI](https://github.com/brule-io/taskctl/actions/runs/34186484380)
at checkout `e0fb1478ec9f355d1fe4ff2689771d69f90b1875`. Each supported platform passed 203
source-corpus tests, 200 identical JVM/native behavioral identities and 289
packaged process cases. [Validation](product/validation.json) records exact
source/test identities and digest-verified downloaded archives, with offline
bootstrap, reproducibility and mutation-free producer reads. The independent
[local Windows proof](local/) passed 200 behavioral and 289 process cases; the
two packaged archive digests were checked before copying the evidence.

The same candidate passed the separate [kernel run](https://github.com/brule-io/taskctl/actions/runs/34186484506)
and [generated-client run](https://github.com/brule-io/taskctl/actions/runs/34186484510); their completed jobs are
retained in [integration-runs.json](integration-runs.json). These remain the
existing 3+10 kernel and 4+2+8 JVM/Python suites, outside the shared native corpus.

The [design and limits](../../ASSERTION-BOUNDARIES.md) record the correction.
The preceding [Java probe](baseline.json) demonstrated accepted but undecodable
profile state, without applying that profile. Red source
`1861299a75e7d2c2f795918dfd3878e2e4a6f0a3` completed seven cases with five failures;
its log and XML are preserved. All seven now pass through the shared JVM/native
corpus. Profile/planning audit maps, blanked assessment criteria, task evidence and
review observations are checked before pure reduction, writable plans or commits.
Provider callbacks cannot mutate receipt/review evidence. Valid legacy time and
historical receipts retain their original meaning; cold reconstruction and
unrelated file bytes/mtimes remain correct.

The shared reducer reuses existing typed codecs rather than inventing a second
assertion schema. Provider evidence views own their immutable maps. Draft DTO
collections are otherwise not universally immutable, and concurrent caller
mutation during an operation is unsupported. No hash version, unchecked-cast
exception, broad native reflection allowance, evidence-truth claim, consumer
migration, external authority or native-v1 freeze is introduced. The producer's
pinned alpha.2 records this closure as a legacy actor assertion, without invented
storage acceptance metadata.
