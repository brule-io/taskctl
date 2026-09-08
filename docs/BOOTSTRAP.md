# Bootstrap composition contract

The 0.3 candidate adds history-backed native alpha2, core-draft-2 semantic
contracts, observed dependency currency, and explicit reconciliation. See
[SEMANTIC-0.3.md](SEMANTIC-0.3.md) for the current changes and compatibility rules;
older draft examples below retain their original versioned meaning.

The loose coupling boundary is the executable command, not a public Kotlin API:

```text
taskctl init --contract taskctl.init/alpha1 --repo DESTINATION --id REPOSITORY_ID
             --profile minimal/alpha1 --toolchain LOCK
             [--seed PLAN] [--plan] --format json
```

The exact release provides both the initializer and its templates. A generator
passes inputs and consumes the typed result; it never owns a copied `.agents`
template, wrapper source, or filesystem layout implementation. The result carries
`api: taskctl.cli/alpha1` and `result.contract: taskctl.init/alpha1`, repository ID,
an initialization plan digest, and the declared file list. Unknown contract IDs
fail explicitly. Tool upgrades may add a new composition contract while continuing
to support the old one, or announce a breaking change before it is retired.

`--plan` validates inputs and reports the deterministic proposed manifest without
creating a destination. Apply validates again and refuses collisions. An explicit
seed is admitted through the same core graph/index validation as `seed`; it never
infers a plan from source. With no seed, task and planning indexes remain empty.

A greenfield generator can initialize tasking first and then write its own project
source skeleton. The 0.3 `adopt` command supports existing source without tasking
through the same bounded initializer; see [semantic adoption](SEMANTIC-0.3.md).
Do not use filesystem copying to circumvent initialization's collision checks.

Example seed (each record has its own explicit protocol identity):

```json
{
  "contract": "taskctl.seed/alpha1",
  "tasks": [{
    "protocol": "tasking/core-draft-1",
    "id": "TASK.greeting",
    "title": "Implement the greeting contract",
    "state": "open",
    "intent": "Produce the greeting behavior specified by the project owner.",
    "requirements": ["Use the supplied project name."],
    "acceptance": ["The greeting test passes."]
  }],
  "roadmaps": [],
  "epics": []
}
```

Native command exit codes: `0` success (including an empty frontier), `2` invalid
input/contract or failed validation, `3` required provider unavailable, `4` stale
revision or active writer, `5` filesystem/I/O error. JSON errors have the same API
identity and an `error` object with numeric code and actionable message. Bootstrap
failures before the runtime is acquired use stderr and exit `2`.

Repository selection is explicit with `--repo`, otherwise the generated launcher
supplies its own repository root; the standalone distribution defaults to cwd.
Input file options resolve relative to the caller's cwd, not the selected repository.
The CLI has no hidden source discovery or upward traversal. Commands after pinned
acquisition do not contact GitHub or run a consumer build.
