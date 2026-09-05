Doc type: `ROADMAP`

# ROADMAP.geist.converse-tool-call-and-copyability

## Scope and posture

- Fix the live Converse bug where tool registry context is too lossy for the
  model to use `shell.exec` and other visible tools confidently.
- Add a lawful prompt-response tool-call loop for Converse over the existing
  host `geist.session.tool.invoke` contract.
- Make Converse evidence copyable so operators can lift prompts, tool context,
  and model/tool output directly from the UI.

## Global Gates

- keep `development` green throughout the slice
- do not regress existing manual Tools-route invocation behavior
- preserve current session prompt transport while adding tool-call follow-up
- keep prompt-context rendering deterministic and bounded
- prefer additive operator behavior over broad runtime orchestration rewrites

## Milestones

- [x] `EPIC.core.077.tool-catalog-prompt-contract-hardening`
- [x] `EPIC.operator.148.converse-tool-call-loop-and-copyability`

## Slice 1: Tool contract truth

- [x] `.agents/tasks/closed/core/TASK.core.388.shell-exec-agent-prompt-contract.md`

## Slice 2: Converse tool-call loop

- [x] `.agents/tasks/closed/operator/TASK.operator.1159.converse-prompt-tool-call-loop.md`
- [x] `.agents/tasks/closed/operator/TASK.operator.1160.converse-copyable-evidence-surface.md`
- [x] `.agents/tasks/closed/operator/TASK.operator.1161.converse-tool-call-browser-audit.md`

## Acceptance

- prompt context supplied from Operator includes enough tool truth for the
  model to identify `shell.exec` and its `command`/`cwd`/`detached` arguments
- Converse can detect a supported tool-call JSON response and execute the
  referenced visible tool through the host
- tool execution remains constrained by the existing agent-visible catalog and
  host validation
- Converse text and evidence surfaces are copyable without leaving the page
- browser proof covers at least one `shell.exec` or debug-tool call path from
  Converse

## Notes

- This roadmap does not introduce provider-native function calling.
- This roadmap does not redesign the Tools route or replace manual tool
  invocation controls.

## Closeout

- runtime `checkIn` passed after fresh-session built-in tool visibility and
  shell-exec prompt-contract hardening
- operator `checkIn` passed with the new Converse loop and copyable evidence
  surfaces
- live browser receipt `FLOW.operator-ui.0026` proves the shell-exec
  prompt-to-tool path
