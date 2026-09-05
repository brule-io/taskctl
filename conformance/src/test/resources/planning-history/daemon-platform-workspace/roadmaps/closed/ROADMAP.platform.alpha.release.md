# ROADMAP: Platform Alpha Release — Build Graph + BOM + Publish Discipline

Doc type: `ROADMAP`

Supersedes: [`.agents/roadmaps/closed/ROADMAP.platform.alpha.hardening.phase2.md`]

## Scope and assumptions
- This roadmap defines the build/graph/publish foundation for the next alpha release.
- Tasks are the unit of execution; ordering here is authoritative.
- Assets are first-class: each TASK must list outputs and update `ASSETS.md` when artifacts change.
- CI must be green before TASK close-out; release tags are gated on `./gradlew e2eAlpha`.
- CI is glue, not substance: every CI step must be runnable via Gradle tasks.
- Current blocker: next delivery work is Phase 2+ (role plugins + artifact graph); CI stabilization gate for this roadmap is complete.

## Global gates (run after each task)
- `./gradlew checkIn`
- `./gradlew test` (when Gradle project changes are involved)
- `pnpm -C web test` (when web changes are involved)
- `pnpm -C web build` (when web changes are involved)
- `./gradlew e2eAlpha` (when changes touch runtime stack or E2E harness)
- Verify `ASSETS.md` reflects outputs produced in the slice.

## Decision needed
- **BOM stratification**: single `daemon-bom` vs layered (`daemon-bom-core`, `daemon-bom-quarkus`, `daemon-bom-test`).
  - Default for this roadmap: **start with a single `daemon-bom`**, add layers only if a concrete conflict appears.
- **Artifact graph scope**: which non-JVM artifacts become first-class variants first (OpenAPI specs, SDKs, container metadata).
  - Default for this roadmap: **OpenAPI specs → client codegen → web SDK** are first-class; others are deferred.

## Phase A — CI Stabilization + Tag Readiness (EPIC.ci.002.platform-ci-stabilization)
- [x] (A.1) `.agents/tasks/closed/ci/TASK.ci.021.failure-baseline.md`
- [x] (A.2) `.agents/tasks/closed/ci/TASK.ci.022.source-bom-resolution.md`
- [x] (A.3) `.agents/tasks/closed/ci/TASK.ci.023.lock-alignment.md`
- [x] (A.4) `.agents/tasks/closed/ci/TASK.ci.024.mr-gated-ci-recovery.md`
- [x] (A.5) `.agents/tasks/closed/ci/TASK.ci.025.green-tag-candidate.md`

## Phase 0 — Carry-over from Alpha Hardening (EPIC.alpha.003–005)
- [x] (0.1) `.agents/tasks/closed/alpha/TASK.alpha.034.spec-first-tooling.md`
- [x] (0.2) `.agents/tasks/closed/alpha/TASK.alpha.035.spec-first-authz.md`
- [x] (0.3) `.agents/tasks/closed/alpha/TASK.alpha.036.spec-first-tenancy.md`
- [x] (0.4) `.agents/tasks/closed/alpha/TASK.alpha.037.spec-first-jobs.md`
- [x] (0.5) `.agents/tasks/closed/alpha/TASK.alpha.038.spec-first-meter.md`
- [x] (0.6) `.agents/tasks/closed/alpha/TASK.alpha.039.spec-first-wall.md`
- [x] (0.7) `.agents/tasks/closed/alpha/TASK.alpha.041.broker-transport-core.md`
- [x] (0.8) `.agents/tasks/closed/alpha/TASK.alpha.042.broker-client-authz.md`
- [x] (0.9) `.agents/tasks/closed/alpha/TASK.alpha.043.broker-client-tenancy.md`
- [x] (0.10) `.agents/tasks/closed/alpha/TASK.alpha.044.broker-client-jobs.md`
- [x] (0.11) `.agents/tasks/closed/alpha/TASK.alpha.045.broker-client-meter.md`
- [x] (0.12) `.agents/tasks/closed/alpha/TASK.alpha.046.broker-client-wall.md`
- [x] (0.13) `.agents/tasks/closed/alpha/TASK.alpha.047.web-sdk-cache.md`
- [x] (0.14) `.agents/tasks/closed/alpha/TASK.alpha.048.gradle-conventions-plugin.md`
- [x] (0.15) `.agents/tasks/closed/alpha/TASK.alpha.049.apply-conventions-plugin.md`

