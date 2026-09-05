# `.agents/` Planning + Execution System (ROADMAP → EPIC → TASK)

This directory defines **how work is planned, executed, verified, and closed** in this repo.
For repo-level onboarding and guardrails, see `AGENTS.md`. For platform intent and structural truths, see `SPEC.md`.
For the end-to-end delivery loop, see `docs/development/modules/ROOT/pages/workflow/march-loop.adoc`.

The workflow is **TASK-centric**: TASKs are the unit of execution; ROADMAP ordering is authoritative.

## Assets-first doctrine
- Assets or it didn't happen: every TASK must name tangible build outputs and how to produce them.
- `ASSETS.md` is the source of truth for expected + produced distribution artifacts.
- Acceptance criteria must include asset verification steps; update `ASSETS.md` when assets are added or changed.

## Roles (two-agent split)

### `codex-2` (Ops/QA/Docs) — planning + verification layer
- Owns `.agents/**` authoring (ROADMAP/EPIC/TASK scope), templates, and workflow policy.
- Owns documentation and QA harness/plans/compliance gates.
- Reviews `codex-1` completion packets against Acceptance criteria and files follow-up TASKs.
- May implement small test-only changes when no refactor is required; otherwise file a TASK for `codex-1`.

### `codex-1` (Builder) — implementation layer
- Owns product implementation and tests for the active milestone.
- Does **not** change ROADMAP/EPIC/TASK scope (Goal/Deliverables/Acceptance) unless the operator explicitly directs.
- Updates TASK `Recon` during implementation.
- Performs lifecycle close-out: move TASK/EPIC files, mark ROADMAP checkboxes complete, append Closure notes.
  - May **infill** TASK details during execution (add clarifying sub-steps, acceptance commands, or constraints) as long as intent does not change.

### Management Codex In Charge (operator-designated)
- Acts as the coordinator for multi-agent execution and QA gating.
- Only this role may spawn/terminate workers or manage tmux automation.
- Delegates scoped TASKs to workers and validates completion packets.

### Policy A (multi-agent default)
- Workers may update `Recon` and `Closure`, move their TASK to `.agents/tasks/closed/`, and update the ROADMAP checkbox for assigned tasks.
- Management Codex performs final QA gating and merges worker branches into `main`.
  - This is the only approved scope for worker edits under `.agents/**`.

## Merge-conflict guardrails (directory ownership)

To avoid merge hell, changes are scoped by directory:

- `.agents/AGENTS.md` and `.agents/templates/**`: `codex-2` only.
- `.agents/roadmaps/**`:
  - `codex-2` authors roadmap content and ordering.
  - `codex-1` may update checkboxes (`[ ]` → `[x]`) and move ROADMAP files from `open/` → `closed/` during close-out.
- `.agents/epics/**`:
  - `codex-2` authors epic content (Objective/Scope/Success Criteria/Non-Goals).
  - `codex-1` may update `status` and move EPIC files from `open/` → `closed/` during close-out.
- `.agents/tasks/**`:
  - `codex-2` authors task scope (Goal/Deliverables/Acceptance).
  - `codex-1` may append to `Recon` and `Closure`, and move TASK files from `open/` → `closed/` during close-out.
  - `codex-1` may **append** detail to `Deliverables`/`Acceptance` for execution clarity (no scope change).

If a change crosses these boundaries, `codex-2` files a TASK that explicitly grants scope and names the owner.

## Active roadmaps and priority

The default planning posture is still intentionally narrow, but it now supports bounded concurrency.

- Default posture:
  - `1` primary roadmap
  - optional `0-2` secondary roadmaps
- Maximum active roadmap count in `.agents/roadmaps/open/`:
  - `3`
- Management Codex owns:
  - which roadmap is primary,
  - which secondary roadmaps may run concurrently,
  - merge order when active roadmaps overlap.

Priority tiers:

- `P0`
  - train health, CI recovery, release blockers, branch alignment
- `P1`
  - active delivery roadmap for the current train
- `P2`
  - bounded-parallel supporting work
- `P3`
  - backlog shaping, R&D, low-urgency process work

Rules:

- `P0` preempts all other work.
- `P1` is the normal primary-roadmap tier.
- `P2` may run with `P1` only when touched surfaces are disjoint or merge order is explicit.
- `P3` yields to merge pressure, CI incidents, or any `P0/P1` contention.

Reference memos:

