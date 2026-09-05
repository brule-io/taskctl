# Repository task ledger

The `.agents` ledger is the repository-owned authority for implementation work. `taskctl` parses every epic, roadmap, and task before exposing the actionable Kahn frontier.

## Cold workflow

```powershell
.\tooling\taskctl\taskctl.cmd doctor
.\tooling\taskctl\taskctl.cmd frontier
.\tooling\taskctl\taskctl.cmd plan
.\tooling\taskctl\taskctl.cmd show TASK.namespace.000
```

Use the corresponding `.sh` launcher on POSIX. Select one frontier task, then read its task, roadmap, epic, and applicable accepted ADRs in the order required by `AGENTS.md`.

## Layout

```text
.agents/
  epics/open/       long-horizon outcomes
  epics/closed/     completed outcome records
  roadmaps/open/    ordered capability gates
  roadmaps/closed/  completed gates
  tasks/open/       actionable or dependency-blocked work
  tasks/closed/     evidence-bearing completed work
  schema/           canonical ledger format
  templates/        authoring starting points
```

Lifecycle state is derived exclusively from the containing `open` or `closed` directory. Do not add persisted status fields, reverse dependency fields, or a parallel tracker.

## Authoring

- Copy the matching template and follow [the schema](schema/ledger-v1.md).
- Use immutable refs and canonical filenames.
- `depends` contains only forward prerequisites and is sorted lexically. `taskctl` derives reverse blocking edges and rejects cycles.
- Requirements are frozen constraints. Deliverables and roadmap exit criteria are observable checkboxes.
- Name owned files or directory boundaries in task requirements. If two tasks must touch the same integration file, order them explicitly.
- Keep operator credentials, deployments, external writes, and releases behind explicit tasks and authority.

After editing ledger records, run `taskctl doctor` and inspect the complete `taskctl plan`, not only the first frontier.

Root specifications follow the admission process in [`docs/arch/specifications/README.md`](../docs/arch/specifications/README.md). A candidate does not authorize product implementation until its admission task closes with an exact digest and coverage evidence.

## Closure

Close a task only after every deliverable is checked, every dependency is closed, and exact evidence exists:

```powershell
.\tooling\taskctl\taskctl.cmd close TASK.namespace.000 `
  --summary "Observable result" `
  --evidence "command: exact output or artifact" `
  --expect-frontier TASK.namespace.001
```

Use `--expect-empty-frontier` only when the whole open graph should be exhausted. Close a roadmap only when all member tasks are closed and every exit criterion is checked. Closure tooling mutates only the selected ledger record and never creates a Git commit.
