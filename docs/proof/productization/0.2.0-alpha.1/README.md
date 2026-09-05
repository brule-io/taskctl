# Native alpha publication proof

[taskctl 0.2.0-alpha.1](https://github.com/brule-io/taskctl/releases/tag/v0.2.0-alpha.1)
is an immutable private prerelease. Its exact source and tag commit is
`af6bc50cc183442b22f9737e1bfae898d02a5837`. This receipt records the completed M1–M4
delivery. It does not replace the earlier extraction or historical evidence.

## Recorded evidence

- [publication.json](publication.json): GitHub release state, six asset identities
  and hashes, tag verification, CI runs, per-module test counts and limitations.
- [release-manifest.json](release-manifest.json), [toolchain.lock](toolchain.lock)
  and [SHA256SUMS](SHA256SUMS): exact copies of the published release metadata.
- `ci-<platform>.json`: packaged consumer proof against each CI archive.
- `release-<platform>.json`: the same 20 checks using real GitHub asset acquisition.
- [local-windows-consumer.json](local-windows-consumer.json): an independently
  downloaded Windows archive initialized a fresh project, acquired its pinned
  runtime through the generated launcher and passed offline reads. The proof also
  records successful default-cache operation after publication.

| Platform | Source tests | Reproducible repackaging | GitHub consumer checks |
| --- | ---: | --- | ---: |
| Windows x86_64 | 82 passed | Passed | 20 passed |
| Linux x86_64 | 82 passed | Passed | 20 passed |
| macOS arm64 | 82 passed | Passed | 20 passed |

The source tests are core 20, repository 4, compatibility 45 and conformance 13
on each platform. Packaged checks include cold acquisition, offline reuse, cached
tamper/version/digest rejection, generated native state, committed-only checkout
reconstruction, revision conflicts, prerequisite evaluation, supplied evidence
validation and closure, read immutability, and generator composition.

Repackaging is byte-identical from the same tested build/runtime inputs; this
does not claim that separately sourced JDK builds are identical. Receipt evidence
used by the lifecycle fixture is explicitly an actor assertion, not an assertion
that taskctl executed an external integration test.

## Consumer commands

The [release quick start](https://github.com/brule-io/taskctl/blob/af6bc50cc183442b22f9737e1bfae898d02a5837/README.md#60-second-greenfield-quick-start)
downloads and verifies an archive without cloning or building taskctl. Given the
extracted Windows release and its downloaded lock:

```powershell
& "$download/tool/taskctl.ps1" init --repo ./my-project --id brule.my-project --toolchain "$download/toolchain.lock"
$env:TASKCTL_GITHUB_TOKEN = gh auth token
cd my-project
./taskctl.ps1 doctor
./taskctl.ps1 frontier
```

On Linux or macOS, use the extracted `taskctl` and the generated `./taskctl`.
Credentials are needed only for acquiring this private release and are never
stored in the project. The supported composition entry point is
`init --contract taskctl.init/alpha1 --format json`; the [reference generator](../../../../examples/generator/README.md)
invokes it without copying tasking templates.

A ready local demo from this release is at `C:\projects\taskctl-greenfield-demo`:

```powershell
cd C:\projects\taskctl-greenfield-demo
./taskctl.ps1 doctor
./taskctl.ps1 frontier
```

Its default runtime cache has been populated on the current host. An empty
frontier is successful: no fake tasks, roadmaps or epics were created.

## Remaining decisions and scope

Native v1 remains unfrozen, and no project software license has been selected.
Existing-code adoption, native version migration and historical inspect/plan/apply
imports are not shipped in this alpha. Existing product repositories were not
migrated. Runtime provider loading and group completion/archival remain future
capabilities; unsupported required semantics fail closed.
