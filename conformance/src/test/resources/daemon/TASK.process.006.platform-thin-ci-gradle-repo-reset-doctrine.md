# TASK: Platform Thin-CI / Gradle-First and Repo-Reset Doctrine

Epic: `.agents/epics/closed/EPIC.process.001.os-governance-and-artifact-truthfulness.md`
Owner: `codex`

## Goal

Anchor the current substantive OS follow-up for Platform-style thin CI, Gradle-owned workflow
logic, and explicit repo-reset posture without pretending the earlier governance issues fully cover
that line.

## Deliverables

- one local task anchor for `daemon-eng/os/daemon-os-workspace#12`
- one explicit reference to Platform source doctrine at
  `daemon-eng/platform/daemon-platform-workspace#1`
- one truthful note describing how this doctrine line differs from the earlier MR/train and
  submodule-auth follow-ups

## Assets

- `.agents/roadmaps/open/ROADMAP.os.governance-alignment.phase1.md`
- `.agents/epics/closed/EPIC.process.001.os-governance-and-artifact-truthfulness.md`
- `daemon-eng/os/daemon-os-workspace#12`
- `daemon-eng/platform/daemon-platform-workspace#1`

## Acceptance

- later agents can recover the active substantive OS doctrine line from local tasking
- the current issue line is distinct from the earlier issue `#1` / MR `!2` / auth blocker `#3`
- the Platform source-doctrine issue is cited explicitly as upstream guidance

## Recon

- This task is planning/doctrine only. It does not implement CI or change build/runtime behavior.
- The OS-specific seam guardrail remains in force: no claim that missing downstream HV lanes are
  already present in the current root.

## Closure

Status: closed on 2026-04-10 08:03 PDT

- The local doctrine line was first anchored on `development` by merge `6fc6406`
  (`task/os-platform-ci-doctrine-line`).
- The operative thin-CI / repo-reset doctrine later landed in
  `.agents/tasks/closed/process/TASK.process.011.thin-ci-gradle-first-and-repo-reset-doctrine.md`
  and the root policy surfaces merged at `1401a85`.
- Keep `daemon-eng/os/daemon-os-workspace#12` open as the broader issue line
  until further repo-local work is explicitly tasked.
