# Historical authority closure evidence

Candidate `c0be11994a16015644387461ed6d356162e306ec` passed [three-platform CI](https://github.com/brule-io/taskctl/actions/runs/34185746092) at checkout
`0ed92695ae422dabc2e46a0566e80d1f10e6bc2f`. Each platform passed 202 source-corpus tests,
199 identical JVM/native behavioral identities and 289 packaged process cases.
[Validation](validation.json) records exact source parents, test identities,
downloaded archive digest checks, offline bootstrap, archive reproducibility and
mutation-free producer reads. The full local JVM check also passed; the six-case
[historical-authority XML](local/) is retained.

The [design](../../HISTORICAL-AUTHORITY.md) reuses only the existing three DAEMON
source witnesses. Exact original bytes, source repositories/revisions and selected
preview adapter versions were checked before closure. A read-only Java probe of
the candidate's compiled adapter produced [source-manifests.json](source-manifests.json):

- `https://gitlab.com/daemon-eng/net/daemon-net-workspace`: `sha256:4c0c5ead56fee9e09df8c239b309e594bfe93c11da9c90abba65c10cd74dedf9`
- `https://gitlab.com/daemon-eng/os/daemon-os-workspace`: `sha256:dc2cdddcc2f7529e4f5b93b0c8c66fb105596873f59e6bcef78e5ee7c3ec6589`

The two OS identities keep their complete names despite sharing TASK.process.006;
the net record keeps TASK.M2.nginx. Archive evidence remains unverified historical
narrative with no native source protocol. The source index remains unchanged.
The probe did not hydrate or access source repositories; it read the preserved
local fixtures and did not create a native import.

Six shared cases prove repository-scoped full identity; exact provenance and
classification; refusal to use an old historical contract as a new native receipt;
later stronger contracts retaining old evidence while unresolved reviews hold
downstream work; optional annotation versus unavailable required capability; and
stale CAS/review refusal with cold reconstruction and unchanged bytes/mtimes.
The newly authored native task attests only archive observation, never independent
truth of the old business claim. No operational DAEMON work is marked migrated.

The separately admitted Value and assertion corrections remain explicit v1 gates;
this receipt closes historical-authority conformance only. Consumer migration,
hydration, external authority, copied-tool retirement and v1 freeze are not implied.
The pinned alpha.2 producer records closure as a legacy actor assertion without
inventing storage acceptance metadata.
