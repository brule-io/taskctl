---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.005
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.004
---

# TASK.draft-mvp.005: Define explicit ports and domain commands

## Description

Create persistence, artifact, identity, league, draft, and query ports plus domain commands that preserve the specification's semantic boundaries.

## Requirements

- Own `ports/src/main` and `domain/src/main`; keep adapters and Exposed absent.
- Prefer capability-specific interfaces over generic repository hierarchies and require explicit source snapshot or football coordinates on temporal reads.
- Represent source disagreements, unresolved identity, rejections, and manual-versus-ESPN draft reconciliation explicitly.

## Deliverables

- [x] Ports cover every specified ETL output, read model, draft command, and health-check dependency.
- [x] Compile-time tests prove domain/strategy consumers cannot access source DTOs or Exposed types.
- [x] Domain commands reject ambiguous handles and invalid draft correction/supersession shapes.

## Closure

```yaml
closed_at: 2026-09-03T02:35:19.647549300Z
closed_by: Codex
summary: Defined capability-specific ETL, identity, football, forecast, league, draft, read-model, and health ports plus draft initialization and append-only command handlers with explicit ambiguity, supersession, and ESPN reconciliation outcomes.
verification:
  - .\gradlew.bat :ports:clean :domain:clean :ports:test :domain:test => BUILD SUCCESSFUL; ports tests=4 failures=0 errors=0 skipped=0; domain tests=7 failures=0 errors=0 skipped=0.
  - .\gradlew.bat :ports:dependencies --configuration runtimeClasspath :domain:dependencies --configuration runtimeClasspath :strategy:dependencies --configuration runtimeClasspath => BUILD SUCCESSFUL; ports resolves domain-types only, domain resolves ports/domain-types only, and strategy resolves ports/domain-types only; no Exposed or source-adapter dependency is present.
  - .\gradlew.bat verify => BUILD SUCCESSFUL; architecture fixtures rejected 7 forbidden cases, module graph verified 11 admitted modules, and 19 product implementation/resource files conformed.
follow_ups:
  - none
```
