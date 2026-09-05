# Extraction proof — 2026-09-04

**Passed on Windows x64 and aibox Linux x64.**

The canonical build passed **61 tests** (9 core, 44 compatibility, 8 conformance), with zero failures or skips. The independently built donor oracle passed its original **44 tests**.

Both archives bundle Eclipse Temurin **21.0.11+10**. Consumer proof commands ran with an invalid `JAVA_HOME`, no system JVM/Gradle/Git in their command path, an empty Gradle home, and a deliberately failing consumer build.

| Platform | Archive SHA-256 |
| --- | --- |
| windows-x86_64 | `7becfde61783ff0cd0c12c69064743cf813282ad0d82dce3cf51d6ebdccbe7eb` |
| linux-x86_64 | `7db7a1cd9fa977cba7351174e6ad7ea6dadafd7569822b0c8f547809ad9bf31e` |

The frozen Fantastikt specimen contains **40 tasks: 11 open, 29 closed**, plus one roadmap and one epic. `doctor`, `frontier`, `plan`, `list`, and `show` match the independent donor CLI. Windows and Linux produce identical task identities, state, prerequisites, graph layers, frontier, and snapshot IDs.

Semantic snapshot:

```text
sha256:5b8fd9d62f7eeca2bce2bf1b7a468d3c6cef007155ea5cdefc3718c7e7827499
```

The integration runs also proved:

- Cold checksum-pinned acquisition and warm offline execution leave consumer files unchanged.
- Read commands do not create `.agents/.lock`; rejected reads preserve content and modification times.
- Stale snapshots, unsupported schemas and unsatisfied prerequisites fail explicitly.
- Closure receipts match the donor, apart from their independently generated timestamp.
- Closure and fault-injected recovery change only the selected open/closed task records and the declared lock/transaction state; unrelated staged/unstaged files and `.git` bytes remain unchanged.
- Arguments retain spaces, embedded quotes, shell metacharacters and trailing backslashes on Windows PowerShell and POSIX shell.
- Wrong checksums, duplicate lock keys, version mismatches and tampered cache files fail closed.
- Linux ledger symlinks cannot redirect reads or closure into unrelated files.

Core conformance covers the sealed value algebra, exact large-number preservation, unknown extension round trips, namespace rejection, semantic contract changes, historical receipt binding, provider activation/constraints and contributed-edge cycles. The PSI policy has negative fixtures for forbidden top types and suppression escapes.

DAEMON conformance retains the full identities of both `TASK.process.006.*` specimens and the historical closed state/narrative of `TASK.M2.nginx` despite its retained unchecked criteria. Import previews record exact source revisions, adapter versions, source hashes, contract digests and a manifest ID; none is represented as native-v1 evidence.

Machine-readable evidence:

- [Windows run](proof/windows-x86_64.json)
- [Linux run](proof/linux-x86_64.json)
- [Test counts](proof/test-results.json)
- [DAEMON import preview](proof/daemon-import-preview.json)
- [Concrete two-host lock](proof/toolchain.lock)
- [Changed donor files](../provenance/extraction-changes.json)

Archives are in `build/distributions/`. Disposable consumers remain at the run directories recorded in each report. The remote proof is under `/home/developer/Developer/src/tasking-extraction-proof-20260904`.

This proves the first external implementation boundary. Native protocol freezing, full dialect migration, production provider loading, private-registry publication, generator integration and consumer adoption remain later milestones. The GitLab CI configuration is present; no remote pipeline or publication was triggered.