## Phase 1 — Platform BOM Foundation (EPIC.alpha.006.platform-bom)
- [x] (1.1) `.agents/tasks/closed/alpha/TASK.alpha.050.platform-bom-module.md`
- [x] (1.2) `.agents/tasks/closed/alpha/TASK.alpha.051.platform-bom-from-catalog.md`
- [x] (1.3) `.agents/tasks/closed/alpha/TASK.alpha.052.platform-bom-adoption.md`

## Phase 2 — Role Conventions + Policy Gates (EPIC.alpha.007.role-conventions)
- [x] (2.1) `.agents/tasks/closed/alpha/TASK.alpha.053.build-logic-skeleton.md`
- [x] (2.2) `.agents/tasks/closed/alpha/TASK.alpha.054.role-plugins.md`
- [x] (2.3) `.agents/tasks/closed/alpha/TASK.alpha.055.policy-gates.md`

## Phase 3 — Artifact Graph (Specs → Clients → Web SDK) (EPIC.alpha.008.artifact-graph)
- [x] (3.1) `.agents/tasks/closed/alpha/TASK.alpha.056.publish-spec-variants.md`
- [x] (3.2) `.agents/tasks/closed/alpha/TASK.alpha.057.consume-spec-variants.md`
- [x] (3.3) `.agents/tasks/closed/alpha/TASK.alpha.058.extend-artifact-graph.md`

## Phase 4 — Affected + Dependent Build Orchestration (EPIC.alpha.009.affected-build)
- [x] (4.1) `.agents/tasks/closed/alpha/TASK.alpha.059.build-affected.md`
- [x] (4.2) `.agents/tasks/closed/alpha/TASK.alpha.060.build-dependents.md`
- [x] (4.3) `.agents/tasks/closed/alpha/TASK.alpha.061.graph-introspection.md`

## Phase 5 — Publish/Signing Discipline (EPIC.alpha.010.release-publishing)
- [x] (5.1) `.agents/tasks/closed/alpha/TASK.alpha.062.publish-policy-plugin.md`
- [x] (5.2) `.agents/tasks/closed/alpha/TASK.alpha.063.release-signing-gate.md`
- [x] (5.3) `.agents/tasks/closed/alpha/TASK.alpha.064.release-ci-alignment.md`

## Phase 6 — Supply Chain Integrity (EPIC.alpha.011.supply-chain)
- [x] (6.1) `.agents/tasks/closed/alpha/TASK.alpha.065.locking-defaults.md`
- [x] (6.2) `.agents/tasks/closed/alpha/TASK.alpha.066.sbom-per-module.md`
- [x] (6.3) `.agents/tasks/closed/alpha/TASK.alpha.067.verification-metadata.md`


## Phase 7 — Backend Lint + Compliance (EPIC.backend-alpha.001.backend-lint-compliance)
- [x] (7.1) `.agents/tasks/closed/TASK.backend-alpha.001.backend-lint-framework.md`
- [x] (7.2) `.agents/tasks/closed/TASK.backend-alpha.002.backend-lint-baseline.md`
- [x] (7.3) `.agents/tasks/closed/TASK.backend-alpha.003.config-mapping-compliance.md`
- [x] (7.4) `.agents/tasks/closed/TASK.backend-alpha.004.http-client-compliance.md`
- [x] (7.5) `.agents/tasks/closed/TASK.backend-alpha.005.gateway-spec-drift-compliance.md`

## Phase 8 — Domain Skill Governance Stream (Standing)
Epic: EPIC.process.002.domain-skill-governance (standing, non-closable)
- [x] (8.1) `.agents/tasks/closed/process/TASK.process.002.skill-harvest.md`
- [x] (8.2) `.agents/tasks/closed/process/TASK.process.003.backend-skill-harvest.md`
- [x] (8.3) `.agents/tasks/closed/process/TASK.process.004.web-skill-harvest.md`
- [x] (8.4) `.agents/tasks/closed/process/TASK.process.005.ops-skill-harvest.md`
- [x] (8.5) `.agents/tasks/closed/process/TASK.process.006.mgmt-skill-harvest.md`

