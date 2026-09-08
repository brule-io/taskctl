# Related-ledger closure evidence

The [design and limits](../../RELATED-LEDGERS.md) describe two fictional source
ledgers and explicit manual observation contracts. `TASK.migration.daemon-descendants`
is public conformance work, not an operational DAEMON migration.

Candidate `1759247376107c56a6b9e48af5ef0152a0dc54f8` passed [three-platform CI](https://github.com/brule-io/taskctl/actions/runs/34183099385) at exact checkout `f02b39800fe2aa0c405bc01899d56565805f1f08`.
Each platform passed 190 source-corpus tests, 187 identical JVM/native behavioral
identities and 289 packaged process parity cases. [validation.json](validation.json)
binds source parents, jobs, test identities and digest-verified downloaded artifacts.
Offline bootstrap, archive reproducibility and mutation-free producer reads also
passed. [Local Windows evidence](local/) independently passed 187 behavioral and
289 process cases with artifact digests checked before copying.

Eight independent source files originate at
`da0bd033a3cce21af5eeebeb78d70bbf069d4f16`. The versioned adapter is
`conformance-related-ledger` / `1.0.0`, with selected dialect `specimen.related/1`.
The compiled test adapter's inspected manifest identities are:

- alpha: `sha256:41d1dd6f66711527161778e5b69b5530f923c87db1fc42fbc3eb85cbe916cfd3`
- beta: `sha256:fa8e3efe71c6479810a23773cf6332dcecc7a69e48ae1f52bc239fe99921fa87`

Six shared file-ledger cases prove distinct repository identity with equal short
task names/contracts; exact source/old-contract assertions; explicit refresh of
repository/task/contract/transitive-input observations; cosmetic HEAD equivalence;
transitive currency after intermediate review; stale CAS/review refusal; cold
reconstruction and unchanged other-ledger/product bytes and mtimes. Crucially,
the local ledger remains current against its recorded witness until an explicit
refresh. The experiment does not claim automatic external freshness or a resolver.

The separate red witness `87afe089d9afbc85bf1cd0e4b6b4e0c2ea96dae6` demonstrated
a bootstrap plan accepting changed borrowed source bytes under a cached reviewed
manifest ID. [The completed failing log](import-borrowing-red.log) preserves that
result. Three shared regressions now prove source/projection/provenance mismatch
rejection before planning/writes and acceptance after a new manifest plus explicit
new review. Existing valid hashes and historical evidence remain unchanged.

The first native attempt omitted the new resource directory; the final candidate
adds only `related-ledger/.*` to the test resource list. No broad reflection
allowance or core semantic accommodation was added for Native Image.

No consumer hydration/mutation, copied-tool retirement, provider activation,
remote authority, deployment or v1 freeze follows. Historical narratives remain
unverified source assertions. The producer's published alpha.2 pin closes this
task with a legacy actor assertion, without invented storage acceptance metadata.
