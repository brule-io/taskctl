# taskctl

Open-source tasking infrastructure, licensed under [Apache-2.0](LICENSE).

A standalone tasking tool for humans and coding agents. Tasks hold bounded intent,
acceptance, causal prerequisites and closure evidence. Roadmaps describe durable
lines of advance; epics associate work by capability. Project state stays in the
project repository; the implementation comes from an exact pinned release.

**Version: `0.3.0-alpha.2` (prerelease). Native protocol v1 is not frozen.**
The [candidate specification](docs/spec/NATIVE-V1-CANDIDATE.md) describes the
proposed semantic boundary and exact alpha compatibility evidence.
Native executables and JVM reference archives are tested on Windows x86_64,
Linux x86_64 and macOS arm64. Native is the default after behavioral parity passes.
Fedora 44 users can also [install the optional native RPM](docs/FEDORA-RPM.md).
The system command and repository-pinned `./taskctl` remain independent.

## 60-second greenfield quick start

Download the [release](https://github.com/brule-io/taskctl/releases/tag/v0.3.0-alpha.2)
native archive for your platform and `toolchain.lock`. Native needs no JVM.
The optional JVM reference archives use `toolchain-jvm.lock` and bundle Java.
The repository and published releases are public. No GitHub account or token is
needed for these downloads or the generated wrapper.

Windows PowerShell (no Java, Gradle or taskctl installation):

```powershell
$download = Join-Path $env:TEMP ('taskctl-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $download | Out-Null
$release = 'https://github.com/brule-io/taskctl/releases/download/v0.3.0-alpha.2'
Invoke-WebRequest -UseBasicParsing "$release/toolchain.lock" -OutFile "$download/toolchain.lock"
Invoke-WebRequest -UseBasicParsing "$release/taskctl-0.3.0-alpha.2-native-windows-x86_64.zip" -OutFile "$download/taskctl-0.3.0-alpha.2-native-windows-x86_64.zip"
$pin = ConvertFrom-StringData (Get-Content -Raw "$download/toolchain.lock")
$archive = "$download/taskctl-0.3.0-alpha.2-native-windows-x86_64.zip"
if ((Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $pin['windows-x86_64.sha256']) { throw 'Checksum mismatch' }
Expand-Archive $archive "$download/tool"
& "$download/tool/taskctl.exe" init --repo ./my-project --id brule.my-project --toolchain "$download/toolchain.lock"
cd my-project
./taskctl.ps1 --version
./taskctl.ps1 doctor
./taskctl.ps1 frontier
```

Linux/macOS shell (`TARGET=macos-aarch64` on Apple Silicon):

```sh
TARGET=linux-x86_64
DOWNLOAD=$(mktemp -d)
RELEASE=https://github.com/brule-io/taskctl/releases/download/v0.3.0-alpha.2
curl -fL "$RELEASE/toolchain.lock" -o "$DOWNLOAD/toolchain.lock"
curl -fL "$RELEASE/taskctl-0.3.0-alpha.2-native-$TARGET.tar.gz" -o "$DOWNLOAD/taskctl-0.3.0-alpha.2-native-$TARGET.tar.gz"
ARCHIVE="$DOWNLOAD/taskctl-0.3.0-alpha.2-native-$TARGET.tar.gz"
EXPECTED=$(sed -n "s/^$TARGET.sha256=//p" "$DOWNLOAD/toolchain.lock")
ACTUAL=$(shasum -a 256 "$ARCHIVE"); test "${ACTUAL%% *}" = "$EXPECTED" || exit 1
tar -xzf "$ARCHIVE" -C "$DOWNLOAD"
"$DOWNLOAD/taskctl" init --repo ./my-project --id brule.my-project --toolchain "$DOWNLOAD/toolchain.lock"
cd my-project
./taskctl --version
./taskctl doctor
./taskctl frontier
```

`No ready tasks.` is a successful empty frontier. Initialization creates no fake
work. Add an explicit plan with `--seed PLAN.json`, or later through `seed` and a
revision check. [Native records and evidence](docs/NATIVE.md) describe the inputs.
`--version`, `-V`, and `version` report one line without repository discovery or
network access. `version --format json` reports the pin in machine-readable form;
`info --format json` reports the acquired implementation and build/runtime details.
The PowerShell launcher supports Unicode paths and precise argument forwarding;
`.bat` is a convenience entry point for ordinary Windows shells.

## Mental model

- **Tasks / DAG:** atomic executable transitions with explicit prerequisites.
- **Roadmaps:** durable named task projections; order does not imply readiness.
- **Epics:** capability scopes associating work across roadmaps.
- **Receipts:** evidence for a particular semantic contract, preserved historically.
- **Extensions:** namespaced specialized data and narrowly scoped behavior.

These relationships are independent; empty and overlapping planning indexes are
valid. Unknown optional extensions survive core edits. Unavailable required
behavior is reported and blocks execution. Completing tasks does not automatically
accept an epic or close a continuing roadmap.

## Pinned consumer interface

Commit the generated `taskctl`, `taskctl.ps1`, `taskctl.bat`, `.taskctl` and `.agents`
state, including the POSIX executable bit. The launcher acquires exactly the
platform archive in `toolchain.lock`, verifies SHA-256, caches it outside your
project and checks its manifest on every run. No consumer build is invoked.
The verified artifact owns its entry point, whether native or JVM. Version queries
work even before the first acquisition. Use `TASKCTL_OFFLINE=1` to require cached
operation. Published taskctl releases work without credentials. Private mirrors
can use an explicit contents-read credential; no credential is stored in the project.

An agent starts with the generated `AGENTS.md`, `taskctl context`, `doctor`, and
`frontier`. Repository commands accept `--repo PATH` and `--format json`. The local
launcher defaults to its own repository, even when called from another directory.
`show`, `roadmap` and `epic` expose the model. `seed` and `close` require the revision
last inspected. `verify` validates supplied evidence without running project code.

## Contracts and composition

- [Revision-aware currency and reconciliation](docs/SEMANTIC-0.3.md)
- [Self-hosting](docs/SELF-HOSTING.md) and [0.3 milestone evidence](docs/MILESTONES-0.3.md)
- [Native repository, ledger seam, lifecycle and evidence](docs/NATIVE.md)
- [Extensions and provider boundaries](docs/EXTENSIONS.md)
- [Releases, pins, versioning and migrations](docs/VERSIONING.md)
- [Native Image, JVM parity and provenance](docs/NATIVE-IMAGE.md)
- [Optional Fedora RPM and global versus pinned execution](docs/FEDORA-RPM.md)
- [Milestone results and current limits](docs/PRODUCTIZATION.md)
- [Bootstrap composition contract](docs/BOOTSTRAP.md) and [reference generator](examples/generator/README.md)
- [Planning-model evidence](docs/PLANNING-MODEL.md)

Generators compose `init --contract taskctl.init/alpha1 --format json`; they do
not copy tasking templates. The initializer owns its files and accepts explicit
profile, seed and release lock inputs. Public Kotlin APIs are not required.

## Existing repositories

Use `taskctl adopt --repo PATH --id ID --toolchain LOCK --plan` to inspect bounded
existing-code adoption, then omit `--plan` to apply it. Existing AGENTS.md and source
are preserved; existing tasking or conflicting launchers are refused. Plain `init`
requires an empty directory (an existing `.git` is allowed). Neither command runs Git.
[Reviewed Fantastikt import](docs/IMPORT.md) provides an explicit inspect/plan/apply
path with exact source witnesses and historical evidence classification. It needs
an isolated target without existing tasking state; it never removes legacy tooling.
The [completed Fantastikt migration](docs/MIGRATION-FANTASTIKT.md) records exact
provenance, canonical usage before copied-tool retirement, and consumer acceptance.
Operational migrations are [owned by their consumer repositories](docs/CONSUMER-OWNERSHIP.md).
This public project's backlog covers reusable protocol, tooling and compatibility
work; it does not dispatch changes into consumer repositories.

## Contributing

Source builds require Java 21: `./gradlew check :cli:installDist` (`./gradlew.bat`
on Windows). `python scripts/package.py` packages the build using `JAVA_HOME`;
`python scripts/bootstrap_test.py --kind jvm` exercises a generated consumer with build tools
removed from PATH. CI also requires byte-identical repackaging. Read [AGENTS.md](AGENTS.md).
The typed value algebra and no-top-type architecture policy are enforced in CI.
Native delivery runs the shared behavioral corpus as native code, compares full
process outputs and filesystem effects, and tests cached bootstrap and import per implementation. Native build
commands and the exact GraalVM pin are in [NATIVE-IMAGE.md](docs/NATIVE-IMAGE.md).

## License

Licensed under [Apache-2.0](LICENSE). See [NOTICE.md](NOTICE.md) for scope and
third-party notices. No CLA is required. A separate future hosted service is not
part of this repository; code licensing grants no additional trademark rights.
The [public-access proof](docs/proof/publication/public-2026-09-07/README.md) records
anonymous downloads and consumer verification for the existing releases.
