# 0.3.0-alpha.1 publication evidence

[The immutable prerelease](https://github.com/brule-io/taskctl/releases/tag/v0.3.0-alpha.1)
contains native and bundled JVM reference archives for Windows x86_64, Linux
x86_64 and macOS aarch64. All six artifacts come from clean source
`73fa5fe61ad3ba2488800dc3456ad2838d2dbe71`. The native lock is the default; its
SHA-256 is `d4c4893b97b5b3837c2d633236aff7e77fcb186b16133b4001d59bb74d92b2b7`.

[Source CI](https://github.com/brule-io/taskctl/actions/runs/34052801534) passed,
on each platform, 93 source tests, the same 90 behavioral tests on JVM and Native
Image, 90 packaged process comparisons, and 23 consumer checks per implementation.
Seven read-only commands per implementation also inspected the actual self-hosted
22-task, four-roadmap, two-epic repository. Raw CI results are in `ci/`.

[Release transport CI](https://github.com/brule-io/taskctl/actions/runs/34054149302)
repeated the 23 consumer checks per implementation and 90 process comparisons
using actual GitHub release downloads. Raw results are in `release/`. These cover
version/info, initialization/adoption, task and planning graphs, stale CAS,
evidenced closure, transitive currency, reconciliation, diagnostics, exit codes,
cached offline use and mutation boundaries. Native compilation alone was never
the acceptance criterion.

`release-manifest.json`, `SHA256SUMS`, both locks and the in-toto statements bind
the archive digests, source identity, GraalVM/build identity and Apache-2.0 license.
`publication.json` records the published asset IDs, digests, source tag and CI runs.
`release-attestation-verification.json` contains the verified signature and subjects
for all 28 assets and the source tag:

```sh
gh release verify v0.3.0-alpha.1 --repo brule-io/taskctl --format json
```

GitHub's signature attests immutable publication of these assets, including build
provenance and test evidence. It is not an independent observation of each build
step; no separate CI OIDC build signature or SLSA level is claimed.

## Cold self-host consumer

`cold-selfhost-windows.json` records 14 checks using only committable tasking state,
AGENTS.md and wrappers copied outside the source tree. PATH contains only Windows
system utilities, JAVA_HOME names an absent directory, and the initial cache is
empty. All version forms leave that cache absent. Offline doctor fails before
acquisition; authenticated acquisition of the published native artifact succeeds.
Doctor/context/frontier/status/affected/planning queries then work offline without
credentials. Every repository file preserves its bytes and mtime. The graph
correctly identifies the unfinished RPM task at the time of this observation.

The actual repository pin now uses those same published URLs and digests.
`cold-selfhost-windows-final.json` repeats all 14 checks after the release closure
receipts were admitted. It finds all 22 tasks current, 11 closed tasks, and exactly
`TASK.migration.fantastikt` in the frontier. The first observation is retained
unchanged; historical proof is not rewritten to imply earlier completion.

```powershell
$env:TASKCTL_OFFLINE = '1'
C:/projects/tasking/taskctl.ps1 --version
C:/projects/tasking/taskctl.ps1 doctor
C:/projects/tasking/taskctl.ps1 context
C:/projects/tasking/taskctl.ps1 frontier
C:/projects/tasking/taskctl.ps1 status
```

## Compatibility and limits

See [the semantic contract](../../../SEMANTIC-0.3.md) and
[milestone report](../../../MILESTONES-0.3.md). Core-draft-2 and native repository
alpha2 are explicit new versions; old record digests and historical receipts keep
their original meaning. Older writers reject the new repository marker. Native
v1 remains unfrozen. The repository remains private and release acquisition needs
read access, even though the protocol/reference tooling is licensed Apache-2.0.

Repackaging identical inputs is byte-identical; independent Native Image compiler
reproducibility is not claimed. Linux archive tests use Ubuntu 24.04; Fedora RPM
tests provide a separate OS packaging proof. Other libc/older OS support is not
implied. No product repository migration or service implementation is part of this
release. The `local/` directory preserves earlier explicitly dirty candidate proof;
published artifact identity comes from the clean CI and release records above.
