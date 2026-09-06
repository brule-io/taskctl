---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.006
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.003
  - TASK.draft-mvp.004
---

# TASK.draft-mvp.006: Create Flyway foundation migrations

## Description

Establish Flyway ownership and the PostgreSQL foundation for metadata, ETL provenance, artifacts, runs, and rejection evidence.

## Requirements

- Own the initial numbered migrations and Flyway configuration under `durability-postgres/src/main/resources/db/migration`.
- Implement all Section 8 provenance fields, uniqueness constraints, timestamps, and durable artifact paths without secret-bearing columns.
- Migrations must be forward-only and repeatable from an empty database, with deterministic Flyway schema/version state that Task 007 exposes through the migration-status port already owned by Task 005.

## Deliverables

- [x] An empty PostgreSQL database migrates to current with provenance and ETL lifecycle constraints intact.
- [x] Migration integration tests prove duplicate artifact SHA handling and rejection/run referential integrity.
- [x] Schema inspection demonstrates no credential or authentication-header storage.

## Closure

```yaml
closed_at: 2026-09-03T02:40:56.756677100Z
closed_by: Codex
summary: Added deterministic Flyway V001 PostgreSQL provenance tables for ETL sources, runs, artifacts, and rejections with forward-only lifecycle, digest, durable-path, and referential constraints plus isolated real-database integration tests.
verification:
  - .\gradlew.bat :durability-postgres:clean :durability-postgres:postgresIntegrationTest => BUILD SUCCESSFUL against PostgreSQL 17.11; 4 tests, 0 failures, 0 errors, 0 skipped; Flyway applied V001 once and a second migrate executed 0 migrations.
  - Migration integration assertions observed SQLSTATE 23505 for duplicate artifact SHA-256, 23503 for a rejection referencing an artifact from another run, and 23514 for invalid durable paths and incoherent run lifecycle/counts.
  - Schema inspection asserted the exact Section 8 columns for etl_source, etl_run, etl_artifact, and etl_rejection; secret_bearing_schema_terms=0; migration SHA-256=4848348CA14677499BBDC3F851CF1C80158D767D723B37EA3081F9308F81FE4A; residual task_006 test schemas=0.
  - .\gradlew.bat verify => BUILD SUCCESSFUL; 11 admitted module boundaries and 21 product implementation/resource files verified; task and ADR ledgers healthy.
follow_ups:
  - none
```