Notes:
- This phase is intentionally recurring. Tasks can remain active/incomplete until a review cycle completes and can be reissued.

## Phase 9 — Keycloak Theme Ownership In Web (EPIC.alpha.018.keycloak-theme-web-ownership)
- [x] (9.1) `.agents/tasks/closed/alpha/TASK.alpha.068.keycloak-theme-source-relocation.md`
- [x] (9.2) `.agents/tasks/closed/alpha/TASK.alpha.069.keycloak-theme-web-gradle-ownership.md`
- [x] (9.3) `.agents/tasks/closed/alpha/TASK.alpha.070.workspace-compose-theme-consumption.md`
- [x] (9.4) `.agents/tasks/closed/alpha/TASK.alpha.071.keycloak-theme-ci-doc-alignment.md`

## Phase 10 — JVM Base Image Unification (EPIC.alpha.019.jvm-base-image-unification)
- [x] (10.1) `.agents/tasks/closed/alpha/TASK.alpha.072.platform-base-image-definition.md`
- [x] (10.2) `.agents/tasks/closed/alpha/TASK.alpha.073.platform-jvm-image-definition.md`
- [x] (10.3) `.agents/tasks/closed/alpha/TASK.alpha.074.service-dockerfile-jvm-migration.md`
- [x] (10.4) `.agents/tasks/closed/alpha/TASK.alpha.075.deployment-image-dedup.md`
- [x] (10.5) `.agents/tasks/closed/alpha/TASK.alpha.076.ci-assets-doc-alignment.md`

## Phase 11 — Backend Lint Pluginization (EPIC.alpha.020.backend-lint-pluginization)
- [x] (11.1) `.agents/tasks/closed/alpha/TASK.alpha.077.backend-lint-plugin-skeleton.md`
- [x] (11.2) `.agents/tasks/closed/alpha/TASK.alpha.078.backend-lint-detekt-integration.md`
- [x] (11.3) `.agents/tasks/closed/alpha/TASK.alpha.079.backend-lint-root-cleanup.md`
- [x] (11.4) `.agents/tasks/closed/alpha/TASK.alpha.080.backend-lint-ci-doc-alignment.md`

## Phase 12 — Spec Engine Repo Extraction (EPIC.alpha.021.spec-engine-repo-extraction)
- [x] (12.1) `.agents/tasks/closed/alpha/TASK.alpha.081.spec-engine-repo-bootstrap.md`
- [x] (12.2) `.agents/tasks/closed/alpha/TASK.alpha.082.spec-engine-consumer-resolution-switch.md`
- [x] (12.3) `.agents/tasks/closed/alpha/TASK.alpha.083.spec-engine-workspace-decoupling-cleanup.md`
- [x] (12.4) `.agents/tasks/closed/alpha/TASK.alpha.084.spec-engine-assets-doc-ci-alignment.md`

## Phase 13 — Docs Repo Extraction (EPIC.alpha.022.docs-repo-extraction)
- [x] (13.1) `.agents/tasks/closed/alpha/TASK.alpha.085.docs-repo-bootstrap.md`
- [x] (13.2) `.agents/tasks/closed/alpha/TASK.alpha.086.docs-workspace-integration-switch.md`
- [x] (13.3) `.agents/tasks/closed/alpha/TASK.alpha.087.docs-path-redirect-and-url-policy.md`
- [x] (13.4) `.agents/tasks/closed/alpha/TASK.alpha.088.docs-assets-ci-governance.md`

## Phase 14 — Broker Config Mapping Adapter (EPIC.alpha.023.broker-config-mapping-adapter)
- [x] (14.1) `.agents/tasks/closed/alpha/TASK.alpha.089.broker-config-contract-and-compat.md`
- [x] (14.2) `.agents/tasks/closed/alpha/TASK.alpha.090.broker-quarkus-adapter-module.md`
- [x] (14.3) `.agents/tasks/closed/alpha/TASK.alpha.091.service-broker-config-migration.md`
- [x] (14.4) `.agents/tasks/closed/alpha/TASK.alpha.092.broker-config-ci-doc-assets-alignment.md`

