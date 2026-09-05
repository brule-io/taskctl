---
title: TASK.operator.1159.converse-prompt-tool-call-loop
epic: EPIC.operator.148.converse-tool-call-loop-and-copyability
impact: HIGH
effort: HIGH
suggestion: OPEN
tags:
  - operator
  - converse
  - tools
depends_on:
  - TASK.core.388.shell-exec-agent-prompt-contract
blocks:
  - TASK.operator.1160.converse-copyable-evidence-surface
  - TASK.operator.1161.converse-tool-call-browser-audit
owner: codex
---

# Goal

Allow Converse to detect a bounded model-emitted tool-call payload and execute
the referenced visible tool through the existing host tool invocation contract.

# Acceptance

- prompt responses can be inspected for a supported tool-call JSON payload
- visible-tool calls dispatch `geist.session.tool.invoke` or detached invoke as
  appropriate
- malformed, unknown, or invisible tool calls fail honestly and remain visible
  to the operator
- successful tool calls refresh session detail, tool catalog, and related
  runtime truth

# Verification

- `./gradlew -p apps/geist-operator :composeApp:jvmTest --tests os.daemon.geist.apps.operator.state.GeistOperatorHostCommandExecutorTest --tests os.daemon.geist.apps.operator.state.GeistOperatorReducerTest`

# Closeout

- Converse now parses bounded JSON tool-call payloads from model output,
  including `tool_call`, `detached_tool_call`, and the shell shortcut form
- successful tool calls dispatch existing host tool-invocation actions and
  refresh session detail plus tool-catalog truth afterward
- malformed or unsupported payloads remain visible without pretending the tool
  ran
