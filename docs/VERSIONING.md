# Versions, releases and migration

Tool releases use exact semantic versions. `0.2.0-alpha.1` publishes an executable
native draft, not frozen native v1. Repository, task, planning, receipt, CLI and
initializer contracts have explicit identities. The public integration boundary
is CLI plus JSON; Kotlin APIs remain internal and may change.

The committed `.taskctl/toolchain.lock` pins a tool version, wrapper version and
SHA-256 for each supported platform. The release includes Java; consumers need no
system JVM or Gradle build. Archives are cached outside the repository by digest.
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

CI packages Windows x86_64, Linux x86_64 and macOS arm64 using the pinned Temurin
21.0.11+10 runtime. Byte-identical repackaging is tested from the same inputs; the
platform runtime itself necessarily differs. Other platforms have no implied support.

Tool upgrades never silently rewrite repository state. Native alpha consumers
must review the selected release's contract support before changing their lock.
No native-version migration is shipped yet. Historical imports remain explicit
future inspect/plan/apply operations with exact source revision, adapter/version,
identity mapping, evidence classification, unsupported semantics and manifest digest.
Imported history must never be retrospectively relabeled as native execution.
