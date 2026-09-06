# taskctl develops through taskctl

The canonical repository now has 22 real tasks, four durable roadmaps, and two
cross-roadmap epics. The graph covers semantic hardening, delivery, first migration
pressure tests, the protocol checkpoint, later complex migrations, and the bounded
remote/IDL pressure tests. Dependencies gate the later phases. Planning membership
does not create dependency edges.

Start with the repository wrapper:

```sh
./taskctl doctor
./taskctl context
./taskctl frontier
./taskctl status
./taskctl roadmap ROADMAP.delivery
./taskctl epic EPIC.alpha3
```

On Windows, use `./taskctl.ps1` or `./taskctl.bat`. These are the canonical wrapper
templates. Implementation remains outside the repository and is selected by the
exact `.taskctl/toolchain.lock`. No copied Kotlin/Python task implementation is
introduced into `.agents`.

The initial adoption used the packaged 0.3 candidate and its verified local cache,
preserving the existing AGENTS.md. The initial candidate archive SHA-256 was
`bed2012c86030012e150f7340d9da163d7c058800d2db74767efca62e7e228e1`.
This was an explicitly dirty development build, not a published release claim.
The final release milestone replaces that temporary local pin with the canonical
released native lock. That pin necessarily follows the source commit used to build
the first compatible released artifact; its publication and dogfood receipts name
the separate source/release/pin identities. No self-referential artifact hash is
claimed.

Task closure is recorded only after its acceptance is met. Receipts describe
actual checks and source identities as actor assertions. Distribution parity,
release transport, RPM transaction tests and final consumer acceptance remain
separate evidence requirements. Later product migrations and service work are
planned here but are not part of this release's implementation.
