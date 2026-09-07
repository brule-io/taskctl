# Native read projections

This describes development after the published `0.3.0-alpha.2` distribution;
existing release pins keep their shipped behavior until explicitly upgraded.

Native `doctor`, `context` and `snapshot` have distinct result contracts. The
outer JSON envelope remains `taskctl.cli/alpha1`; consumers should inspect the
result's `contract` field. This replaces the earlier alpha implementation in
which all three commands returned the same summary. It changes no stored task,
revision, lifecycle, provider or receipt semantics.

| Command | Result contract | Purpose |
| --- | --- | --- |
| `doctor` | `taskctl.doctor/alpha1` | Structural inspection status, counts, unavailable capabilities and currency diagnostics. |
| `context` | `taskctl.context/alpha1` | A deterministic bounded briefing with counts, selected work and explicit follow-up commands. |
| `snapshot` | `taskctl.snapshot/alpha1` | The complete typed ledger view, including historical evidence and exact extension values. |

All accept `--repo PATH` and `--format json|text`. They perform no Git operations,
network access, environment probes or repository writes. Invalid repositories
retain existing diagnostics and exit codes. A successfully inspected ledger can
have unavailable capabilities or affected work: doctor exits zero and reports
`health: blocked` or `health: attention`. `valid: true` means its structural load
succeeded, not that every task is executable. Frontier and closure still enforce
their normal guards.
Text output leads with `doctor: ok`, `doctor: attention` or `doctor: blocked` so
the inspection's status is visible before the details.

## Bounded context

The encoded JSON **result** is at most 32,768 UTF-8 bytes and contains at most 12
work items. The process envelope additionally carries the caller's repository
path. Counts describe the whole ledger, independent of which work fits. Context
does not claim a complete ledger even when no work was omitted.

Selection is deterministic: work needing review, then ready work, then waiting
open work, each ordered by full case-sensitive task identity. This is briefing
presentation, not execution priority. Legacy identity-only readiness keeps its
original rules and can coexist with reported unresolved historical observations.
Required unavailable task or planning capabilities cannot acquire readiness from
the briefing.

Titles are limited to 160 Unicode code points and intent to 240, without splitting
a surrogate pair. `text_truncated` identifies each shortened preview. Repository
display text has its own explicit truncation flag. Task identities are always
complete: an identity over 512 UTF-16 units is omitted instead of being shortened
into a misleading locator. Items that cannot fit the byte budget are also omitted.

`omitted_items`, `omitted_overlong_identities`, `truncated`, and `limits` expose
these choices. Use `show TASK --format json` for complete task content, `frontier`
and `affected` for full task selections, and `snapshot` for all typed state and
identities. No file or wire identity is computed from a truncated display string.

## Complete typed snapshot

`records` contains full tasks, roadmaps and epics. `history` contains the exact
typed HEAD map, origin and all immutable task revisions, or null for untracked
legacy history. `receipts`, `dependency_bindings` and `imports` preserve their
complete typed values. Imports retain exact source strings, selected adapter and
version, historical classification and manifest identity. Large integers and
decimals remain exact; they are never coerced through floating point.

`derived` contains frontier and currency explanations. Unavailable required
capabilities are listed and produce an empty derived frontier, consistent with
the existing execution guards. Task and planning records are sorted by identity;
authored memberships, requirements and other meaningful record contents are
preserved. No historical receipt becomes proof of a later changed contract.

This is a semantic inspection view, not a filesystem backup or an import command.
It does not include wrapper binaries, cache files, source formatting of native
documents, filesystem timestamps or storage lock/journal files. The reported
ledger `revision` is the snapshot identity supplied by storage, not a hash of the
read projection. Consumers must retain the original repository for an exact
physical restoration; this command does not invent a new restoration protocol.

## Validation

CLI projection tests join the same JVM/Native Image behavioral corpus. They
exercise large graphs, long text and identities, encoded-byte expansion, Unicode,
precise opaque values, unavailable task/planning capabilities, legacy readiness,
tracked currency, complete revision/receipt/import reconstruction and deterministic
selection. Packaged process tests repeat distinct outputs and large-state reads
through both implementations while checking exit codes, file bytes and timestamps.
