# Public Apache-2.0 publication

On 2026-09-07 the owner authorized making
[brule-io/taskctl](https://github.com/brule-io/taskctl) public and open source.
GitHub reports public visibility and Apache-2.0. LICENSE and NOTICE already covered
the protocol and reference tooling; this publication changes distribution access,
not protocol semantics, artifact bytes or the license text.

Only taskctl's visibility changed. The ancestral source repositories retain their
own visibility. Historical tasking witnesses, source revisions and provenance remain
intact in taskctl. Earlier release receipts retain their original private-publication
context; the same immutable release assets are now publicly accessible.

## Pre-publication review

[The audit](pre-public-audit.json) scanned all reachable Git refs, including six
fetched release tags (28 commits), and the retained logs of all 35 CI runs (162 files).
Gitleaks 8.30.1 was downloaded from its upstream release and SHA-256 verified.
Logs produced zero findings. All 56 Git findings were the `snakeyaml-2.5.jar`
dependency SHA-256, verified individually against the actual JAR. No credentials
were confirmed. The audit records its pattern-scanning limits; it makes no claim
that every possible secret is detectable. No history rewrite or blanket scanner
allowlist was needed.

## Anonymous consumer access

[Anonymous consumer proof](anonymous-consumer.json) records:

- GitHub repository and release metadata read without authentication.
- The existing native lock downloaded unchanged.
- All three native archives downloaded anonymously and digest-verified.
- Windows quick-start download, extraction and native greenfield initialization.
- Fresh wrapper-cache acquisition with no token, Java, Gradle, Git or Python on
  the consumer PATH; version inspection did not create the cache.
- Offline cached doctor/frontier and unchanged repository bytes/mtimes.
- Fantastikt's existing pin and all 41 current tasks, preserving its product frontier.

[Release access](release-access.json) also records all six immutable public releases
and an anonymous checksum-verified download of the existing alpha.1 Fedora RPM.
No release was rebuilt, replaced or retagged. Older immutable archive documentation
may describe the former private access requirement; current onboarding uses
anonymous downloads.

Fantastikt's unchanged `main` commit `9b8b4c91aa3991c8a82dfa7d09591fcfbd53c74e`
was reverified through [its hosted workflow](https://github.com/brule-io/fantastikt/actions/runs/34151320268/attempts/2).
The exact conclusion and job steps are recorded in [ci-main.json](ci-main.json).
No cross-repository secret or consumer workflow change was introduced.

Native v1 remains unfrozen. Brule migration and service work remain separate tasks.
`TASK.release.public` is closed with a contract-bound actor assertion. The
[final producer state](producer-final.json) has 23 current tasks and 13 closures;
only `TASK.migration.brule` is ready.
