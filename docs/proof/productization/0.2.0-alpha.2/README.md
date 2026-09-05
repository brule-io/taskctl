# Native distribution publication proof: 0.2.0-alpha.2

[Release v0.2.0-alpha.2](https://github.com/brule-io/taskctl/releases/tag/v0.2.0-alpha.2)
was published as an immutable private prerelease on 2026-09-05 at 23:09:56 UTC.
Its tag resolves to `4ea1074fb629b66d3e76d18151fd9273e1ee041a`. All 22 uploaded
asset sizes and SHA-256 digests match the tested inputs and assembled metadata.
These documentation receipts follow the release source; they do not replace or
rebuild its artifacts.

The release ships native and JVM archives for Windows x86_64, Linux x86_64 and
macOS arm64. [toolchain.lock](toolchain.lock) selects native after all parity gates
passed; [toolchain-jvm.lock](toolchain-jvm.lock) selects the JVM reference. Consumer
launchers acquire, verify, cache and execute either through the same artifact
entry-point contract. No implementation fallback is implicit.

| Evidence, on each platform | Result |
| --- | --- |
| Source checks | 82 passed |
| Existing behavioral corpus on JVM | 79 passed |
| Same behavioral corpus compiled as native code | Same 79 test identities passed |
| JVM/native process comparisons | 49 passed |
| Packaged bootstrap checks | 21 passed per implementation |
| Actual GitHub release acquisition checks | 21 passed per implementation |
| Process comparisons repeated on release downloads | 49 passed |
| Repackaging the same compiler/runtime inputs | Identical archive bytes |

[Source CI](https://github.com/brule-io/taskctl/actions/runs/33997015520) and
[release smoke](https://github.com/brule-io/taskctl/actions/runs/33997798618) completed
successfully on all three platforms. The three compiler/PSI architecture checks
remain JVM source checks; all runtime behavioral test identities run in both
implementations. The corpus files enumerate the identities and bind their digest.

The process corpus covers version/info, init/bootstrap, doctor/context/frontier/show,
task/roadmap/epic relationships, stale-revision rejection, verify/close/receipts,
historical evidence, required capabilities, diagnostics and exit codes. It compares
stdout/stderr and resulting file bytes/modes from identical preimages; reads also
preserve mtimes, and writes preserve unrelated source and Git sentinels. Only
info's implementation/runtime/build identity may differ. The bootstrap suite adds
cold downloads, version queries before cache creation, credential-free offline
reuse, checksum rejection, committed-checkout reconstruction and generator composition.

## Artifact and signature evidence

- [publication.json](publication.json): release/tag identity, all asset IDs/digests,
  workflow/job URLs, measurements and limits.
- [release-manifest.json](release-manifest.json): implementation and full build
  identity for every archive, including the exact GraalVM toolchain and executable
  digests, both locks and all published evidence assets.
- [SHA256SUMS](SHA256SUMS): checksums for the archives and release metadata.
- `*.intoto.json`: per-archive build provenance statements.
- `corpus-*.json` and `parity-*.json`: exact published source-CI evidence.
- `ci/` and `release/`: bootstrap evidence for both formats and process parity
  repeated against actual release downloads.
- [release-attestation-verification.json](release-attestation-verification.json):
  verified GitHub signature, timestamp and subjects. All 22 asset subjects and
  the source tag match the locally checked inputs.

```text
gh release verify v0.2.0-alpha.2 --repo brule-io/taskctl --format json
```

GitHub's signature attests immutable publication of the assets, including their
build provenance. It does not independently observe every build step. No separate
CI OIDC build signature or SLSA level is claimed; per-build attestations are not
available to this private repository on the organization's current Team plan.

## Source-free Windows consumer

[local-windows-consumer.json](local-windows-consumer.json) records a fresh published
native download, archive/file verification, direct executable version aliases,
mutation-free init planning and creation of `C:\projects\taskctl-native-demo`.
The generated wrapper reports all version forms before its cache exists. Cold
acquisition then succeeds; offline doctor/info/frontier succeed without credentials.
Consumer PATH contains only Windows system utilities, with an absent JAVA_HOME.
Read commands preserve every repository file's bytes and mtime. The existing
alpha.1 demo is unchanged. The default user cache is populated and verified offline.

```powershell
cd C:\projects\taskctl-native-demo
./taskctl.ps1 --version
./taskctl.ps1 -V
./taskctl.ps1 version --format json
./taskctl.ps1 info --format json
$env:TASKCTL_OFFLINE = '1'
./taskctl.ps1 doctor
./taskctl.ps1 frontier
```

Native protocol v1 remains unfrozen. No existing product repository was migrated,
and the project software license remains an owner decision. Linux testing covers
Ubuntu 24.04; other libc or older OS baselines are not implied. The reproducibility
claim concerns repackaging identical inputs, not independent Native Image compiler
invocations. Production configuration permits only the exact VERSION resource and
does not add broad reflection allowances.
