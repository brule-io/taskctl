---
title: TASK.operator.1160.converse-copyable-evidence-surface
epic: EPIC.operator.148.converse-tool-call-loop-and-copyability
impact: MEDIUM
effort: MEDIUM
suggestion: OPEN
tags:
  - operator
  - converse
  - copyability
depends_on:
  - TASK.operator.1159.converse-prompt-tool-call-loop
blocks:
  - TASK.operator.1161.converse-tool-call-browser-audit
owner: codex
---

# Goal

Make the important Converse text/evidence surfaces copyable instead of
truncated, non-interactive `BasicText` blocks.

# Acceptance

- operator can copy prompt/tool/scenario/message evidence from Converse
- long message and tool-context blocks remain readable without ellipsis-only
  loss
- copy affordances fit the current midnight design language

# Verification

- `./gradlew -p apps/geist-operator :composeApp:jvmTest --tests os.daemon.geist.apps.operator.ui.converse.*`

# Closeout

- Converse evidence blocks now use copyable surfaces instead of truncated
  `BasicText`
- prompt context, scenario facts, tool availability, and model/tool output are
  readable and copyable from the live page
