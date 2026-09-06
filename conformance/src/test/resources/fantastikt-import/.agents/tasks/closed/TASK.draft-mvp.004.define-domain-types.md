---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.004
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.002
---

# TASK.draft-mvp.004: Define semantic domain value types

## Description

Define serializable identities, football coordinates, source snapshot coordinates, money, statistics, positions, and fantasy-asset sum types without persistence coupling.

## Requirements

- Own `domain-types/src/main` and its unit tests; depend only on Kotlin stdlib and kotlinx serialization.
- Centralize lowercase UUID parsing/formatting; minting remains behind a port and never derives from external IDs.
- Model `FootballPlayer` as Person identity, TeamDefense as a team-backed FantasyAsset, NFL Position separately from ESPN FantasyPosition, and money as integral cents.

## Deliverables

- [x] Typed IDs and value objects cover the specification's canonical entities, temporal coordinates, artifacts, forecasts, leagues, drafts, and events.
- [x] Round-trip and invalid-value tests prove UUID, jersey, money, season/week, and sealed fantasy-asset behavior.
- [x] The module contains no Exposed or source-adapter dependency.

## Closure

```yaml
closed_at: 2026-09-03T02:24:44.931382100Z
closed_by: Codex
summary: Defined serialization-safe domain identities, explicit temporal/source coordinates, exact money/statistic values, separate football/fantasy positions, and the sealed player/team-defense asset model.
verification:
  - command: .\gradlew.bat :domain-types:clean :domain-types:test -> BUILD SUCCESSFUL; XML receipt tests=13 failures=0 errors=0
  - command: .\gradlew.bat :domain-types:dependencies --configuration runtimeClasspath -> only kotlinx-serialization-core 1.11.0 and Kotlin stdlib 2.4.10 on production runtime classpath
  - command: .\gradlew.bat verify -> BUILD SUCCESSFUL; product sources: 8 implementation/resource files conform; architecture fixtures: 7 forbidden cases rejected
  - tests: UUID parsing normalizes uppercase and rejects malformed/noncanonical values; jersey/money/season/week invalid values reject; sealed fantasy assets round-trip player and team-defense variants and reject unsupported person payload
  - commands: taskctl doctor; adrctl doctor; git diff --check -> all exit 0
follow_ups:
  - none
```
