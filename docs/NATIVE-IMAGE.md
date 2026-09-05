# Native executables and the JVM reference

The alpha ships two implementations of the same protocol and lifecycle. Native
executables are built with digest-pinned GraalVM Community; the JVM archive keeps
the bundled Temurin Java 21 reference. No task schema, semantic digest, evidence
classification, graph rule or lifecycle transition differs by implementation.

## Version and diagnostic commands

`taskctl --version`, `taskctl -V` and `taskctl version` print one line and exit 0.
`taskctl version --format json` returns:

```json
{"api":"taskctl.cli/alpha1","command":"version","result":{"tool":"taskctl","version":"0.2.0-alpha.2"}}
```

These commands do not discover or read a task repository. In a generated launcher,
they report the committed tool version even with an empty cache: they neither
download nor initialize a cache. `info` acquires the pinned artifact if needed and
reports its actual implementation, runtime and build identity. CLI stdout/stderr
use UTF-8, including redirected Windows output.

## Common execution contract

Lock format 2 is retained. Wrapper version 3 accepts both wrapper 2 and wrapper 3
pins. New archives declare `launcherContract=taskctl.launcher/1` and an entry point
in their verified `distribution.properties`. The launcher verifies the archive
and every manifest-listed file, sets the distribution/repository context, and
executes that entry point. Runtime selection belongs inside the artifact.

On Unix the entry point is `taskctl` (a native executable or JVM launch script).
On Windows it is `taskctl.exe` or `taskctl.ps1`. Native Windows archives also carry
PowerShell/batch convenience launchers. JVM entry points use an exact library
list, so an extra JAR in a cache never enters the classpath. Native archives carry
no JVM or application JARs. Older published JVM archives remain supported by the
launcher's explicit compatibility path.

The release's `toolchain.lock` selects native only after both implementations pass
the release gates on all three platforms. `toolchain-jvm.lock` selects the reference
implementation explicitly. Neither failure nor an unsupported platform triggers
an implicit implementation switch.

## Build and parity gates

[packaging/graalvm.json](../packaging/graalvm.json) pins the GraalVM release, platform
URLs and SHA-256 digests. `python scripts/graalvm.py` verifies the build toolchain
and writes `build/graalvm-home.txt`. Set `GRAALVM_HOME` to that directory; retain
Java 21 for the Gradle/Kotlin toolchain. Windows builds need the Visual C++ build
environment; CI initializes it explicitly.

```text
./gradlew check :cli:installDist
./gradlew :cli:nativeCompile :native-tests:nativeTest --max-workers=1
python scripts/corpus_proof.py
python scripts/package.py --kind jvm
python scripts/package.py --kind native
python scripts/bootstrap_test.py --kind jvm
python scripts/bootstrap_test.py --kind native
python scripts/parity_test.py
```

The `native-tests` project reuses the original compiled behavioral tests. The
corpus gate requires identical, successful test identities on JVM and Native
Image, with no missing or skipped tests. The three Kotlin compiler/PSI architecture
tests run as source checks on the JVM; they are not runtime behavior. Native test
resources include only the existing DAEMON and planning-history fixture trees.

Process parity runs both artifacts against identical repository preimages and
compares outputs, exit codes, resulting file bytes and modes. It also proves
read-only byte/mtime preservation and bounded mutations, including unrelated
source and Git sentinels. Only `info`'s implementation, runtime and build fields
may differ, and those are checked against the actual artifact metadata. This
comparison exposed a Windows output-encoding difference, fixed at the process
boundary without changing protocol semantics.

The bootstrap suite runs on both artifacts with build tools removed from consumer
PATH. It covers cold acquisition, offline reuse, version without acquisition,
corrupt cache/digest/version rejection, greenfield reconstruction, graph filters,
CAS, evidence and closure, and generator composition. Release smoke tests repeat
these operations with the real GitHub download URLs.

Production metadata includes only the exact `VERSION` resource. The shared tests
use Native Build Tools' JUnit support; test metadata stays out of the production
binary. No general reflection configuration or reachability-metadata repository
is enabled. The builds use `-march=compatibility`; supported hosts remain Windows
x86_64, Linux x86_64 (tested on Ubuntu 24.04) and macOS arm64. Other Linux libc or
older OS baselines are not implied by the architecture name alone.

## Provenance and attestations

Every artifact identifies `implementation: native|jvm`, source revision, input JAR
digests and its build tools. Native records additionally bind the GraalVM release,
toolchain download digest, Native Image identity/options and executable digest.
Each archive has an in-toto/SLSA provenance statement. The release manifest binds
all statements, corpus/process proofs, both locks and all platform archives.

These provenance statements are release assets covered by GitHub's signed immutable
release attestation. Verify it with `gh release verify TAG --repo brule-io/taskctl`;
then verify the asset digests and statement subjects. The statement describes the
build; the GitHub release signature attests its publication, not independent
observation of every build step. No separate CI OIDC signature or SLSA level is
claimed. GitHub's per-build artifact attestations require Enterprise Cloud for
private repositories; the current organization uses Team. This does not require
changing repository visibility or publishing private witnesses.

Byte-identical repackaging is tested from the same build/runtime inputs for both
formats. It is not a claim that separate Native Image compiler invocations produce
identical executables.

References: [Native Build Tools testing](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#testing-support),
[GraalVM toolchain release](https://github.com/graalvm/graalvm-ce-builds/releases/tag/graal-25.3.4.1),
[GitHub artifact attestation availability](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations).
