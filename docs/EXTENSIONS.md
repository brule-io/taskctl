# Extension and provider contract

Core owns the top-level record namespace. Unknown core fields are errors.
Specialized payloads live under the `extensions` map, keyed by identities such as
`example.workspace/v1`. `required_extensions` is a separate core-readable list.
Optional unknown payloads are preserved, including exact integer/decimal values
and source spelling during lifecycle edits. They are not executed or interpreted.

The typed `Value` algebra is the canonical representation. `Any`, nullable top
types, untyped containers and unchecked-cast suppressions are prohibited beyond
the reviewed decoder boundary. The Kotlin PSI rule runs in `check` and CI.

Core `SemanticProvider` implementations contribute validation, dependency/readiness
predicates, evidence obligations and evidence verification. The core validates
the complete contributed graph and retains transition authority. Environment
probes are separate explicit operations; deterministic reads never contact a host.
Provider artifacts and behavior must be pinned when activated by a profile.

This alpha ships the `minimal/alpha1` profile, with no runtime behavioral provider
loader. Required unsupported features can be stored and inspected; `doctor`
lists them explicitly, and frontier/closure fails closed. There is no arbitrary
project code execution, schema-fetch side effect or placeholder provider that
silently approves required semantics. Local schema activation and bundled release,
ownership and execution providers remain subsequent capability work.

Roadmap and epic concepts are native. Primary-lane policy, concurrency, ownership,
release gates, host requirements and project-specific annotations remain extensions.
Group archival/completion evidence still needs a native contract before v1 freezes.
