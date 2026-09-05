# taskctl

A standalone tasking tool for humans and coding agents. Tasks hold bounded intent,
acceptance, causal prerequisites and closure evidence. Roadmaps describe durable
lines of advance; epics associate work by capability. Project state stays in the
project repository; the implementation comes from an exact pinned release.

**Native alpha: `0.2.0-alpha.1`. Native v1 is not frozen.**
Windows x86_64, Linux x86_64 and macOS arm64 are tested in GitHub CI.

## 60-second greenfield quick start

Download the [release](https://github.com/brule-io/taskctl/releases/tag/v0.2.0-alpha.1)
archive for your platform and `toolchain.lock`. The archive includes Java.
For this initially private repository, authenticate `gh` with repository read access.

Windows PowerShell (no Java, Gradle or taskctl installation):

```powershell
$download = Join-Path $env:TEMP ('taskctl-' + [Guid]::NewGuid().ToString('N'))
gh release download v0.2.0-alpha.1 --repo brule-io/taskctl --pattern '*windows-x86_64.zip' --pattern toolchain.lock --dir $download
$pin = ConvertFrom-StringData (Get-Content -Raw "$download/toolchain.lock")
$archive = "$download/taskctl-0.2.0-alpha.1-windows-x86_64.zip"
if ((Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $pin['windows-x86_64.sha256']) { throw 'Checksum mismatch' }
Expand-Archive $archive "$download/tool"
& "$download/tool/taskctl.ps1" init --repo ./my-project --id brule.my-project --toolchain "$download/toolchain.lock"
$env:TASKCTL_GITHUB_TOKEN = gh auth token
cd my-project
./taskctl.ps1 doctor
./taskctl.ps1 frontier
```

Linux/macOS shell (`TARGET=macos-aarch64` on Apple Silicon):

```sh
TARGET=linux-x86_64
DOWNLOAD=$(mktemp -d)
gh release download v0.2.0-alpha.1 --repo brule-io/taskctl --pattern "*$TARGET.tar.gz" --pattern toolchain.lock --dir "$DOWNLOAD"
ARCHIVE="$DOWNLOAD/taskctl-0.2.0-alpha.1-$TARGET.tar.gz"
EXPECTED=$(sed -n "s/^$TARGET.sha256=//p" "$DOWNLOAD/toolchain.lock")
ACTUAL=$(shasum -a 256 "$ARCHIVE"); test "${ACTUAL%% *}" = "$EXPECTED" || exit 1
tar -xzf "$ARCHIVE" -C "$DOWNLOAD"
"$DOWNLOAD/taskctl" init --repo ./my-project --id brule.my-project --toolchain "$DOWNLOAD/toolchain.lock"
export TASKCTL_GITHUB_TOKEN=$(gh auth token)
cd my-project
./taskctl doctor
./taskctl frontier
```

`No ready tasks.` is a successful empty frontier. Initialization creates no fake
work. Add an explicit plan with `--seed PLAN.json`, or later through `seed` and a
revision check. [Native records and evidence](docs/NATIVE.md) describe the inputs.
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
Use `TASKCTL_OFFLINE=1` to require cached operation. Private downloads need an
explicit contents-read credential; no credential is stored in the project.

An agent starts with the generated `AGENTS.md`, `taskctl context`, `doctor`, and
`frontier`. All native commands accept `--repo PATH` and `--format json`. The local
launcher defaults to its own repository, even when called from another directory.
`show`, `roadmap` and `epic` expose the model. `seed` and `close` require the revision
last inspected. `verify` validates supplied evidence without running project code.

## Contracts and composition

- [Native repository, ledger seam, lifecycle and evidence](docs/NATIVE.md)
- [Extensions and provider boundaries](docs/EXTENSIONS.md)
- [Releases, pins, versioning and migrations](docs/VERSIONING.md)
- [Milestone results and current limits](docs/PRODUCTIZATION.md)
- [Planning-model evidence](docs/PLANNING-MODEL.md)

Generators compose `init --contract taskctl.init/alpha1 --format json`; they do
not copy tasking templates. The initializer owns its files and accepts explicit
profile, seed and release lock inputs. Public Kotlin APIs are not required.

## Existing repositories

Existing-code adoption follows the working greenfield path; plain `init` currently
requires an empty directory (an existing `.git` is allowed). It refuses unrelated
source and never initializes or mutates Git itself. Legacy import is the advanced
path after adoption. Historical compatibility code and provenance fixtures exist,
but native inspect/plan/apply migrations are not shipped in this alpha.

## Contributing

Source builds require Java 21: `./gradlew check :cli:installDist` (`./gradlew.bat`
on Windows). `python scripts/package.py` packages the build using `JAVA_HOME`;
`python scripts/bootstrap_test.py` exercises a generated consumer with build tools
removed from PATH. CI also requires byte-identical repackaging. Read [AGENTS.md](AGENTS.md).
The typed value algebra and no-top-type architecture policy are enforced in CI.

## License

The owner has not selected a project software license. See [NOTICE.md](NOTICE.md).
