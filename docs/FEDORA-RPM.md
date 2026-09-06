# Fedora RPM distribution

The RPM is an optional packaging of the canonical Linux x86_64 Native Image
archive. Its executable must match the archive's GraalVM executable digest exactly.
No Kotlin/JVM/Native Image compilation occurs in the RPM build, and no protocol
semantics change. The normal tar.gz, release locks and repository wrapper remain
independent of the RPM workflow and publication.

The test target is [Fedora 44](https://www.fedoraproject.org/workstation/download/),
the current stable release when this packaging was introduced. The official
container image is pinned by digest in the Dockerfile; resolved dependency versions
are recorded in build/test provenance. RPM dependencies on native shared libraries
are generated from the ELF rather than guessed or replaced by a JVM dependency.

## Install and use

Download the binary `*.x86_64.rpm`, `RPM-SHA256SUMS`, and associated evidence from
the RPM GitHub release. Verify the release attestation and the package checksum
before installing; these initial packages are not signed with a separate RPM key.
The repository remains private, so release downloads require read access.

```sh
gh release verify rpm-v0.2.0-alpha.2-2 --repo brule-io/taskctl
gh release download rpm-v0.2.0-alpha.2-2 --repo brule-io/taskctl \
  --pattern '*.x86_64.rpm' --pattern RPM-SHA256SUMS
sha256sum --check --ignore-missing RPM-SHA256SUMS
sudo dnf install ./taskctl-0.2.0-alpha.2-2.fc44.x86_64.rpm
taskctl --version
taskctl help
taskctl init --repo ./my-project --id example.my-project \
  --toolchain /usr/share/taskctl/toolchain.lock
cd my-project
taskctl doctor
taskctl frontier
./taskctl --version
```

`taskctl` uses the system package and the current directory or explicit `--repo`.
`./taskctl` uses that repository's exact committed pin. A global upgrade never
changes the pin, and a pinned wrapper never falls back to the global executable.
The generated wrapper may acquire its own archive on first use; private downloads
use `TASKCTL_GITHUB_TOKEN` or `GH_TOKEN`. `TASKCTL_OFFLINE=1` requires its cache.

```sh
sudo dnf upgrade ./taskctl-NEW-VERSION-RELEASE.fc44.x86_64.rpm
rpm -V taskctl
sudo dnf remove taskctl
```

DNF may download normal OS dependencies when enabled. The taskctl package itself
has no transaction scripts, triggers, services, repository configuration, signing
key installation, downloads or project initialization. It owns only these paths:

| Location | Purpose |
| --- | --- |
| `/usr/bin/taskctl` | Small system launcher |
| `/usr/libexec/taskctl/taskctl` | Unchanged canonical native executable |
| `/usr/share/taskctl/` | Canonical bootstrap templates/build identity and release lock |
| `/usr/share/doc/taskctl/` | RPM usage documentation |
| `/usr/share/licenses/taskctl/` | Apache-2.0 LICENSE and original third-party notices |
| `/usr/share/man/man1/taskctl.1.gz` | Manual page |

The RPM owns no `.agents`, `.taskctl`, repository launchers, home directories or
wrapper caches. Erase leaves those intact, including cached `./taskctl` execution.
No shell completion files are currently available upstream. When they exist, they
can be installed as ordinary package files without transaction scripts.

## Packaging and release gates

The optional [RPM workflow](../.github/workflows/rpm.yml) consumes a published
canonical release. It verifies its signed release attestation, then binds the
downloaded archive, manifest, lock and Linux corpus/process evidence to those
verified subjects. The native and JVM behavioral counts must match, and the
process evidence must identify that exact native archive and JVM reference.

```sh
gh workflow run rpm.yml --repo brule-io/taskctl \
  -f upstream_tag=v0.2.0-alpha.2 -f packaging_release=2
```

Dependency images are prepared with network access. The actual rpmbuild and runtime
test containers then run with `--network none`; no host directories are mounted.
The build produces a binary RPM and a standard source RPM. The spec verifies its
source hashes and all canonical archive files. Stripping/debug-package generation
is disabled to preserve the proven executable; the staged and installed bytes are
checked against it. Package-content checks reject transaction scripts, unexpected
paths and JVM dependencies. rpmlint runs on the spec, binary RPM and source RPM.
Raw findings are retained; any accepted finding needs an exact diagnostic pattern and reason in
`packaging/rpm/lint-exceptions.json`, and unexpected findings fail the build.

The clean runtime test installs a baseline packaging revision, checks version/help
and info, creates a greenfield repository as an unprivileged user, runs doctor and
frontier, upgrades to the candidate packaging revision, and erases the package.
It checks file bytes, mtimes and modes in existing and newly created repositories
and the user's cache before and after every DNF transaction. It also compares
global/pinned version resolution and runs the cached wrapper after erase. Network
syscall traces must contain no IPv4/IPv6 socket calls during those transactions.
The upgrade fixture uses the same native executable with an earlier RPM Release;
it proves package lifecycle behavior without inventing another tool implementation.

`scripts/release_rpm.py` assembles a draft from a successful optional workflow run.
It defaults to a companion `rpm-vVERSION-RELEASE` tag because existing tool releases
are immutable. `--tag` may target an existing draft for future combined assembly;
assets are never overwritten. RPM-specific checksums, manifest and provenance are
additive, so ordinary tar/wrapper publication needs no RPM result. After verifying
the draft's asset digests, the maintainer publishes it immutably. Provenance binds
the canonical archive/executable and GraalVM identity, packaging source, Fedora
environment, spec/support sources and tests. The GitHub release signature attests
publication; it is not an independent CI build signature or SLSA-level claim.

## License decision and future COPR seam

taskctl is licensed under Apache-2.0. The RPM carries LICENSE as `%license`,
records Apache-2.0 in its metadata, and retains the original third-party notice
bundle. The former pending-license rpmlint exception has been removed.

The source RPM includes the spec, checksummed canonical native archive and support
sources. `rpmbuild --rebuild PACKAGE.src.rpm` requires no GitHub credentials or
compiler and performs no downloads. This is a repackaging source RPM, not an
archive of the Kotlin sources. The spec uses normal Source/BuildRequires/files
sections and independent Version/Release fields so a later COPR job can consume
the same SRPM subject to service eligibility. No COPR project
or official Fedora inclusion request is made now.

The build gate also runs `rpmbuild --rebuild` on the generated SRPM in a separate
build directory, offline. Its resulting payload digests, modes, ownership, links,
version/release and dependencies must match the candidate RPM.

Pre-release versions map `0.2.0-alpha.2` to RPM `0.2.0~alpha.2`, with packaging
revisions in Release; this preserves alpha-before-final ordering under
[RPM's version rules](https://rpm.org/docs/latest/man/rpm-version.7).
GitHub rewrites `~` in uploaded asset names, so release filenames use the upstream
SemVer spelling (`0.2.0-alpha.2`); RPM headers retain `0.2.0~alpha.2`. DNF compares
the headers, not the download filename. Rpmlint checks the actual transport names.
