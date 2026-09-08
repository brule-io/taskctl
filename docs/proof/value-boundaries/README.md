# Value boundary closure evidence

Candidate `fe46c13376a0155eb8f7c182df86351179556909` passed [three-platform product CI](https://github.com/brule-io/taskctl/actions/runs/34185422564)
at checkout `e580a928574b7656eba6117db9b3a3a25369b3ed`: 196 source-corpus tests, 193 identical
JVM/native behavioral identities and 289 packaged process cases per platform.
[Validation](product/validation.json) records exact source parents, test identities
and digest-verified downloaded archives. Offline acquisition, reproducibility and
mutation-free producer reads also passed. [Local Windows evidence](local/) passed
193 behavioral and 289 process cases; both packaged archive digests were checked.

The same candidate passed the separate [kernel run](https://github.com/brule-io/taskctl/actions/runs/34185422567)
and [IDL/client run](https://github.com/brule-io/taskctl/actions/runs/34185422539); exact completed jobs are retained
in [integration-runs.json](integration-runs.json). Their test identities remain
the existing 3+10 kernel and 4+2+8 JVM/Python integration suites, outside the shared
native corpus.

The [boundary design](../../VALUE-BOUNDARIES.md) describes owned unmodifiable
Value collections and shared task/planning constructor checks. Source
`ac4c46fc63299cabd805e61ebb6fe97601099162` reproduced three of four ownership
failures and both record-input failures. The completed red logs/XML are retained.
The earlier [Java probe](baseline.json) produced an undecodable bootstrap plan
against `1759247376107c56a6b9e48af5ef0152a0dc54f8` without applying it.
All six shared cases now pass. Old valid hashes, Unicode and precise numeric
category/scale retain their meaning; no unchecked cast or broad reflection
allowance was introduced.

A separate profile-audit/evidence alias defect was subsequently reproduced and
admitted as `TASK.protocol.assertion-boundary`; the v1 assessment retains that
explicit gate. This closure proves the bounded Value and draft-record correction,
not universal DTO immutability, concurrency safety or protocol freeze. Consumer
repositories remain unchanged. Closure uses the pinned producer's legacy actor
assertion and does not invent authoritative acceptance time.
