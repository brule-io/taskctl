# Fedora 0.3.0-alpha.1 RPM evidence

[The immutable RPM release](https://github.com/brule-io/taskctl/releases/tag/rpm-v0.3.0-alpha.1-1)
packages the canonical Linux native archive from
[0.3.0-alpha.1](https://github.com/brule-io/taskctl/releases/tag/v0.3.0-alpha.1).
Tool source is `73fa5fe61ad3ba2488800dc3456ad2838d2dbe71`; packaging/test source is
`1b7a37e88538721f42bfb322dae37e423cc5f1f7`. No taskctl compiler runs during packaging.

| Identity | SHA-256 |
| --- | --- |
| Canonical Linux native archive | `6b6aa76eefac4c364a949c245324c4cb1faa5924a0bcebb7812ef8764063aecf` |
| Unchanged native executable | `d44daf7b88eb6010662941f24f2a055bf528d00d7a17627c8bbccfcf52112131` |
| Binary RPM | `0161a0155d887d898fd5b32ad56afd5e6eb615d05641e493276f72e472149354` |
| Source RPM | `9485cf4bfe2580b46338d34c4b79627698417147f4d85403a89069b5b5e8a2a7` |

[Fedora CI](https://github.com/brule-io/taskctl/actions/runs/34054585202) passed all
11 transaction/runtime checks in a clean Fedora 44 x86_64 container. Install,
version aliases/JSON/help/info, unprivileged init, doctor/frontier, independent
global/pinned command resolution, upgrade and erase all passed. The cached wrapper
still operates after erase. The packaging-release upgrade runs from
`0.3.0~alpha.1-0.fc44` to `0.3.0~alpha.1-1.fc44` with the same native executable.

Actual build/runtime containers use `network=none`; dependency image preparation
uses networking separately. Install/upgrade/erase traces contain zero IPv4/IPv6
socket calls. Transactions preserve project/user bytes, mtimes and modes, including
tasking state, unrelated source, Git sentinels and cache. Package-content checks
reject scriptlets, triggers, project ownership, unexpected locations and JVM
dependencies. Canonical bootstrap assets, README, five guides, LICENSE and notices
are verified against the original archive. Apache-2.0 is recorded in RPM metadata.

The minimal container sets `tsflags=nodocs`, which caused the first test to skip
the correctly packaged documentation. The successful run explicitly clears that
transaction option and verifies the full payload, without editing system config
or making docs mandatory on users' systems. The original failed run was
`34054406234`; no RPM from it was published.

`rpm-build.json` proves a separate offline `rpmbuild --rebuild` of the source RPM
with matching payload digests, modes, ownership, links, EVR and dependencies.
The SRPM contains the checksummed native archive and packaging support, not a new
Kotlin build. This preserves the packaging seam for later COPR use.

`rpmlint.txt` records exit 0, zero errors and five narrowly documented warnings:
two local support-source URL findings, one false positive for the canonical POSIX
wrapper's `hash()` function, and two GitHub transport filename findings. RPM headers
retain `0.3.0~alpha.1`; transport filenames use `0.3.0-alpha.1`. Exact patterns and
reasons are enforced in `packaging/rpm/lint-exceptions.json`. The earlier pending
license exception is gone.

`publication.json` and `release-attestation-verification.json` record the verified
packaging tag and all 11 published asset digests. `rpm-manifest.json`, per-RPM
in-toto statements and `RPM-SHA256SUMS` bind the canonical archive, GraalVM identity,
package identity, Fedora environment and checks. GitHub attests immutable asset
publication; no independent CI OIDC signature, RPM signing key or SLSA level is
claimed. The repository remains private. No COPR project or official Fedora
submission was created, and native protocol v1 remains unfrozen.

```sh
gh release verify rpm-v0.3.0-alpha.1-1 --repo brule-io/taskctl
gh release download rpm-v0.3.0-alpha.1-1 --repo brule-io/taskctl \
  --pattern '*.x86_64.rpm' --pattern RPM-SHA256SUMS
sha256sum --check --ignore-missing RPM-SHA256SUMS
sudo dnf install ./taskctl-0.3.0-alpha.1-1.fc44.x86_64.rpm
taskctl --version
taskctl help
```

[RPM usage](../../../FEDORA-RPM.md) explains initialization, upgrade/removal and
the distinction between system `taskctl` and repository-pinned `./taskctl`.
