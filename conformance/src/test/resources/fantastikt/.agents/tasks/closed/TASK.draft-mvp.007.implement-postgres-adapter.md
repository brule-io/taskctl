---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.007
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.005
  - TASK.draft-mvp.006
---

# TASK.draft-mvp.007: Implement the isolated PostgreSQL adapter

## Description

Implement transaction, connection, migration, provenance, and test-harness adapters with all Exposed details contained inside `durability-postgres`.

## Requirements

- Own `durability-postgres/src/main/kotlin` foundation packages and Testcontainers infrastructure; later tasks own feature-specific tables and queries.
- Use Exposed DAO for ordinary access and DSL/batch operations for ETL, while returning only domain and port types.
- Provide transaction boundaries suitable for one atomic ETL merge or draft event append.

## Deliverables

- [x] Connection, migration status, ETL run, artifact, and rejection ports operate against Testcontainers PostgreSQL.
- [x] Public API inspection finds no Exposed type outside the adapter.
- [x] Failure tests prove transaction rollback and durable evidence for failed runs.

## Closure

```yaml
closed_at: 2026-09-03T02:51:35.089759300Z
closed_by: Codex
summary: Implemented an isolated Hikari/Flyway/Exposed PostgreSQL foundation adapter for connectivity, migration status, ETL identifiers, source metadata, runs, artifacts, rejections, and explicit atomic transaction boundaries, backed by self-contained Testcontainers PostgreSQL infrastructure.
verification:
  - .\gradlew.bat :durability-postgres:clean :durability-postgres:postgresIntegrationTest => BUILD SUCCESSFUL; 6 tests across 2 suites, 0 failures, 0 errors, 0 skipped; ephemeral PostgreSQL 17.11 containers closed explicitly.
  - PostgresFoundationAdapterTest proved connectivity and migration status PASS, source DAO upsert, run/artifact/rejection persistence, outer transaction rollback of nested port calls, and durable FAILED run completion/error evidence after rollback.
  - javap -public inspection of PostgresSettings, PostgresFoundationAdapter, and its companion => public_api_exposed_signatures=0; production Exposed imports occur in exactly 2 files, both under durability-postgres/src/main/kotlin.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL; 49 tasks executed including postgresIntegrationTest; 11 admitted module boundaries and 24 product implementation/resource files verified; task and ADR ledgers healthy.
follow_ups:
  - none
```
