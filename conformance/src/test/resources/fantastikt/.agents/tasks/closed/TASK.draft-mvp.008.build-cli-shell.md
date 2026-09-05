---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.008
roadmap: ROADMAP.draft-mvp.001
effort: MEDIUM
impact: MEDIUM
depends:
  - TASK.draft-mvp.002
  - TASK.draft-mvp.005
---

# TASK.draft-mvp.008: Build the ball CLI shell

## Description

Create the `ball` executable, composition root, typed configuration loading, terminal error contract, and empty command hierarchy for the admitted CLI surface.

## Requirements

- Own `cli/src/main` composition and command-registration packages plus launch/distribution configuration.
- Register every Section 12 database, ETL, league, and draft command without claiming unimplemented behavior.
- Configuration reads environment variables and explicit files, validates without echoing secrets, and supports deterministic test overrides.

## Deliverables

- [x] `ball --help` exposes the complete stable command tree and unimplemented commands fail visibly.
- [x] Configuration tests prove required/optional values, redaction, and no implicit wall-clock selection.
- [x] The packaged executable starts on the repository-supported JDK.

## Closure

```yaml
closed_at: 2026-09-03T02:56:05.032592Z
closed_by: Codex
summary: Added the ball composition root, complete Section 12 command registry, stable help and terminal exit contract, typed file/environment/override configuration with secret redaction, and Gradle launch distributions.
verification:
  - .\gradlew.bat :cli:clean :cli:test => BUILD SUCCESSFUL; 7 tests across 2 suites, 0 failures, 0 errors, 0 skipped; help assertions cover 28 Section 12 leaf commands and every leaf returns UNAVAILABLE while unimplemented.
  - .\gradlew.bat :cli:installDist followed by cli/build/install/ball/bin/ball.bat --help => help_exit=0 with all 28 command usages; ball.bat db status => unavailable_exit=69 and visible registered-but-not-implemented error.
  - Configuration tests proved required-key failures, optional null/default behavior, ordered file/environment/override precedence, four-secret redaction, and credential-free error messages; cli/src/main implicit_wall_clock_references=0.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL; 52 tasks (51 executed, 1 up-to-date), 11 admitted module boundaries, 27 product files, PostgreSQL integration suite, and healthy task/ADR ledgers; launcher runtime was Temurin OpenJDK 21.0.11 LTS.
follow_ups:
  - none
```
