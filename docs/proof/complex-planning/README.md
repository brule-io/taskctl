# Complex planning closure evidence

This evidence covers the general conformance contract of `TASK.migration.loom`.
It does not admit an operational Loom migration or implement release gates.
See [COMPLEX-PLANNING.md](../../COMPLEX-PLANNING.md) for the fixture and limits.

[CI 34177026855](https://github.com/brule-io/taskctl/actions/runs/34177026855)
passed candidate `33e3a7f04ffbb4475c52808fcf532e20231d1c8e` at checkout
`2fcc5eb00353914a77ad37ae4f4de605c9a19014`. Each of Linux x86_64,
Windows x86_64 and macOS aarch64 passed 167 source tests (including three
architecture checks), 164 identical JVM/Native Image behavioral tests and 289
packaged process parity cases. [validation.json](validation.json) binds the
candidate, checkout parents, jobs, test identities and digest-verified downloaded
artifacts. Archive reproducibility, offline cached bootstrap and mutation-free
producer inspection also passed for both implementations.

The combined candidate includes the planning-history and provider/profile
implementations. Eight shared cases exercise the fictional historical import,
explicit mapping reviews, overlapping scopes, audited planning history, amendments,
archival, assessments, stale CAS and direct/transitive evidence drift. Thirty-one
additional process cases feed the canonical imported preimage to both ordinary
executables and compare output, status and exact file effects. Historical source,
import witnesses and old receipts remain intact; all task closure never implies
scope acceptance. The initial candidate separately passed 147 behavioral tests
and 243 process cases locally on Windows and on all three CI platforms.

Thirteen independently authored source files are pinned at public fixture commit
`445b637b02c4451977e015af5d6301132f8db75c`. The adapter is
`conformance-planning-indexes/1.0.0`; its manifest identity is
`sha256:7c6844313efb6601b1839356790abaabcd44e40df1369c17b62725ac55f9acee`.
The test-local adapter rejects unsupported structural and stateful semantics and
is not installed in the product CLI. No private consumer material is published.

The producer retains its published alpha.2 pin and records a legacy actor assertion
without invented authoritative acceptance time. This closure proves the bounded
compatibility task, not consumer adoption, deployment, a release or native-v1 freeze.
