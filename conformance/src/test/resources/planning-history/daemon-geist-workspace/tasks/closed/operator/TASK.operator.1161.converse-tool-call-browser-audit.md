---
title: TASK.operator.1161.converse-tool-call-browser-audit
epic: EPIC.operator.148.converse-tool-call-loop-and-copyability
impact: HIGH
effort: MEDIUM
suggestion: OPEN
tags:
  - operator
  - browser
  - converse
  - tools
depends_on:
  - TASK.operator.1159.converse-prompt-tool-call-loop
  - TASK.operator.1160.converse-copyable-evidence-surface
blocks: []
owner: codex
---

# Goal

Capture a browser-level receipt proving Converse can drive a bounded tool call
and that the resulting text/evidence is copyable.

# Acceptance

- one browser proof exercises Converse prompt submission, tool-call execution,
  and refreshed evidence
- receipt captures the post-tool state clearly enough to debug regressions
- the flow documents any remaining safety restrictions around `shell.exec`

# Verification

- `./gradlew -p apps/geist-operator operatorUiFlowReport`

# Closeout

- Playwright flow `FLOW.operator-ui.0026` proves the prompt contract contains
  explicit `shell.exec` guidance, Converse emits a lawful tool-call JSON
  payload, and the UI dispatches the resulting host tool invocation
- receipt lives under `build/operator-ui-flows/receipts/FLOW.operator-ui.0026.json`
