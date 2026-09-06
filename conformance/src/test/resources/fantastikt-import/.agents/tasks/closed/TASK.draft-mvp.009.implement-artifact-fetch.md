---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.009
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.005
  - TASK.draft-mvp.006
  - TASK.draft-mvp.007
---

# TASK.draft-mvp.009: Implement content-addressed artifact fetching

## Description

Implement the shared streaming fetch stage that durably records every remote response before typed transformation.

## Requirements

- Own `etl-core` artifact/fetch packages and their filesystem/HTTP tests.
- Apply bounded timeouts, retry only idempotent failures, stream through SHA-256 into a temporary file, then atomically place content under `.data/etl/<source>/<sha256>/`.
- Record URI, length, checksum, content type, ETag, Last-Modified, fetch time, and schema fingerprint while excluding credentials and authentication headers.

## Deliverables

- [x] CSV, CSV.GZ, and JSON fixtures prove streaming, checksum reproducibility, atomic placement, and minimum-size/content-type validation.
- [x] Same checksum and configuration reuse evidence without duplicating canonical artifact rows.
- [x] Interruption and retry tests leave no corrupt artifact presented as complete.

## Closure

```yaml
closed_at: 2026-09-03T03:03:05.260368800Z
closed_by: Codex
summary: Implemented bounded GET fetching, streaming SHA-256 measurement, atomic content-addressed filesystem promotion, narrow provenance metadata capture, canonical digest reuse, contract validation, and retry/interruption cleanup in etl-core.
verification:
  - .\gradlew.bat :etl-core:clean :etl-core:test => BUILD SUCCESSFUL; 7 tests in 1 suite, 0 failures, 0 errors, 0 skipped.
  - HTTP/filesystem fixtures players.csv, stats.csv.gz, and response.json proved byte-for-byte streaming, reproducible SHA-256, .data/etl-style source/digest/filename placement, empty staging directories, ETag/Last-Modified/content-type/fetch-time/schema metadata, and minimum-size/content-type validation after durable retention.
  - Sequential same-body requests with config-v1 returned one canonical metadata record and reused the first StoredArtifact; transient HTTP 503 retried once while HTTP 400 did not retry; both complete responses were retained before status handling.
  - InterruptedInputStream preserved the thread interrupt with 0 durable files, and a separate truncated IOException retried to a complete artifact with exactly 1 final file and 0 staging files; request-header rendering redacted Authorization and sensitive query names were rejected.
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL; 55 tasks (54 executed, 1 up-to-date), 11 admitted module boundaries, 29 product files, all PostgreSQL tests, and healthy task/ADR ledgers.
follow_ups:
  - none
```
