# Versions, releases and migration

The 0.3 candidate adds history-backed native alpha2, core-draft-2 semantic
contracts, observed dependency currency, and explicit reconciliation. See
[SEMANTIC-0.3.md](SEMANTIC-0.3.md) for the current changes and compatibility rules;
older draft examples below retain their original versioned meaning.

Tool releases use exact semantic versions. `0.2.0-alpha.2` adds native executables while
retaining the JVM reference; the native task protocol remains unfrozen. Repository, task, planning, receipt, CLI and
initializer contracts have explicit identities. The public integration boundary
is CLI plus JSON; Kotlin APIs remain internal and may change.

The committed `.taskctl/toolchain.lock` pins a tool version, wrapper version and
SHA-256 for each supported platform. The default lock selects native executables;
`toolchain-jvm.lock` selects a reference archive with bundled Java. Consumers need
no system JVM or Gradle build. Archives are cached outside the repository by digest.
Every invocation verifies the archive and extracted manifest-bound files. Extra
JARs in a cache do not enter the classpath. Unknown lock fields are rejected.

Releases use GitHub Releases with repository-level immutability enabled. Assets
are uploaded to a draft, verified, and only then published. Never replace bytes
under an existing release identity. `scripts/release.py` assembles the draft from
matching clean CI artifacts and their bootstrap proof. `SHA256SUMS`, the release
manifest, GitHub asset digests and the toolchain lock identify the same archives.

The lock uses GitHub's asset API transport. Private downloads require an explicit
`TASKCTL_GITHUB_TOKEN` or `GH_TOKEN` with repository contents-read permission.
Credentials are not written into a project or lock. The launcher sends them only
to the GitHub API origin, not redirect destinations. Public repositories can use
the same endpoint without credentials. See [GitHub's asset download contract](https://docs.github.com/en/rest/releases/assets#get-a-release-asset).

CI packages Windows x86_64, Linux x86_64 and macOS arm64 using digest-pinned GraalVM
Community and the Temurin 21.0.11+10 reference runtime. Byte-identical repackaging is tested from the same inputs; the
platform runtime itself necessarily differs. Other platforms have no implied support.

Tool upgrades never silently rewrite repository state. Native alpha consumers
must review the selected release's contract support before changing their lock.
No native-version migration is shipped yet. Historical imports remain explicit
future inspect/plan/apply operations with exact source revision, adapter/version,
identity mapping, evidence classification, unsupported semantics and manifest digest.
Imported history must never be retrospectively relabeled as native execution.

## Alpha-1 launcher upgrades

Wrapper version 3 introduces the artifact entry-point contract. Its lock syntax
remains format 2, and it can also run the original wrapper-2 JVM archives. An
alpha-1 launcher cannot execute a native alpha-2 archive by changing only its URL.

For an existing alpha-1 consumer, explicitly update `taskctl`, `taskctl.ps1` and
`taskctl.bat` from the verified new release's `bootstrap/` directory, and replace
`.taskctl/toolchain.lock` with that release's selected native or JVM lock. Review
and commit these four bounded changes; preserve the Unix executable bit. This
does not require changing any `.agents` records, graph identities or receipts.
There is no automatic task-state migration or implicit Git operation.

Version queries report the committed pin without acquisition. Use `info` to
inspect the actual verified executable and its build identity. The default native
lock is emitted only after all native/JVM corpus and process parity gates pass.
See [native builds and signed release provenance](NATIVE-IMAGE.md).
