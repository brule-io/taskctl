# Workspace capability and value-boundary closure evidence

This evidence covers the general conformance contract of `TASK.migration.dropzone`.
The [design and limits](../../WORKSPACE-CAPABILITIES.md) describe two independently
authored fictional capabilities. They are not product providers or an operational
Dropzone adapter, and invoke no live host/service probes.

[The three-platform CI run](https://github.com/brule-io/taskctl/actions/runs/34180483878) passed candidate `1944cba5cd9abdffd33ca3b3511f499e8c23f211` at checkout
`414a2fb45751f268bdda26294519b8f1aeee2896`. Each of Linux x86_64, Windows x86_64 and macOS aarch64 passed 181
source-corpus tests (including three architecture checks), 178 identical JVM/native
behavioral identities and 289 packaged process parity cases. [validation.json](validation.json)
binds the jobs, parents, exact test identities and digest-verified downloaded
artifacts. Offline bootstrap, reproducible archive bytes and mutation-free producer
inspection also passed. [Local Windows evidence](local/) independently records
the same 178 behavioral tests and 289 process cases with verified artifact digests.

Nine shared file-ledger cases cover required typed workspace/environment payloads,
exact persisted pins, contributed prerequisites, explicit synthetic observations,
held policies, core evidence/acceptance guards, cold unavailable-provider inspection,
direct/transitive contract drift, historical receipts and unchanged unrelated files.
Unknown optional payloads preserve large integers, decimal type/scale and Unicode
controls, separators, noncharacters and supplementary characters in nested values
and keys. Installation grants no semantic authority; the CLI still has no loader.

The retained failing witnesses identified three concrete defects:

- At `2575d5404f2e15e01983a931678d2cc5555e2313`, a programmatic task namespace could
  pass construction, be written, and fail reconstruction. Task/planning constructors
  now share the existing namespace validity boundary before mutation.
- At `3df76b8f1b37e47726bbfee3b048cb990a4ec803`, unpaired surrogates were accepted and
  valid Unicode values could fail or change through JSON/YAML. Four shared core
  regressions now prove scalar validity, safe escaping, cold round trips and a
  pre-fix canonical digest. Malformed Unicode is intentionally rejected; valid
  text is not normalized and its canonical hashes remain unchanged.
- At `cbfd7378df6cb3c4364a19fee50a180becec265b`, representation details changed a
  provider's readiness under an equal contract/pin. The shared provider regression
  proves equivalent canonical inputs for evaluation and evidence verification,
  while the original stored payload remains exact. Precision requirements must be
  explicit semantic fields rather than incidental decimal scale.

These are bounded repairs of validity/semantic-input guarantees, not a claim that
the prior implementation accepted exactly the same malformed inputs. Historical
envelope/hash/provenance witnesses continue to pass; no old evidence is rewritten
or retroactively declared native v1. Provider/core transition authority remains
unchanged. The producer keeps its published alpha.2 pin and uses its legacy actor
assertion receipt format without invented authoritative acceptance time. Consumer
adoption, stateful mappings, deployment and native-v1 freeze remain separately gated.
