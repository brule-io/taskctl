# 0.3.0-alpha.3 build and publication proof

Status: [0.3.0-alpha.3](https://github.com/brule-io/taskctl/releases/tag/v0.3.0-alpha.3)
is published publicly and immutably. All 34 downloaded asset digests, the source
tag and signed GitHub release attestation were verified; see
[publication](publication.json), [manifest](release-manifest.json),
[checksums](SHA256SUMS) and the [raw verification](release-attestation-verification.json).

The exact build source is `084cea04271764f6d666c0db6aa0da517abce7a6`.
[CI](https://github.com/brule-io/taskctl/actions/runs/34190359911) passed on Linux
x86_64, Windows x86_64 and macOS aarch64. Each platform passed 209 source-corpus
checks, the same 206 JVM/native behavioral checks, 289 packaged process comparisons,
25 bootstrap checks per implementation and seven self-host reads per implementation.
The process corpus compares output, diagnostics, exit codes and repository effects;
bootstrap includes cold acquisition, source/build-tool-free use and offline caches.

[Validation](validation.json) binds the successful jobs, exact checkout, artifact
metadata, test identity digest and downloaded archive hashes. The [CI records](ci/)
retain the actual corpus, bootstrap, process and self-host results. The six
[content records](content-validation.json) each report 37 bundled guide files,
99 local links and 70 links bound to the exact source commit. Experimental kernel,
IDL, PostgreSQL and Smithy runtime dependencies are excluded from the CLI archives.

The bounded kernel and IDL source jobs passed separately at this same revision:
[kernel](https://github.com/brule-io/taskctl/actions/runs/34190359893) and
[IDL](https://github.com/brule-io/taskctl/actions/runs/34190359892). They do not
constitute a deployed service or part of the consumer runtime distribution.

The first [transport run](https://github.com/brule-io/taskctl/actions/runs/34192042487)
passed bootstrap on all three platforms, then exposed a verification-harness
dependency on two Gradle-generated preimages missing from the clean checkout.
[The failure](transport-failure.json) is retained. Harness revision
`cb4575b5d830169abadb3d53ea102a570060c441` makes those fictional inputs explicit,
with exact generator provenance and byte digests. It changes only the parity
driver and fixtures (five source paths total); the canonical runtime artifacts and their
build revision remain unchanged. The fresh
[transport run](https://github.com/brule-io/taskctl/actions/runs/34192543158) passed
on all three platforms using that separately identified harness: 25 bootstrap
checks per implementation, the exact content checks and all 289 process pairs.
[Transport validation](transport-validation.json) records the restricted source
diff, fixed input identities and [actual results](transport/). No process cases
were removed or normalized. No draft asset was replaced to resolve the harness
failure, and no runtime source changed.

Native-v1 remains unfrozen. Tool version alpha.3 is separate from the repository,
record, receipt, planning and profile envelope versions described in the release
notes. This release introduces no automatic consumer adoption or state rewrite.
The Fedora RPM and producer-pin acceptance have their own prerequisite-bound tasks.

Build, transport, publication, closure and later producer-pin commits have distinct
identities. A receipt written after publication is not part of the already-built
source, and no later CI artifact replaces the published bytes. The intended
attestation boundary is the signed immutable GitHub release; no separate CI OIDC
build signature, RPM signing key or SLSA level is claimed.
