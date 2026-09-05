# MEMO: Multi-Roadmap Contract and Priority Rules

Date: 2026-03-18
Owner: governance-01
Status: proposed for adoption

## Purpose

Allow multiple active roadmaps without turning execution order into folklore.

The old rule, "exactly one active roadmap," was simple but too rigid for parallel work across independent surfaces.
The replacement rule is stricter where it matters: one primary roadmap, optional secondary roadmaps, explicit ownership, and explicit conflict handling.

## Contract

### 1. Active roadmap cap

- Default cap: `3` active roadmap files in `.agents/roadmaps/open/`
- Recommended posture:
  - `1` primary roadmap
  - `0-2` secondary roadmaps
- Exceeding the cap is an explicit management incident, not an informal convenience.

### 2. Primary roadmap

- Exactly one roadmap is designated `primary`.
- The primary roadmap defines:
  - default execution order,
  - default next-task selection,
  - default reporting posture in status surfaces.
- If an operator says "pick the next roadmap item" without qualification, use the primary roadmap.

### 3. Secondary roadmaps

- Secondary roadmaps are allowed only when all are true:
  - the workstreams are materially independent,
  - merge order is still tractable,
  - ownership boundaries are explicit,
  - Management Codex has assigned them deliberately.
- Secondary roadmaps must declare:
  - their priority tier,
  - expected file/submodule ownership,
  - whether they may run concurrently with the primary roadmap.

### 4. Priority tiers

- `P0`: train health, CI recovery, release blockers, branch alignment
- `P1`: active delivery roadmap for the current train
- `P2`: parallelizable supporting work with bounded overlap risk
- `P3`: backlog shaping, R&D, or low-urgency process work

Priority rules:

- `P0` preempts all other work.
- `P1` is the default primary roadmap tier.
- `P2` may run in parallel with `P1` only when file ownership is disjoint or merge order is explicitly known.
- `P3` must yield to any merge queue pressure or CI incident.

### 5. Lease and ownership rules

- One worker owns one task lease at a time.
- One task belongs to one roadmap.
- One worker branch should carry one task slice unless Management Codex explicitly batches adjacent process/docs work.
- If two roadmaps want the same files, they are not independent. One of them must wait, split, or be re-tasked.

### 6. Overlap and conflict rules

Treat overlap in this order:

1. Exact file overlap
   - serialize work
   - do not run in parallel
2. Shared submodule ownership
   - use separate `.workers/<name>` clones
   - require explicit merge order
3. Shared root policy/docs surfaces
   - parallel work is allowed only when sections are cleanly partitioned
   - otherwise serialize
4. Purely disjoint surfaces
   - parallel execution is allowed

### 7. Escalation policy for blocked lanes

A lane is `blocked` when any of the following is true:

- upstream task dependency is incomplete,
- file/submodule conflict is discovered after dispatch,
- MR is superseded by a higher-priority lane,
- CI is red for reasons outside the task slice,
- branch/train alignment is in doubt.

Blocked-lane actions:

1. mark the lane blocked in worker report and status surfaces
2. stop adding scope
3. record the blocker and affected files
4. hand control back to Management Codex for one of:
   - requeue,
   - split task,
   - supersede,
   - merge after prerequisite,
   - close as deferred

### 8. Merge policy

- Merge queue order beats worker completion order.
- A faster MR does not get to jump the queue if another lane owns a prerequisite surface.
- Secondary-roadmap MRs should be rebased or held if the primary roadmap changes shared policy files first.

## Example: Two-roadmap execution matrix

| Roadmap | Tier | Primary | Surface | Allowed to run with other roadmap? | Notes |
|---|---|---:|---|---|---|
| `ROADMAP.platform.alpha.0.6.3.wishlist-polish-and-major-change-tracks.md` | P1 | yes | workspace/web/process | yes, with bounded lanes | default execution source |
| `ROADMAP.platform.alpha.0.6.x.ci-recovery-hotfix.md` | P0 | no | CI/build/release | yes, but preemptive | can suspend P1 lanes touching CI/build |

Interpretation:

- If the P0 roadmap touches `.gitlab-ci.yml` or release logic, any P1 lane on CI/build surfaces pauses.
- A P1 web-only lane may continue if it does not depend on the blocked CI/build slice beyond ordinary merge gating.

## Recommended durable metadata

When multiple active roadmaps exist, each roadmap should carry:

- `Priority: P0|P1|P2|P3`
- `Role: primary|secondary`
- `Concurrency: serialized|bounded-parallel|parallel`

This is intentionally small. The goal is legibility, not a planning bureaucracy.

## Adopted recommendation

- Adopt the "one primary, optional secondary roadmaps" model.
- Keep the active-roadmap cap at `3`.
- Treat roadmap concurrency as a management function, not an implicit worker privilege.
