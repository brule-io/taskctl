# Typed evidence time closure evidence

This evidence covers `TASK.protocol.evidence-time` and the boundaries documented
in [EVIDENCE-TIME.md](../../EVIDENCE-TIME.md). It is development conformance, not a
published release or a native-v1 freeze.

[CI run 34170972487](https://github.com/brule-io/taskctl/actions/runs/34170972487)
passed candidate `3e504e61aaf040de3aa69f531f47785657ff40a3` on Linux x86_64,
Windows x86_64 and macOS aarch64. Each platform ran 124 source tests, including
three architecture policy tests, 121 identical behavioral tests on JVM and Native
Image, and 171 packaged process parity cases. The retained [validation](validation.json)
binds the exact candidate, CI checkout and parents, jobs, test identities and build
artifacts. Downloaded archive bytes were digest-checked against their metadata.

Three golden tests were first run against the unchanged implementation at
`4233a65b42c482e8fc78ef18ab2eb483d376181e`. Commit `9e71468` pins their synthetic
old receipt/review JSON bytes and native/imported task-revision identities. They
retain arbitrary nonblank historical strings and pass unchanged under the new
codecs. Eight additional behavioral tests cover strict timestamp parsing, retained
offset/fraction spelling, legacy classification, unknown/mixed fields, attempted
storage-authority spoofing, import identity, cold reconstruction and revision CAS.

The process corpus exercises successful and rejected new receipt, reconciliation
and import-review envelopes. It checks old and new evidence together after restart,
stale revision rejection, preserved historical receipts, exact source manifests,
diagnostic/exit parity, cached bootstrap and bounded repository effects. CI also
inspects the actual producer ledger read-only with each implementation. Local
Windows source checks, 121-test JVM/native parity and all 171 process cases passed;
the opt-in abstract-machine callers compiled with explicit legacy constructors.

Review of the final model confirms that actor envelopes contain `AssertionTime`
(`LegacyRecordedAt` or `OccurredAt`), while optional `TransitionResult.acceptedAt`
uses the separate `AcceptedAt` type. Constructors validate privately. No actor
transition or evidence decoder accepts an authoritative acceptance timestamp;
no ambient clock was added to core evaluation. The current file boundary reports
acceptance time absent. A future backend still needs a persisted acceptance-event
and clock policy before claiming that authority.

This lifecycle receipt is an actor assertion about observed checks. The producer
uses its published alpha.2 pin to record it in the supported legacy envelope;
that does not relabel the receipt as a newly validated occurrence or invent a
storage timestamp. Historical evidence and import provenance retain their original
contract identities and classifications.