- `.agents/memos/MEMO.process.multi-roadmap-contract.md`
- `.agents/memos/MEMO.process.worktree-lane-matrix.md`
- `.agents/templates/workers/MULTI_ROADMAP_DISPATCH.template.md`

## Invariants vs preferences

### Invariants (must exist for the workflow to function)
- At least one active ROADMAP exists in `.agents/roadmaps/open/`.
- Exactly one active roadmap is designated the primary roadmap for default task selection and reporting.
- Optional secondary roadmaps are allowed only under Management Codex ownership and within the active-roadmap cap.
- Every executable unit of work has a TASK file under `.agents/tasks/open/<namespace>/`.
- Every TASK references exactly one EPIC (by ID) and includes clear `Deliverables` and `Acceptance`.
- Every TASK includes an explicit asset list and updates `ASSETS.md` when assets change or are introduced.
- Every ROADMAP item references a TASK file path and tracks completion via checkboxes.
- Close-out moves TASKs from `open/` → `closed/`, appends a Closure note, and updates the ROADMAP checkbox.
- Scope control: only `codex-2` changes `Goal`/`Deliverables`/`Acceptance` intent once a TASK is “in execution”.
  - `codex-1` may **infill** by adding specificity (commands, paths, validation steps) without changing intent.

### Preferences (defaults; can be changed without breaking the system)
- Serial numbers are zero-padded to three digits (`001`); any sortable convention is acceptable if consistent.
- Branch naming (e.g., `task/<namespace>/<nnn>-<slug>`) and commit conventions are recommended but not required.
- EPICs may optionally include an explicit task checklist; the ROADMAP is still the authoritative ordering.

## Directory layout

- `.agents/roadmaps/open/` — exactly one active roadmap file
- `.agents/roadmaps/closed/` — completed roadmaps (versioned filenames)
- `.agents/epics/open/` — active epics
- `.agents/epics/closed/` — completed epics
- `.agents/tasks/open/<namespace>/` — executable tasks grouped by namespace
- `.agents/tasks/closed/` — completed tasks
- `.agents/templates/` — schemas/templates for authoring ROADMAP/EPIC/TASK

## Naming conventions

- ROADMAP: `ROADMAP.<project>.<slug>.md`
- EPIC: `EPIC.<namespace>.<nnn>.<slug>.md`
- TASK: `TASK.<namespace>.<nnn>.<slug>.md`

Where:
- `<namespace>` is thematic and stable (e.g., `core`, `ops`, `lineage`).
- `<nnn>` is a serial indicating intended implementation order within the namespace.
- `<slug>` is kebab-case.

## Document schemas (required structure)

Canonical templates live in `.agents/templates/`.

### ROADMAP
- Top-of-file metadata block:
  - `Doc type: \`ROADMAP\``
  - `Supersedes:` list (optional; file paths or IDs)
- Required sections:
  - `Scope and assumptions`
  - `Global gates`
  - `Decision needed` (when policy is ambiguous)
  - One or more ordered phases listing checklist items that reference TASK paths.

### EPIC
- Top-of-file metadata block:
  - `Namespace: \`<namespace>\``
  - `Slug: \`<slug>\``
  - `Status: open|closed`
- Required sections:
  - `Objective`
  - `Scope`
  - `Success criteria`
  - `Non-goals`
  - `Guardrails`

### TASK
- YAML front matter:
  - Required: `title`, `epic`, `impact`, `effort`, `suggestion`, `tags`, `depends_on`, `blocks`, `owner`
- Conventions:
  - `title` matches the TASK ID (and filename).
  - `impact`/`effort` use `LOW|MEDIUM|HIGH`.
  - `depends_on`/`blocks` are lists of TASK IDs (not file paths).
- Required sections:
  - `Goal`
  - `Deliverables`
  - `Assets`
  - `Acceptance`
  - `Recon`
  - `Closure` (added/filled at close-out)

## Execution workflow

### Canonical roadmap
Use the primary roadmap in `.agents/roadmaps/open/` as the default authoritative execution order unless the operator explicitly directs otherwise.

If multiple active roadmaps exist:

- the primary roadmap answers "pick the next roadmap item",
- secondary roadmaps authorize only their explicitly assigned bounded slices,
- workers must be told which roadmap they are executing against.

