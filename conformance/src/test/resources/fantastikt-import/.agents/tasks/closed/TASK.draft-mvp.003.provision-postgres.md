---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.003
roadmap: ROADMAP.draft-mvp.001
effort: MEDIUM
impact: HIGH
depends:
  - TASK.draft-mvp.001
---

# TASK.draft-mvp.003: Provision durable local PostgreSQL

## Description

Provide the Docker Compose PostgreSQL runtime, durable database and artifact mounts, health checks, and safe configuration examples required for offline draft operation.

## Requirements

- Own `compose.yaml`, container initialization/health assets, `.env.example`, and ignore rules for local data and secrets.
- Use a named volume for PostgreSQL and a durable bind mount or volume for `BALL_DATA_DIR`; container recreation must not erase either.
- Never commit credentials, ESPN cookies, FantasyPros keys, or live league identifiers.

## Deliverables

- [x] Compose starts a healthy PostgreSQL service with the specified JDBC defaults and persistent volumes.
- [x] A stop/remove/recreate smoke test preserves a sentinel database row and raw artifact.
- [x] Configuration documentation distinguishes committed examples from operator secrets.

## Closure

```yaml
closed_at: 2026-09-03T02:19:38.783278500Z
closed_by: Codex
summary: Provisioned loopback-only Docker Compose PostgreSQL with named database durability, a durable raw-artifact bind mount, credential-safe examples, health checks, and a recreation smoke test.
verification:
  - command: .\docker\postgres\persistence-smoke.ps1 -> database sentinel TASK.draft-mvp.003 preserved; artifact sha256 59a77b5f2666d9c85c489bd1911a6eebbd91ef22fe48b90a3b75f1b21f3844d4 preserved; recreated container 2deb7298f970ed41fb6805bec512da2dee4364af0707719813d89eec4800a629 healthy
  - command: docker compose ps --format json -> postgres running healthy on 127.0.0.1:5432 with one local named volume and /data bind mount
  - commands: docker compose config --quiet; Git Bash -n docker/postgres/healthcheck.sh; .\gradlew.bat verify; taskctl doctor; adrctl doctor; git diff --check -> all exit 0, Gradle BUILD SUCCESSFUL
  - command: git check-ignore -v .env .data/etl/task-003-smoke/raw-artifact.txt -> .env and .data are ignored; .env.example documents placeholder-only local secrets
follow_ups:
  - none
```
