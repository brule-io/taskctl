---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.001
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
---

# TASK.draft-mvp.001: Admit the Sunday Draft specification and seed its ledger

## Description

Turn the root `SPEC.md` candidate into repository-owned implementation authority by recording its exact digest and review, repairing the bootstrapped onboarding documentation, and persisting a dependency-complete Sunday MVP task graph.

## Requirements

- `SPEC.md` is the sole source of product decomposition; task text may narrow ownership and proof but must not add product scope.
- Review every normative P0 requirement, acceptance criterion, test, operational gate, input, and explicit non-goal for ambiguity, contradiction, and ownership.
- Record the exact SHA-256 digest of the reviewed specification and a requirement-to-task coverage index.
- Create at least twenty implementation tasks under this roadmap with non-overlapping owned files or explicitly sequenced overlap.
- Repair the onboarding paths named by `AGENTS.md` and document the actual `loom.agent/v1` schema without changing lifecycle semantics.
- Do not implement product functionality in this admission task.

## Deliverables

- [x] A specification admission record contains the exact reviewed digest, disposition, scope boundaries, and requirement-to-task coverage index.
- [x] Canonical engineering, task-ledger, and ADR onboarding documents exist at every path required by `AGENTS.md`.
- [x] One epic, one roadmap, and at least twenty implementation tasks encode all P0 work, operator gates, dependencies, file ownership, and narrowest proof.
- [x] `taskctl doctor`, `taskctl plan`, `adrctl doctor`, and the repository verification build pass with an actionable post-admission frontier.

## Closure

```yaml
closed_at: 2026-09-03T01:51:32.758987400Z
closed_by: codex-genesis
summary: Admitted the Sunday Draft MVP specification and persisted one epic, one roadmap, and 39 implementation and operation tasks.
verification:
  - SPEC canonical LF SHA-256=4bd2c0353fa208850ab82e49bacb5e502f998935d39071daf45b7c52dc03b1eb
  - SPEC Windows checkout SHA-256=9bb2634d556055afd9382eefb4d0c035a73ea7d2542e358438238e8a09769947
  - candidate source commit=db047da66239d441d5923755db9225e4d750e425
  - ledger scan=40 contiguous tasks, 27 Kahn layers, root TASK.draft-mvp.001, terminal TASK.draft-mvp.040, 145 requirements, 128 deliverables
  - fresh-clone closure rehearsal at 45d890377ccbf2325e7a65841a94ebb5c53f086e=39 open, 1 closed, exact frontier TASK.draft-mvp.002 and TASK.draft-mvp.003
  - taskctl doctor=tasks 40 open, 0 closed; roadmaps 1 open, 0 closed; epics 1 open, 0 closed; doctor ok
  - taskctl plan=layers 0 through 26
  - adrctl doctor=0 proposed, 0 accepted, 0 superseded, 0 rejected; doctor ok
  - gradlew.bat clean verify --warning-mode all=BUILD SUCCESSFUL in 10s; 9 actionable tasks, 8 executed, 1 up-to-date
  - GitHub Actions pre-close=https://github.com/brule-io/fantastikt/actions/runs/33702860759 success at 3afc9b23fd074652bf39d427f77d303053b3c059
  - post-close frontier=TASK.draft-mvp.002 and TASK.draft-mvp.003
follow_ups:
  - none
```
