---
title: TASK.core.388.shell-exec-agent-prompt-contract
epic: EPIC.core.077.tool-catalog-prompt-contract-hardening
impact: HIGH
effort: MEDIUM
suggestion: OPEN
tags:
  - core
  - tools
  - prompt
  - shell
depends_on: []
blocks:
  - TASK.operator.1159.converse-prompt-tool-call-loop
owner: codex
---

# Goal

Render prompt-facing tool truth explicitly enough that Geist can identify how
to call `shell.exec` and other visible tools without guessing argument names.

# Acceptance

- prompt-facing tool context includes bounded per-tool argument guidance
- `shell.exec` explicitly exposes `command`, optional `cwd`, and optional
  `detached`
- operator prompt-context injection preserves deterministic ordering and stays
  compact enough for regular Converse prompts

# Verification

- `./gradlew -p services/geist-runtime :runtime-service:test --tests os.daemon.geist.runtime.tool.catalog.*`
- `./gradlew -p apps/geist-operator :composeApp:jvmTest --tests os.daemon.geist.apps.operator.state.GeistOperatorSteeringCommandReducerTest`

# Closeout

- `shell.exec` prompt-facing truth now includes explicit argument guidance for
  `command`, optional `cwd`, and optional `detached`
- operator tool-registry context now emits deterministic per-tool description,
  bounded args summaries, and a lawful JSON tool-call contract
- fresh sessions now observe built-in tool descriptors immediately, so
  `shell.exec` and `patch.apply` are visible without waiting for later rebuilds
