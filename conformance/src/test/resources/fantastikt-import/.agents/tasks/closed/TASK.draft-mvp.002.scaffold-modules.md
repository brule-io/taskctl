---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.002
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.001
---

# TASK.draft-mvp.002: Scaffold the strict Kotlin module graph

## Description

Create the admitted Gradle multi-module skeleton and enforce dependency direction and Kotlin type restrictions before product code accumulates.

## Requirements

- Own root Gradle settings/build logic and create baseline build files for `domain-types`, `ports`, `domain`, `durability-postgres`, `etl-core`, all four ETL adapters, `strategy`, and `cli`; a later feature task may extend only its module-local build file for dependencies required by that task's owned package.
- Keep deferred Neo4j, play-by-play, and web modules absent from the P0 runtime graph.
- Preserve `:tooling:workflow`, repository-authority and reviewed-SPEC-digest verification, task/ADR doctors, platform launchers, and CI while replacing the bootstrap root build with product modules.
- Enforce explicit API where applicable and fail verification on non-Kotlin product implementation sources, domain Exposed imports, adapter Exposed signatures, wildcard imports, star projections, production `Any` type positions, and unapproved unchecked-cast suppression. Apply production-language scans to implementation files under `src/main` in the eleven P0 product modules while allowing resources and excluding tests, generated output, Gradle caches, and the separately governed `:tooling:workflow` implementation.

## Deliverables

- [x] `gradlew projects` exposes all and only the eleven admitted P0 product modules alongside the preserved `:tooling:workflow` module, with dependency-boundary tests.
- [x] Architecture fixtures demonstrate a non-Kotlin production source and every forbidden Kotlin construct are rejected while conforming Kotlin plus resources compile.
- [x] `gradlew verify` includes all architecture checks.
- [x] The existing task/ADR ledgers, cold-onboarding authority check, and Windows/POSIX workflow launchers still pass after the module restructure.

## Closure

```yaml
closed_at: 2026-09-03T02:05:20.742176700Z
closed_by: Codex
summary: Scaffolded the eleven-module Kotlin P0 graph with enforced dependency and source boundaries while preserving repository workflow tooling.
verification:
  - command: .\gradlew.bat clean verify -> BUILD SUCCESSFUL; 36 actionable tasks executed; module graph reported 11 admitted modules; 7 forbidden fixtures rejected; conforming Kotlin/resource fixture compiled
  - command: .\gradlew.bat projects -> cli, domain, domain-types, durability-postgres, etl-core, etl-espn, etl-fantasypros, etl-ffverse, etl-nflverse, ports, strategy, and tooling:workflow exposed
  - commands: taskctl/adrctl doctor through .cmd, .ps1, and Git Bash .sh launchers -> task ledger 39 open/1 closed doctor ok; ADR ledger 0 records doctor ok
  - command: git diff --check -> exit 0 with no diagnostics
follow_ups:
  - none
```
