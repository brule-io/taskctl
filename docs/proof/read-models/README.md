# Distinct read-model closure evidence

This evidence covers `TASK.protocol.read-models` and the contracts described in
[READ-MODELS.md](../../READ-MODELS.md). It proves the development implementation;
it is not a published distribution or a native-v1 freeze.

[CI run 34170183800](https://github.com/brule-io/taskctl/actions/runs/34170183800)
passed for candidate `4233a65b42c482e8fc78ef18ab2eb483d376181e` on Linux x86_64,
Windows x86_64 and macOS aarch64. Each platform passed 113 source tests, including
three architecture policy tests, and 110 identical behavioral test identities on
JVM and Native Image. Each packaged process parity run passed 156 cases.

[Validation](validation.json) records the candidate, exact CI checkout and parents,
job results, test-identity digest, artifact digests and build identities. Retained
`ci` files include corpus parity, process output/exit/file parity, cached bootstrap
and read-only inspection of the actual producer ledger with both implementations.
The collector verified the downloaded archive bytes against their build metadata.

The eight added behavioral tests and process cases distinguish diagnostic status,
bounded actionable context and complete typed snapshots. They exercise large
graphs and text, exact opaque numbers, Unicode and encoded-byte expansion,
explicit omission of oversized identities, unsupported required capabilities,
legacy and tracked readiness, current/historical receipts, immutable revisions,
source manifests and dependency bindings. Read and failed-command checks compare
repository file bytes and timestamps; mutations remain bounded to tasking state.

Local Windows checks also passed the 113 source tests, 110 native/JVM behavioral
tests and all 156 process cases using clean source at the same candidate. The
retained three-platform CI artifacts provide the portable proof.

The lifecycle receipt is a separate actor assertion about these observed checks.
No task contract, historical hash domain, planning relationship, provider authority
or lifecycle rule changes as a result of the new read projections.
