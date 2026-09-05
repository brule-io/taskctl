# `.agents/` Planning + Execution System

This directory holds the planning system for Geist.

The workflow is TASK-centric:

- ROADMAP selects ordering
- EPIC groups a bounded outcome
- TASK defines executable work

## Invariants

- At least one active roadmap exists in `.agents/roadmaps/open/`.
- Every roadmap item references a task file.
- Every task references exactly one epic.
- `ASSETS.md` is updated whenever build outputs change.

## Current Posture

This repo is in bootstrap mode.

- The workspace reset and Kotlin foundation are complete in the working tree.
- The roadmap starts with the next real implementation slices from this fresh
  baseline.

## Close-Out Rules

- Implement against one task at a time.
- Verify with `./gradlew checkIn` unless a task defines a narrower lane.
- Move completed tasks to `.agents/tasks/closed/` and update roadmap checkboxes.

