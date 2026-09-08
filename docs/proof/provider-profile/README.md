# Effective provider/profile closure evidence

This evidence covers `TASK.protocol.provider-profile` and the boundaries in
[EFFECTIVE-PROFILES.md](../../EFFECTIVE-PROFILES.md). It is development conformance,
not a release, production provider acquisition path or consumer adoption.

[CI run 34175294571](https://github.com/brule-io/taskctl/actions/runs/34175294571)
passed candidate `a7df73311e14b2d43bf563a76fc6831c0a706c22` on Linux x86_64,
Windows x86_64 and macOS aarch64. Each platform passed 159 source tests, including
three architecture policy checks, 156 identical JVM/Native Image behavioral tests
and 258 packaged process parity cases. [validation.json](validation.json) binds
the exact candidate, checkout `80102851183be6080b4de9cda7379b093974de47`, parents,
jobs, test identities and artifacts. Downloaded archive bytes were checked against
their metadata digests. Local Windows also passed all 156 behavioral tests and
258 process cases; abstract-machine callers compiled.

Shared core/file cases cover exact typed pins, canonical provider inputs, immutable
profile audit history, contributed DAGs, rejected evidence, missing/mismatched/failed
providers, transitive drift and explicit profile-bound revalidation. Historical
edges are retained without mixing old and current graphs into false cycles. Pin
replacement requires review; exact semantic restoration follows the existing
contract-equivalence rule. Unknown optional values and old hash/envelope witnesses
remain intact. Planning evidence distinguishes changed semantics from unavailable
evaluation. Cold reconstruction, policy/object tampering and interrupted journal
recovery are tested through the file boundary.

The packaged CLI corpus covers explicit alpha5 empty-profile adoption, bounded
config/policy/history writes, unchanged task/import/planning state, review/3,
revision/3, verify/close and historical receipts. It refuses unavailable pins and
stale CAS/profile HEADs while preserving inspection. CI additionally verifies
archive reproducibility, cached offline bootstrap and read-only producer inspection
for both implementations. Only diagnostic build/runtime fields may differ.

Review confirms that the shared reducer retains graph, lifecycle, acceptance,
currency and CAS authority. Providers receive canonical semantic input and have
no transition/storage API. Registration is a trusted code boundary whose artifact
authenticity must be established externally; matching fictional implementations
are exercised in the shared test corpus, not shipped as approval capabilities.
The CLI has no runtime loader. Environment probes remain explicit and separate;
this change does not implement host/operator semantics or admit stateful imports.

The producer retains its published alpha.2 pin and does not adopt profile storage.
Its closure receipt uses that pin's legacy actor-assertion envelope, with no invented
authoritative acceptance time. No consumer migration, deployment or v1 freeze follows.
