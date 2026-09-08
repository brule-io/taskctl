# Typed evidence time before native v1

Availability: development after published `0.3.0-alpha.2`. Existing repository
pins retain the published behavior until explicitly upgraded to a distribution
containing this change. Native v1 remains unfrozen.

Actor occurrence time, historical text, and storage acceptance time have different
nominal Kotlin types. An occurrence timestamp is an actor's assertion about when
an observation happened. Its syntax being valid does not prove its truth, establish
identity or authority, or make a task executable. Dependency observations, task
HEADs and ledger revisions still govern currency and compare-and-swap.

## Versioned assertion envelopes

| Assertion | Historical envelope | New envelope |
| --- | --- | --- |
| Closure evidence | `taskctl.receipt/alpha1`, `recorded_at` | `taskctl.receipt/alpha2`, `occurred_at` |
| Reconciliation | `taskctl.reconciliation/1`, `recorded_at` | `taskctl.reconciliation/2`, `occurred_at` |
| Import review | `taskctl.import-review/1`, `recorded_at` | `taskctl.import-review/2`, `occurred_at` |

Historical envelopes decode to `LegacyRecordedAt`: every previously valid nonblank
string survives exactly, including whitespace, non-date text and apparent ISO
timestamps. The codec never infers a newer classification from the string's shape.
Their protocol names, fields, encoded receipt bytes and task-revision hash domains
remain unchanged. Old envelopes are still accepted as legacy assertions; this is
an explicit compatibility contract, not a way to claim validated occurrence time.

New envelopes decode to `OccurredAt`. All three retain classification
`actor-assertion`; the other fields and lifecycle checks retain their established
meaning. They require `occurred_at` and reject `recorded_at`, `accepted_at`, unknown
fields, unknown protocols and missing fields. Legacy envelopes also reject an
added `occurred_at` or `accepted_at`. No permissive fallback is attempted.

For example, a new closure assertion is:

```json
{
  "protocol": "taskctl.receipt/alpha2",
  "classification": "actor-assertion",
  "task": "TASK.api",
  "contract": "sha256:<exact digest from show>",
  "actor": "reviewer",
  "occurred_at": "2026-09-07T16:15:00.123456789-07:00",
  "evidence": {"integration": "Observed result and durable evidence locator"}
}
```

For reconciliation and import, use their new protocol from the table and replace
the old `recorded_at` field with `occurred_at`. `reconcile --plan` describes the new
envelope. Submitted review HEADs, input observations, manifest identities, evidence
requirements and CAS preconditions are unchanged.

## Occurrence timestamp profile

The versioned `occurrence-time/1` profile is deliberately explicit:

```text
YYYY-MM-DDTHH:mm:ss[.1-to-9-fractional-digits](Z|+HH:MM|-HH:MM)
```

Years are 0001–9999, dates must exist in the ISO calendar, seconds are 00–59,
and numeric offsets are within ±18:00. `T` and `Z` are uppercase ASCII. An explicit
known offset is required; `-00:00`, zone names, local times, missing seconds,
leap-second spellings, surrounding whitespace and precision beyond nanoseconds
are rejected. This is a documented profile, not a claim to accept every ISO 8601
or RFC 3339 representation. Inputs outside it fail instead of being truncated.

Valid lexical values are preserved. `Z` and `+00:00`, or two different offsets
describing the same instant, can therefore produce distinct assertion bytes.
`toInstant()` is an explicit projection for callers; it does not normalize stored
evidence or replace the semantic contract digest. A new reconciliation produces
a new immutable revision, and the old revision remains available.

## Acceptance belongs to the accepting boundary

`AcceptedAt` uses the same validated timestamp profile but does not implement
`AssertionTime`. `TransitionResult.acceptedAt` is optional storage-boundary
metadata. A backend may supply it when it accepts a transition; an actor cannot
include it in any evidence envelope or transition request. `AcceptedAt.fromInstant`
requires an explicitly supplied instant. Core evaluation reads no ambient clock.

The current file adapter leaves acceptance time absent. It does not derive one
from occurrence time, file mtimes, the client's clock or old receipt strings, and
it does not rewrite history to manufacture that information. Its existing CLI
transition result remains unchanged. A future accepting backend must define its
own persisted acceptance event and clock policy before returning authoritative
timestamps; this type alone provides no authentication or clock assurance.

Import manifests remain source identities independent of their review time.
Import admission and task-revision outer envelopes continue to carry explicitly
versioned nested reviews. Older tools reject unfamiliar nested assertion contracts
rather than silently interpreting them; upgrade the repository pin before using
new envelopes. No automatic migration, Git operation or repository-wide rewrite
is introduced.

## Evidence

`HistoricalTimeGoldenTest` pins old receipt/review bytes and native/imported
revision identities against the unchanged codec at
`4233a65b42c482e8fc78ef18ab2eb483d376181e`, before implementation changes.
The synthetic fixtures and expected hashes were committed in `9e71468`.
Additional shared tests cover the parser profile, mixed-version rejection, authority
spoofing, import identity, equivalent instants, restart, stale CAS, current inputs
and preservation of old receipts. Packaged process cases exercise all three new
envelopes and compare stdout, diagnostics, exit codes and resulting files between
JVM and Native Image. The [closure evidence](proof/evidence-time/README.md) records
the exact green candidate, all three platform builds and the retained parity proof.