### Operator approval semantics
- Default: treat TASK close-out as requiring explicit operator approval.
- If the operator says **“operate autonomously”** (or equivalent), treat that as **operator approval** to:
  - run QA gates (`make lint`, `make test`, `make dryrun`) as needed
  - commit changes
  - perform TASK close-out steps (ROADMAP checkbox updates, open→closed moves, Closure notes, and EPIC close-out when empty)

### 0) Preconditions
- Working tree is clean before starting a TASK (no unrelated local changes).
- If a clean tree is not possible, explicitly isolate changes so close-out remains atomic.
- For parallel Codex workers on submodule work, use full repo clones under `.workers/` to avoid shared submodule worktree conflicts.
- For root-only concurrent process/docs work, prefer `.worktrees/` over sharing one root checkout.

### 1) Select work (ROADMAP-first)
- If asked to “pick next roadmap item”: open the canonical ROADMAP and choose the first unchecked TASK.
- If asked to implement a specific TASK: confirm it appears on the active ROADMAP (or note explicitly if it does not).
- If multiple active roadmaps exist, confirm whether the task belongs to the primary or a secondary roadmap before starting.
- Execute **one TASK at a time** in linear order; do not start the next TASK until the current TASK is verified and closed out.
- Do not begin the next TASK until CI for the implementation commit is **green**. If CI is still running, wait.
- Read the EPIC, then the TASK, then relevant repo docs (`SPEC.md`, `Makefile`, code).

### 2) Implement (scope discipline)
- Implement until `Deliverables` and `Acceptance` are satisfied.
- Record discoveries in `Recon` (paths, commands, edge cases, decisions).
- If additional specificity is needed, infill the TASK with clarifying sub-steps or acceptance checks (do not change intent).
- Keep changes minimal and focused; file follow-up TASKs instead of expanding scope.

### 3) Verify (evidence)
- Stop making functional changes (“code freeze”).
- Run the repo’s lint/tests/dry-run gates appropriate to the TASK.
- Select gates according to the documented change-scope lane policy; not every task needs the full release lane.
- Prefer `make lint`, `make test`, and `make dryrun` as the default gates for this repo.
- Before concluding verification, prefer running `./gradlew checkIn` (or the repo-local equivalent) to anticipate CI outcomes.
- For **all code changes**, run tests/builds and manual verification steps appropriate to the change.
  - Skipping any verification step requires explicit operator approval and must be recorded in `Recon` and `Closure`.
- Web UI changes must add or update component/unit tests (`*.spec.ts` / `*.spec.tsx`) in the app package.
- E2E coverage is handled only in the dedicated Playwright suite (kept separate from app unit tests).
- Verify the asset(s) listed in the TASK exist or can be produced from the documented build commands.
- Promotion to `main` always uses the full release/promotion lane even if the implementation slice used a narrower lane.
- CI is **glue**, not substance: anything done in CI must be invocable via Gradle tasks
  (including web builds, publishing, signing, and ancillary scripts).
- Gradle configuration cache is **best-effort**: fix incompatibilities at the task level
  (e.g., `doNotTrackState`) and **never disable it globally** (`org.gradle.configuration-cache=false` is not allowed).

### 4) Close-out (after operator approval + CI verification)
- Update ROADMAP checkbox to `[x]`.
- Move TASK file from `.agents/tasks/open/<namespace>/` → `.agents/tasks/closed/`.
- Append a `Closure` note with timestamp, branch (if used), and a 1–3 bullet summary.
- If the EPIC has no remaining open tasks, move it to `.agents/epics/closed/` and set `status: closed`.
- **Close-out is allowed only after CI passes for the implementation commit.**
  - Implementation commit → verify CI green → commit close-out changes.

### Branching + integration (standard)
- Workers use epic-scoped branches: `epic/<slug>`.
- Push to origin and request integration via the Management Codex.
- Dual-rail policy:
  - `development` is the default integration rail.
  - `main` is the promoted stable rail.
- Management Codex integrates worker output into the active train rail after QA gates pass.
- Promotion to `main` is an explicit release/promotion act from the train rail, not the default worker merge target.
- If the train rail is stale or misaligned, fix that as explicit work instead of normalizing direct-to-`main` integration.

## Completion packets (from `codex-1`)

At the end of each milestone (or when requested), `codex-1` provides a concise completion packet:
- What changed (high-level) and which TASKs were closed.
- How to run/verify (exact commands + expected outputs).
- Known gaps or deferred work (with proposed follow-up TASK IDs).
- Any non-obvious operational notes (permissions, prerequisites, safety caveats).
