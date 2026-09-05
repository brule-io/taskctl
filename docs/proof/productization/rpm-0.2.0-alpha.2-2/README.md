# Fedora RPM publication proof

[rpm-v0.2.0-alpha.2-2](https://github.com/brule-io/taskctl/releases/tag/rpm-v0.2.0-alpha.2-2)
is the first published Fedora RPM packaging of the canonical taskctl alpha.2
Linux native artifact. The tool version remains `0.2.0-alpha.2`; the RPM header
uses `0.2.0~alpha.2-2.fc44`. The companion release is independent of the existing
immutable tar.gz/wrapper release.

[Fedora CI](https://github.com/brule-io/taskctl/actions/runs/33999027802) built and
tested the published packages from a clean checkout. No taskctl compiler ran.
The workflow first verified the canonical release's signature and matched the
downloaded manifest, native archive, release lock and Linux parity/corpus proofs
to signed asset subjects. Native and JVM behavioral parity was already proven
for that exact canonical archive.

The binary RPM and source RPM are accompanied by their spec, build/test evidence,
checksums and in-toto provenance. [publication.json](publication.json) records the
release/tag identity, workflow, exact asset IDs/digests and limitations.
[rpm-manifest.json](rpm-manifest.json) binds the original archive/executable,
GraalVM identity, packaging source, Fedora image and resolved package environment.

## Measured behavior

All 11 transaction/runtime checks passed in a clean Fedora 44 x86_64 container:

- Install with DNF, verifying the installed canonical executable and `rpm -V`.
- `--version`, `-V`, `version`, JSON version, `help` and `info`.
- Greenfield init and mutation-free planning as an unprivileged user, using the
  unchanged canonical bootstrap templates and upstream release lock.
- `doctor` and an empty `frontier`, preserving repository bytes and mtimes.
- Repository-pinned wrapper execution from its independent verified cache.
- Global versus repository-pinned version resolution.
- DNF upgrade from packaging revision 1 to 2, with unchanged native executable.
- DNF erase, removing package-owned files while the cached repository wrapper
  remains usable for doctor/frontier.

Install, upgrade and erase each preserved file bytes, mtimes and modes throughout
the test user's home, including existing/new `.agents`, `.taskctl`, unrelated
source, Git sentinels and wrapper cache. Package-content checks found no scriptlets,
triggers, JVM dependencies or paths outside the declared `/usr` package locations.
The actual package build and runtime containers had `network=none`. Recorded DNF
network traces contain zero IPv4/IPv6 socket calls. Dependency image preparation
uses network access separately; no package transaction acquires dependencies.

The source RPM was rebuilt offline in a separate build directory with ordinary
`rpmbuild --rebuild`. Its payload digests, permissions, ownership, links, EVR and
dependencies matched the candidate package. This proves the repackaging seam for
a future build service; it is not a claim of byte-identical RPM archives or native
compiler output. Source RPM contents include the canonical binary archive and
packaging support, not a new build of the Kotlin implementation.

## Lint and transport

[rpmlint.txt](rpmlint.txt) records exit 0, **zero errors and seven warnings** on the
spec and actual release-named binary/source RPMs. The accepted findings are narrow:

- Two pending-license warnings: no project license has been selected or granted.
- Two source-URL warnings: packaging support is generated locally, checksummed
  and included in the self-contained source RPM.
- One potential-bashism false positive: the canonical wrapper defines its own
  POSIX `hash()` function; it is not using the Bash builtin.
- Two filename warnings: GitHub rewrites `~`, so transport names use the upstream
  SemVer spelling while the RPM headers retain correct prerelease ordering.

Exact diagnostic patterns and reasons are enforced by the repository's
`packaging/rpm/lint-exceptions.json`; unexpected findings fail CI. Fedora's normal
shebang rewriting is excluded only for the canonical bootstrap template, whose
bytes are compared with the original archive. The initial revision-1 draft failed
asset-name verification and was discarded without publication. Revision 2 uses
portable asset names; no published asset was replaced.

## Verify and use

```sh
gh release verify rpm-v0.2.0-alpha.2-2 --repo brule-io/taskctl
gh release download rpm-v0.2.0-alpha.2-2 --repo brule-io/taskctl \
  --pattern '*.x86_64.rpm' --pattern RPM-SHA256SUMS
sha256sum --check --ignore-missing RPM-SHA256SUMS
sudo dnf install ./taskctl-0.2.0-alpha.2-2.fc44.x86_64.rpm
taskctl --version
taskctl help
```

[release-attestation-verification.json](release-attestation-verification.json)
contains the verified GitHub signature and all asset/tag subjects. The release
signature attests publication of the packages, build provenance and evidence;
no separate RPM signing key, CI OIDC build signature or SLSA level is claimed.
The repository is private. Native protocol v1 remains unfrozen, the license
decision remains pending, and no COPR project or official Fedora submission was
created. Existing product repositories were not migrated or modified.