## Phase 15 — Platform BOM Uniformity (EPIC.alpha.024.platform-bom-uniformity)
- [x] (15.1) `.agents/tasks/closed/alpha/TASK.alpha.093.platform-bom-contract-and-catalog-alignment.md`
- [x] (15.2) `.agents/tasks/closed/alpha/TASK.alpha.094.quarkus-service-convention-defaults.md`
- [x] (15.3) `.agents/tasks/closed/alpha/TASK.alpha.095.service-gradle-uniformity-migration.md`
- [x] (15.4) `.agents/tasks/closed/alpha/TASK.alpha.096.bom-drift-guardrails-and-ci-alignment.md`

## Phase 16 — Gateway Auth Context Unification (EPIC.alpha.025.gateway-auth-context-unification)
- [x] (16.1) `.agents/tasks/closed/alpha/TASK.alpha.097.gateway-request-context-provider.md`
- [x] (16.2) `.agents/tasks/closed/alpha/TASK.alpha.098.gateway-auth-header-map-dedup.md`
- [x] (16.3) `.agents/tasks/closed/alpha/TASK.alpha.099.gateway-auth-context-enforcement.md`
- [x] (16.4) `.agents/tasks/closed/alpha/TASK.alpha.100.gateway-codegen-sdk-context-alignment.md`

## Phase 17 — Coordinated Version Alignment Release (EPIC.alpha.002.version-sot)
- [x] (17.1) `.agents/tasks/closed/alpha/TASK.alpha.101.coordinated-version-inventory.md`
- [x] (17.2) `.agents/tasks/closed/alpha/TASK.alpha.102.submodule-version-alignment-bump.md`
- [x] (17.3) `.agents/tasks/closed/alpha/TASK.alpha.103.coordinated-tags-and-releases.md`
- [x] (17.4) `.agents/tasks/closed/alpha/TASK.alpha.104.workspace-release-manifest-and-verification.md`

## Phase 18 — Codebase Coherence Audit (EPIC.audit.002.codebase-coherence-and-policy-adherence)
- [x] (18.1) `.agents/tasks/closed/audit/TASK.audit.201.junie-onboarding-and-audit-recon.md`
- [x] (18.2) `.agents/tasks/closed/audit/TASK.audit.202.audit-criteria-refinement-v1.md`
- [x] (18.3) `.agents/tasks/closed/audit/TASK.audit.203.audit-pass1-codebase-compliance.md`
- [x] (18.4) `.agents/tasks/closed/audit/TASK.audit.204.findings-to-remediation-tasking-v2.md`
- [x] (18.5) `.agents/tasks/closed/audit/TASK.audit.205.strict-reaudit-plan-and-handoff.md`

## Phase 19 — Gateway Session Credential Passing (EPIC.alpha.026.gateway-session-credential-passing)
- [x] (19.1) `.agents/tasks/closed/alpha/TASK.alpha.105.authz-session-get-operation.md`
- [x] (19.2) `.agents/tasks/closed/alpha/TASK.alpha.106.authz-client-session-get.md`
- [x] (19.3) `.agents/tasks/closed/alpha/TASK.alpha.107.authz-service-session-handler.md`
- [x] (19.4) `.agents/tasks/closed/alpha/TASK.alpha.108.gateway-cookie-to-credential-refactor.md`

## Phase 20 — Audit Pass-2 Remediation Backlog (EPIC.audit.002.codebase-coherence-and-policy-adherence)
- [x] (20.1) `.agents/tasks/closed/audit/TASK.audit.206.workspace-default-gate-composite-resolution.md`
- [x] (20.2) `.agents/tasks/closed/audit/TASK.audit.207.lib-test-baseline-and-lock-alignment.md`
- [x] (20.3) `.agents/tasks/closed/audit/TASK.audit.208.roadmap-task-lifecycle-hygiene-pass2.md`
- [x] (20.4) `.agents/tasks/closed/audit/TASK.audit.209.docs-transport-currency-pass2.md`
## Notes
- When a TASK is closed, move it to `.agents/tasks/closed/` and update its checkbox here.
- When this ROADMAP is complete, move it to `.agents/roadmaps/closed/` and version the filename.
