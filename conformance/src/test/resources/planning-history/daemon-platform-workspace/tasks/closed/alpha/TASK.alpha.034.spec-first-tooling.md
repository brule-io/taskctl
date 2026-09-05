---
title: TASK.alpha.034.spec-first-tooling
epic: EPIC.alpha.003.spec-first-services
impact: HIGH
effort: MEDIUM
suggestion: OPEN
owner: codex-1
tags: [spec, codegen, build]
depends_on: []
blocks: [TASK.alpha.035.spec-first-authz, TASK.alpha.036.spec-first-tenancy, TASK.alpha.037.spec-first-jobs, TASK.alpha.038.spec-first-meter, TASK.alpha.039.spec-first-wall]
---

## Goal
Provide shared tooling to generate DTOs and JAX-RS interfaces from service-local OpenAPI specs.

## Deliverables
- Gradle task(s) to generate JAX-RS interfaces and DTOs from `spec/` inputs.
- Generated sources wired into compilation for services.
- Documentation for the spec-first workflow (local dev + CI).

## Assets
- Spec bundles and generated client jars remain the published artifacts.

## Acceptance
- `./gradlew :platform-<service>:specBundle` succeeds for at least one service.
- Generated sources compile without manual edits.
- CI remains green after tooling adoption.

## Recon
- Added `tooling/spec-codegen/spec-codegen.gradle.kts` and README to provide shared lint/bundle/codegen tasks.
- Helper uses spec-engine from GitLab npm registry and OpenAPI Generator for JAX-RS + Kotlin clients.

---

## Closure
- Completed: 2026-02-14 00:00 (local)
- Branch: `main`
- Summary:
  - Shared spec-codegen helper exists for lint/bundle/codegen.
  - Authz/Tenancy already wire generated sources into compilation.
