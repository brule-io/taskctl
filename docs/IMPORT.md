# Reviewed ancestral import

The 0.3.0-alpha.2 Fantastikt adapter admits the tested `loom.agent/v1` dialect
through a reviewed, content-addressed manifest. It is not a generic YAML import.
Select `fantastikt-loom-agent-2026` explicitly; its conversion version is `0.2.0`.
The older inspection/extraction adapter remains a separate historical interface.

## Inspect, review, apply

Start from a clean source checkout and record its exact Git commit externally.
The tool never calls Git: `--source-repository` and `--source-revision` are caller
assertions. The manifest binds those assertions, every source witness, the adapter
version, and the complete native projection. Verify the checkout/commit relation
before signing the review. Source reads create no locks, journals or caches.

```sh
taskctl import inspect --source SOURCE --source-repository fantastikt --source-revision SHA --adapter fantastikt-loom-agent-2026 --format json
taskctl import plan --source SOURCE --source-repository fantastikt --source-revision SHA --adapter fantastikt-loom-agent-2026 --format json > import-plan.json
```

Review identities, prerequisites, planning memberships, source and target contract
digests, raw historical closure text, and the reported structural frontier. Write
an explicit review (substitute the plan's exact `manifest_id`):

```json
{
  "protocol": "taskctl.import-review/1",
  "classification": "actor-assertion",
  "manifest": "sha256:MANIFEST_DIGEST",
  "actor": "reviewer",
  "recorded_at": "2026-09-06T00:00:00Z",
  "rationale": "Describe what was reviewed and any limitations."
}
```

Use an isolated target checkout containing existing code but no active `.agents`,
`.taskctl` or taskctl launchers. Archive legacy state explicitly before applying;
the importer never removes it. Existing `AGENTS.md` is preserved, so integrate
the generated `.agents/README.md` instructions into that authority deliberately.

```sh
taskctl import apply --source SOURCE --file import-plan.json --review review.json --repo TARGET --id fantastikt --toolchain toolchain.lock --plan --format json
taskctl import apply --source SOURCE --file import-plan.json --review review.json --repo TARGET --id fantastikt --toolchain toolchain.lock --format json
```

Apply recomputes the plan against source bytes and rejects drift with exit 4.
Unknown adapter versions, mismatched reviews and existing tasking state are errors.
The write plan lists paths and pre/post digests. Writes use the bounded bootstrap
journal: tasking records, history, import admission, wrappers and pin. No implicit
Git operation, product source change, network call or legacy-tool removal occurs.

## Mapping and historical evidence

Task IDs and lifecycle are preserved. Description becomes intent; Requirements
and Deliverables retain their Markdown as requirement/acceptance values. Explicit
prerequisites and legacy realization prerequisites become causal edges. Roadmaps
and epics independently store their projected task sets. Legacy effort, impact,
ordinal and ownership annotations live under `legacy.fantastikt/v1`; they grant
no new execution authority. Closed planning records are rejected pending a native
planning audit policy. Unsupported conversion is never silently discarded.

The exact UTF-8 `.agents` witness files (except runtime `.lock`) are embedded in
`.agents/imports/<manifest-digest>.json`. Source file SHA-256 and historical
adapter contract identity remain distinct from the new core-draft-2 contract.
The source is explicitly **not native**. Historical closure is classified as
`historical-narrative-unverified`; import creates **no native receipts**.

Imported tasks initially lack dependency observations. Closed roots are affected;
downstream tasks can be unresolved. The structural frontier in the plan is not
permission to execute. `history TASK` exposes the exact source and import review.
Use normal `reconcile TASK --plan` and explicit actor/evidence reviews, in causal
order, to establish current contract/input correspondence. A review is a new
assertion with its own revision; it never upgrades an old narrative into a receipt
or claims old tests were rerun. Leave uncertain work unresolved.

## Compatibility and proof

Imports use `taskctl.native/alpha3` and origin-bearing `taskctl.task-revision/2`.
Existing alpha1/alpha2 repositories, task contracts and revision/1 digests retain
their meanings. Ordinary init/adopt still create alpha2. Older tools reject alpha3;
pin an import-capable distribution before replacing copied workflow tooling.

The conformance fixture retains all 40 tasks, 29 closures, one roadmap and one
epic from Fantastikt commit `b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4`.
Tests cover exact witnesses, separate historical evidence, explicit reconciliation,
changed contracts, realization edges, stale plans/reviews, bounded writes, cold
reconstruction and the native/JVM process and cached-wrapper boundaries.

Readiness for a real consumer also requires its own build checks and normal
dogfood usage before copied task tooling is removed. This adapter does not
authorize migrations of other ancestral dialects.
