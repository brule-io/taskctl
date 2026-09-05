# ROADMAP: Platform Alpha Hardening + Contract Transport

Doc type: `ROADMAP`

Supersedes: []

## Scope and assumptions
- Tasks are the unit of execution; this roadmap is authoritative ordering.
- Keep the repo green after each task (lint/tests/dryrun as appropriate).
- Assets are first-class: tasks must list build outputs and keep `ASSETS.md` current.
- CI must run the test suite for each component; local changes must be verified with the same tests.

## Global gates (run after each task)
- `./gradlew checkIn`
- `./gradlew test` (when Gradle project changes are involved)
- `pnpm -C web test` (when web changes are involved)
- `pnpm -C web build` (when web changes are involved)
- Verify `ASSETS.md` reflects outputs produced in the slice.

## Decision needed
- Version source-of-truth file location/format across repos:
  - **Recommended**: `VERSION` file at repo root (single line semver), consumed by Gradle + web + CI.
  - Rationale: simple to read in Gradle/Node/CI and easy to bump consistently.

## Phase 1 — CI Hygiene + Test Enforcement (EPIC.alpha.001.ci-hygiene)
- [x] (1) `.agents/tasks/closed/alpha/TASK.alpha.030.pnpm-store-warning.md`
- [x] (2) `.agents/tasks/closed/alpha/TASK.alpha.031.ci-test-enforcement.md`

## Phase 2 — Versioning Source of Truth (EPIC.alpha.002.version-sot)
- [x] (3) `.agents/tasks/closed/alpha/TASK.alpha.032.version-source.md`
- [x] (4) `.agents/tasks/closed/alpha/TASK.alpha.033.version-propagation.md`

## Phase 3 — Spec-First Service Contracts (EPIC.alpha.003.spec-first-services)
- [ ] (5) `.agents/tasks/open/alpha/TASK.alpha.034.spec-first-tooling.md`
- [ ] (6) `.agents/tasks/open/alpha/TASK.alpha.035.spec-first-authz.md`
- [ ] (7) `.agents/tasks/open/alpha/TASK.alpha.036.spec-first-tenancy.md`
- [ ] (8) `.agents/tasks/open/alpha/TASK.alpha.037.spec-first-jobs.md`
- [ ] (9) `.agents/tasks/open/alpha/TASK.alpha.038.spec-first-meter.md`
- [ ] (10) `.agents/tasks/open/alpha/TASK.alpha.039.spec-first-wall.md`

## Phase 4 — Transport Unification (Broker-First) (EPIC.alpha.004.transport-broker)
- [ ] (11) `.agents/tasks/open/alpha/TASK.alpha.041.broker-transport-core.md`
- [ ] (12) `.agents/tasks/open/alpha/TASK.alpha.042.broker-client-authz.md`
- [ ] (13) `.agents/tasks/open/alpha/TASK.alpha.043.broker-client-tenancy.md`
- [ ] (14) `.agents/tasks/open/alpha/TASK.alpha.044.broker-client-jobs.md`
- [ ] (15) `.agents/tasks/open/alpha/TASK.alpha.045.broker-client-meter.md`
- [ ] (16) `.agents/tasks/open/alpha/TASK.alpha.046.broker-client-wall.md`

## Phase 5 — Build/CI Optimization + Gradle Conventions (EPIC.alpha.005.build-optimization)
- [ ] (17) `.agents/tasks/open/alpha/TASK.alpha.047.web-sdk-cache.md`
- [ ] (18) `.agents/tasks/open/alpha/TASK.alpha.048.gradle-conventions-plugin.md`
- [ ] (19) `.agents/tasks/open/alpha/TASK.alpha.049.apply-conventions-plugin.md`

## Notes
- When a TASK is closed, move it to `.agents/tasks/closed/` and update its checkbox here.
- When this ROADMAP is complete, move it to `.agents/roadmaps/closed/` and version the filename.
