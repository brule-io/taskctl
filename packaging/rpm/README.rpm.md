# taskctl system package

The RPM packages the canonical parity-tested Linux x86_64 native artifact.
It does not compile taskctl or change protocol semantics. The project software
license remains undecided: `LicenseRef-taskctl-license-pending` records that fact
and grants no license. NOTICE.md and the canonical third-party notices are installed
under `/usr/share/licenses/taskctl`. No shell completion files exist upstream yet.

Install a verified local RPM with `sudo dnf install ./taskctl-*.x86_64.rpm`.
Upgrade with `sudo dnf upgrade ./taskctl-*.x86_64.rpm`; remove with
`sudo dnf remove taskctl`. DNF may acquire normal system dependencies when enabled;
the package itself has no install/remove scripts, triggers, services or downloads.
RPM file digests support `rpm -V taskctl`. Initial GitHub artifacts are covered by
checksums and immutable release attestation, not an RPM signing key. Check those
before installation; no repository or signing-key configuration is installed.

The PATH command `taskctl` runs the system package. `./taskctl` runs the repository's
committed wrapper and exact lock, independently of the system package. Installing,
upgrading or erasing the RPM never changes `.agents`, `.taskctl`, repository
launchers, or the user's wrapper cache. Erasing the RPM leaves cached `./taskctl`
usable. A repository lock never falls back to the global executable.

```sh
taskctl --version
taskctl help
taskctl init --repo ./my-project --id example.my-project \
  --toolchain /usr/share/taskctl/toolchain.lock
cd my-project
taskctl doctor
taskctl frontier
./taskctl --version
```

The bundled lock is the canonical upstream release lock and is copied only by an
explicit `init` command. A generated wrapper downloads its own archive on first
execution; private releases require `TASKCTL_GITHUB_TOKEN` or `GH_TOKEN` with read
access. Credentials never enter package metadata or the project lock. After
acquisition, `TASKCTL_OFFLINE=1 ./taskctl doctor` uses the verified cache.

No existing project is adopted or migrated by installing this package. Native
protocol v1 remains unfrozen. See https://github.com/brule-io/taskctl for protocol,
bootstrap, Native Image and licensing documentation.
