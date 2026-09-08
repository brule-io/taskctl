# Persisted effective provider semantics

This describes development after `0.3.0-alpha.2`. It does not publish a release,
upgrade existing consumers or freeze native v1. The published minimal profile and
all prior task, review, receipt, import and planning envelopes retain their original
meanings. Upgrade a repository's binary before explicitly adopting this draft.

The ledger now persists a complete effective profile and its immutable audit
history. Contracts, current dependency evaluation, revision observations, currency,
reconciliation, closure and planning assessment binding use the same profile.
Changing a profile leaves old task revisions and receipts intact and exposes the
new semantics as work to review. Provider installation alone never activates it.

## Identities and authority

An `EffectiveProfile` has a namespaced `ProfileId` and a map from validated
`ExtensionId` values to exact `ProviderPin` values. Each pin contains a separate
`ProviderId`, an exact `ProviderVersion` label and a SHA-256 `ProviderDigest`.
Version labels are bounded ASCII identifiers, not a claim of SemVer ordering.
The core compares the entire pin with the trusted registry's descriptor; it does
not hash or authenticate an injected Kotlin object's implementation. Build or
dependency acquisition must verify the implementation's artifact/source identity
before registration. This draft has no production provider acquisition path and
makes no independent authenticity claim for the fictional conformance providers.
`ProfileDigest` identifies the complete canonical profile. `ProfileRevisionId`
identifies an immutable profile change including its parent, inspected ledger
revision and actor audit. All identity constructors are private and validated.

`PinnedSemanticProvider` contributes deterministic record validation, prerequisite
identities, readiness blockers, evidence requirements and evidence verification.
Its `ProviderTask` input contains canonical semantic core data, its own activated
payload and the exact effective contract digest. It cannot inspect incidental
title/layout, lifecycle state or inactive optional annotations through this input;
the legacy draft-1 title remains visible only because that dialect made it semantic.
Semantic prose is normalized before reaching providers. This prevents code from
depending on record data excluded from its digest. It receives no transition or
storage API. The core validates identities, the entire
contributed graph, lifecycle, acceptance, currency and CAS; it retains all transition
authority. Missing pins, unavailable or mismatched code, failed evaluation, cycles,
dangling prerequisites and rejected evidence cannot confer execution or revalidation.

`ProviderRegistry` is an explicit adapter/programmatic dependency. It is transient
and is never serialized as ledger state. Exact matching implementations can be
injected into `FileTaskLedger`; the shared JVM/native corpus exercises that seam.
This is a trusted code boundary, not a sandbox for arbitrary Kotlin. Implementations
must obey deterministic evaluation and the separate probe contract. The CLI has
**no runtime provider loader and ships no placeholder approval provider**. Its default
registry is empty. A profile with unavailable pins can be inspected but cannot
execute work; concrete bundled capabilities and legacy mappings remain separate work.

`EnvironmentProbe` remains a separate explicit interface. Neither profile loading
nor deterministic evaluation calls it, runs a process, reads a network resource or
executes evidence text. Any future capability that uses a probe result must require
explicit evidence bound to the reviewed contract/inputs; a raw probe response is
not an approval token. This draft does not claim a host/operator capability or
implement stateful legacy imports. Required planning capabilities also remain
separately unavailable; a task-only implementation cannot impersonate them.

## Explicit profile changes

Inspect the current repository and profile HEAD:

```text
taskctl doctor --format json
taskctl profile show --format json
taskctl profile history --format json
```

An unadopted repository reports an untracked profile history with null HEAD, while
retaining its historical `minimal/alpha1` behavior. To opt into an explicit empty
profile, prepare a complete change using the inspected HEAD and ledger revision:

```json
{
  "protocol": "taskctl.profile-change/1",
  "reviewed_head": null,
  "profile": {
    "protocol": "taskctl.effective-profile/1",
    "identity": "example.workspace/v1",
    "bindings": {}
  },
  "audit": {
    "protocol": "taskctl.profile-audit/1",
    "classification": "actor-assertion",
    "actor": "reviewer",
    "occurred_at": "2026-09-08T00:00:00Z",
    "reason": "Adopt an explicit reviewed semantic profile.",
    "evidence": {"review": "Exact reviewed implementation and result locator."}
  }
}
```

```text
taskctl profile set --file change.json --expect-revision sha256:... --plan
taskctl profile set --file change.json --expect-revision sha256:...
```

Subsequent changes require the current `ProfileRevisionId` as `reviewed_head`.
Each change supplies the full profile and explicit nonblank actor/reason/evidence,
with a validated [occurrence timestamp](EVIDENCE-TIME.md). Unknown fields, actor
`accepted_at`, stale profile HEADs and no-op replacements are rejected. Storage
CAS still requires the exact inspected whole-ledger revision.

A binding has this shape; these are illustrative identities, not shipped code:

```json
{
  "example.feature/v1": {
    "provider": "example.feature-provider/v1",
    "version": "1.0.0",
    "digest": "sha256:<64 lowercase hexadecimal digits>"
  }
}
```

