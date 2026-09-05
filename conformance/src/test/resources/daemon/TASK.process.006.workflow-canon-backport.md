# TASK: Workflow Canon Backport

Epic: `.agents/epics/closed/EPIC.process.001.os-governance-and-artifact-truthfulness.md`
Owner: `codex`

## Goal

Backport Platform's clearer workflow canon into OS root docs and planning
surfaces without flattening OS into Platform or pretending the CI/auth blocker
is solved.

## Deliverables

- one OS-specific workflow canon surface that explains loop, train rails,
  and verification lanes in terms of current OS reality
- root references updated so agents read that canon before the older
  lane-economy material
- `.agents` tasking and roadmap references that point future agents at the
  canonical OS workflow

## Assets

- `AGENTS.md`
- `README.md`
- `docs/index.md`
- `docs/ci-cd/index.md`
- `daemon-eng/os/daemon-os-workspace#5`

## Acceptance

- later agents can recover OS workflow truth from root docs without inferring it
  from scattered notes
- the canon remains truthful about `checkIn`, `fullPipeline`,
  and the separate CI/auth blocker
- lane-economy material is clearly marked as supplemental context

## Closure

Status: closed on 2026-04-09 14:01 PDT

- OS MR `!9` merged the workflow canon backport into `development`.
- Issue `daemon-eng/os/daemon-os-workspace#5` is closed.
- The root canon now points agents at `README.md`, `docs/index.md`, and
  `docs/ci-cd/index.md` before the older lane-economy material.
