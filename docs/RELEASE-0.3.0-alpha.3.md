# taskctl 0.3.0-alpha.3

This alpha packages the post-checkpoint protocol corrections and construction
boundary fixes. Native-v1 remains unfrozen; the
[candidate specification](spec/NATIVE-V1-CANDIDATE.md) is a review proposal.
Native executables and an explicit bundled-JVM reference are provided for
Windows x86_64, Linux x86_64 and macOS aarch64. Native locks are generated only
after behavioral and packaged-process parity pass.

Newly available since alpha.2:

- Distinct diagnostic `doctor`, bounded `context`, and complete typed `snapshot`
  result contracts, with explicit truncation and capability/currency diagnostics.
- Explicit audited roadmap/epic amendments, active/archived disposition and
  scope-bound assessments. Planning changes preserve task contracts and the DAG.
- Validated occurrence-time evidence envelopes alongside unchanged historical
  recorded strings; storage acceptance time remains a separate typed concept.
- Persisted exact effective profiles and profile-bound history/currency through
  the programmatic provider seam. The CLI has no runtime provider loader and
  supplies no production host/operator or approval capability.
- Corrections for malformed Unicode, whole-number decimal round trips,
  noncanonical provider inputs, mutable import manifests, mutable Value aliases
  and invalid assertion inputs. Retained red witnesses and conformance evidence
  describe the defects and the boundaries now enforced.
- A consolidated candidate specification and evidence index, plus complete
  consumer guides in each archive. Links to unbundled proof/source files bind
  the exact packaging source commit.

Existing task, contract, receipt and import identities retain their original
versioned meanings. Installing this tool does not rewrite repository state.
`init` still creates history-backed native alpha2; reviewed imports use alpha3.
Planning history adoption explicitly opts into alpha4, and effective profiles
into alpha5. These storage markers are separate from the tool's alpha.3 version.
Upgrade the exact tool pin before opting into a newer repository/envelope format;
older writers reject formats they do not support. Read
[planning history](PLANNING-HISTORY.md), [profiles](EFFECTIVE-PROFILES.md),
[evidence time](EVIDENCE-TIME.md) and [read models](READ-MODELS.md) before adopting.

The bounded PostgreSQL/HTTP kernel and Smithy/Python projection remain source
experiments outside the CLI/native artifacts. This release does not launch a
service, migrate another repository, resolve external ledgers automatically,
freeze native v1 or change project authorization. Stateful consumer adoption
still requires a proved preserving adapter/provider and consumer-owned evidence.

The wrapper contract remains version 3 with lock format 2. `--version`, `-V` and
`version` need no acquisition; `info` reports the verified executable. Cached
offline execution remains supported. Explicit task writes remain bounded and
inspectable, with no implicit Git operations. Global `taskctl` and a repository's
`./taskctl` select their own installations/pins independently.

Apache-2.0 and exact third-party notices are included. Release manifests identify
native versus JVM artifacts, GraalVM/build/runtime identities and SHA-256 digests.
The signed immutable GitHub release binds the source tag and uploaded assets;
no separate CI OIDC signature or SLSA level is claimed. An optional Fedora RPM is
a separately tested companion of the canonical Linux native artifact and does
not block or replace the archives. Existing immutable releases remain available.