The storage marker becomes **`taskctl.native/alpha5`**, which requires task history
and profile history, and permits prior imports and optional planning history.
The repository and policy configuration envelopes retain their versions and store
the selected profile digest in their `profile` fields. Profile roots record the
exact pre-adoption ledger revision; later changes retain immutable parents.
Older writers reject alpha5. Initialization, import and planning tracking do not
silently activate a profile or reinterpret earlier records.

Profile changes write only the declared config/policy files and
`.agents/profile-history/{heads.json,revisions/<digest>.json}`. They leave task
records, task history, receipts, import evidence and planning history untouched.
The existing bounded transaction journal includes these files. Recovery validates
every preimage before writing any result, including after a partial config/policy
write; an unrelated edit causes a conflict. No command invokes Git or a network.

## Contract and revision versions

`taskctl.semantic-contract/3` binds the unchanged empty-profile base task contract,
the full typed effective profile and selected extension payloads. This preserves
the original content interpretation of each task record dialect while making the
new profile explicit. Optional unknown extension data stays losslessly preserved
and inactive. Activating a feature makes its exact payload contractual. Required
feature identities remain independently readable core data.

`taskctl.task-revision/3` stores `RevisionSemantics`: the exact effective profile
and contributed prerequisite identities, together with the record, parent, review,
revision-aware dependency observations and optional import origin. Its contract is
self-contained and reconstructable without loading old provider code. Historical
validation checks the authored plus recorded contributed edge set and each observed
upstream revision/contract identity. New admissions must validate the current whole
graph with matching code before capturing new profiled revisions. Older `/1` and
`/2` encodings, hash domains and imported provenance remain unchanged.

Provider contributions do not rewrite a task's authored `requires` or planning
memberships. Current transitive inputs are computed over the complete effective
graph. A missing or failed required evaluation produces unknown inputs and
unresolved currency; it never claims that an incomplete graph is authoritative.
Historical edges remain evidence, while current traversal follows the validated
current DAG. Edges from different historical versions are not combined into a
spurious causal cycle.

## Drift and explicit review

Profile or provider-pin changes leave task HEADs unchanged and change the current
effective contracts. New/removed contributed edges and transitive input changes
remain visible until explicit review. Reviewing an intermediate task cannot silently
acknowledge its downstream consumers. Ordinary `revise` retains the task's last
captured profile and contributed observations; it cannot substitute for profile
revalidation. New profiled tasks require available deterministic evaluation and
current prerequisite profile witnesses before their revisions can be captured.

`reconcile TASK --plan` returns the exact current profile digest, task HEAD and
complete effective prerequisite observations, including provider evidence keys.
New profiled reviews use **`taskctl.reconciliation/3`**, with a required `profile`
digest and validated `occurred_at`. The remaining fields match `/2`: classification,
task, reviewed_head, outcome, observations, actor, rationale, evidence and successor.
Old review envelopes cannot be reinterpreted as approval of a newly adopted profile.

Revalidated reviews require matching profile/HEAD/observations, current upstream
currency, available semantics, readiness guards and all core/provider evidence
checks. Negative or unresolved outcomes remain unresolved and do not acknowledge
new inputs. Installing a matching replacement after pin drift is insufficient;
review must still bind the changed profile. Returning through an explicit profile
change to exactly previously reviewed semantics can restore currency through the
existing contract-equivalence rule. This does not erase intervening profile history.

Receipts already bind a semantic contract digest, so their existing envelopes can
address contract/3 without changing what old receipts meant. Historical closure
evidence remains valid for its own captured contract. The same effective contracts
feed planning member observations. Absent provider code makes an otherwise matching
assessment unresolved; actual changed contracts/inputs make it historical. Neither
case grants scope acceptance or task execution.

## Reads and conformance

Profiled repositories use `taskctl.doctor/alpha3` and `taskctl.snapshot/alpha3` result
contracts. Doctor names the exact profile digest and reports missing/failed semantics;
snapshot includes all immutable profile history alongside task/import/planning state.
`profile history` exposes each exact audited change. The bounded context contract
retains its limits and names the same profile digest. Legacy read versions remain
available for unadopted repositories. Runtime provider objects are never encoded.

Core and file cases cover typed identities, strict envelopes, old hash witnesses,
contributed graph order/cycles, evidence rejection, transitive drift, pin replacement,
unknown optional values, missing code, cold reconstruction and recovery boundaries.
The same provider implementations are exercised only as conformance fixtures in
JVM and Native Image tests; they are not registered as product capabilities.
Packaged CLI parity additionally exercises explicit profile adoption, reviews and
closure under an empty profile, unavailable-pin inspection/refusal, stale CAS,
immutable history and restoration of an exactly previously reviewed profile.
[Closure evidence](proof/provider-profile/README.md) records the successful
three-platform JVM/native corpus and packaged process comparisons.
