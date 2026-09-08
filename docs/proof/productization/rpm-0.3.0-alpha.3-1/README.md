# Alpha.3 Fedora RPM publication proof

The [optional RPM companion](https://github.com/brule-io/taskctl/releases/tag/rpm-v0.3.0-alpha.3-1)
is public and immutable. Its 11 asset digests, packaging tag and signed GitHub
release attestation were verified; [publication.json](publication.json),
[RPM-SHA256SUMS](RPM-SHA256SUMS) and the [raw verification](release-attestation-verification.json)
retain those observations. No independent RPM signing key or CI OIDC build
signature is claimed.

[Fedora CI](https://github.com/brule-io/taskctl/actions/runs/34193262329) used clean
Fedora 44 x86_64 containers with package build and transaction networking disabled.
Packaging source is `a604dea2dae662cf57becf13a8b58e013d907230`; canonical tool source
is `084cea04271764f6d666c0db6aa0da517abce7a6`. The native executable is byte-identical
to the [alpha.3 Linux archive](https://github.com/brule-io/taskctl/releases/tag/v0.3.0-alpha.3).
No taskctl compiler was invoked.

The 12 recorded checks cover install, version aliases/JSON/help/info, unprivileged
greenfield init, doctor/frontier, independent global and repository pins, upgrade,
erase, package contents and continued cached local operation after erase. Three
DNF transaction traces report zero Internet socket calls and unchanged project/user
bytes, mtimes and modes. Upgrade is explicitly packaging release 0 to 1 of the same
tool version; this evidence does not claim an alpha.1-to-alpha.3 upgrade trial.

All 99 installed local guide links resolve. Two package-owned relative links retain
canonical README/license references without rewriting guide or license bytes.
The [read-only preflight](guide-preflight.json) records the three formerly missing
targets. The spec uses the documented absolute-path `%doc` form while keeping
license payloads separately classified as `%license`. See the
[RPM specification manual](https://rpm.org/docs/4.20.x/manual/spec.html).
Erase checks owned paths with `lexists`, including possible dangling links.

The source RPM rebuilt offline in an independent build directory with equal
payload digests, modes, ownership, links, EVR and dependencies. rpmlint exited 0:
zero errors and five findings covered by existing exact dispositions (canonical
wrapper hash-function false positive, GitHub tilde filename transport, and local
support-source URLs). No new exception was added. [Raw lint](rpmlint.txt),
[build details](rpm-build.json), [transaction results](rpm-test.json) and
[provenance](rpm-manifest.json) are retained.

Apache-2.0 and canonical third-party notices are included. No shell completions
exist upstream. This optional upstream package does not replace archives or
repository pins and is not a COPR project or official Fedora submission.
Native-v1 remains unfrozen.
