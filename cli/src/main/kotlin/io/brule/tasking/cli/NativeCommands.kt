package io.brule.tasking.cli

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import java.nio.file.Files
import java.nio.file.Path

internal object NativeCommands {
    val version: String get() = ToolRuntime.version
    internal class Arguments(args: List<String>) {
        val options = linkedMapOf<String, String>()
        val positional = mutableListOf<String>()
        init {
            var i = 0
            while (i < args.size) {
                val value = args[i++]
                if (value.startsWith("--")) {
                    require(value !in options) { "duplicate option: $value" }
                    options[value] = if (value == "--plan") "true" else {
                        require(i < args.size && !args[i].startsWith("--")) { "missing value for $value" }; args[i++]
                    }
                } else positional += value
            }
        }
        fun allow(vararg keys: String, positions: Int = 0) {
            require((options.keys - keys.toSet() - setOf("--repo", "--format")).isEmpty()) { "unknown option: ${options.keys - keys.toSet() - setOf("--repo", "--format")}" }
            require(positional.size == positions) { "expected $positions positional arguments; run taskctl help" }
            require(options["--format"] in setOf(null, "json", "text")) { "format must be json or text" }
        }
        fun need(key: String): String = options[key] ?: error("required option: $key")
        fun root(): Path = Path.of(options["--repo"] ?: System.getProperty("taskctl.repository") ?: System.getenv("TASKCTL_REPOSITORY") ?: ".").toAbsolutePath().normalize()
    }

    fun run(args: List<String>): Int {
        val command = args.firstOrNull() ?: "help"
        val options = Arguments(args.drop(1))
        if (command in setOf("help", "--help")) {
            println("""
                taskctl $version — standalone native alpha (v1 not frozen)
                init --repo PATH --id ID --toolchain LOCK [--seed FILE] [--plan]
                adopt --repo PATH --id ID --toolchain LOCK [--seed FILE] [--plan]
                import inspect|plan --source PATH --source-repository ID --source-revision SHA --adapter ID
                import apply --source PATH --file PLAN --review REVIEW --repo TARGET --id ID --toolchain LOCK [--plan]
                doctor (diagnostics) | context (bounded briefing) | snapshot (complete typed state)
                frontier [--roadmap ID] [--epic ID]
                show TASK | roadmap [ID] | epic [ID]
                status | affected [TASK] | history TASK
                revise TASK --file RECORD --expect-revision REVISION
                reconcile TASK --plan | --file REVIEW --expect-revision REVISION
                track --expect-revision REVISION (explicit adoption of legacy native history)
                seed --file FILE --expect-revision REVISION
                verify TASK --receipt FILE
                close TASK --receipt FILE --expect-revision REVISION
                recover | info | version [--format json]
                --version | -V (no repository or network access)
                Repository commands accept --repo PATH and --format json|text.
                init composition contract: taskctl.init/alpha1. No implicit Git/network operations.
            """.trimIndent())
            return 0
        }
        val result = when (command) {
            "import" -> ImportCommands.run(options)
            "info" -> { options.allow(); ToolRuntime.info() }
            "init", "adopt" -> {
                options.allow("--id", "--toolchain", "--profile", "--seed", "--plan", "--contract")
                require(options.options["--profile"] in setOf(null, "minimal/alpha1")) { "only minimal/alpha1 is bundled" }
                require(options.options["--contract"] in setOf(null, Bootstrap.CONTRACT)) { "unsupported init contract" }
                val distribution = ToolRuntime.distribution() ?: error("init requires the standalone distribution (bootstrap templates bundled)")
                val seed = options.options["--seed"]?.let { NativeCodec.decodeSeed(readObject(Path.of(it))) } ?: Transition.AddRecords()
                val plan = Bootstrap.plan(options.root(), options.need("--id"), version, distribution, Files.readString(Path.of(options.need("--toolchain"))), seed, adopt = command == "adopt")
                if ("--plan" in options.options) plan.result() else Bootstrap.apply(plan)
            }
            "recover" -> {
                options.allow()
                Bootstrap.recover(options.root())
                obj("revision" to StringValue(FileTaskLedger(options.root()).recover().value))
            }
            else -> ledgerCommand(command, options, FileTaskLedger(options.root()))
        }
        val envelope = obj("api" to StringValue("taskctl.cli/alpha1"), "command" to StringValue(command),
            "repository" to StringValue(options.root().toString()), "result" to result)
        if (options.options["--format"] == "json") println(Json.encode(envelope))
        else render(command, result)
        return 0
    }

    private fun ledgerCommand(command: String, args: Arguments, ledger: TaskLedger): ObjectValue {
        when (command) {
            "doctor", "context", "snapshot", "status" -> args.allow()
            "affected" -> args.allow(positions = args.positional.size.also { require(it <= 1) })
            "history" -> args.allow(positions = 1)
            "frontier" -> args.allow("--roadmap", "--epic")
            "show" -> args.allow(positions = 1)
            "roadmap", "epic" -> args.allow(positions = args.positional.size.also { require(it <= 1) })
            "seed" -> args.allow("--file", "--expect-revision", "--plan")
            "verify" -> args.allow("--receipt", positions = 1)
            "close" -> args.allow("--receipt", "--expect-revision", "--plan", positions = 1)
            "revise" -> args.allow("--file", "--expect-revision", "--plan", positions = 1)
            "reconcile" -> args.allow("--file", "--expect-revision", "--plan", positions = 1)
            "track" -> args.allow("--expect-revision", "--plan")
            else -> error("unknown command: $command; run taskctl help")
        }
        fun mutate(value: Transition): ObjectValue {
            val expected = Revision.parseOrThrow(args.need("--expect-revision"))
            return if ("--plan" in args.options) {
                require(ledger is FileTaskLedger) { "this ledger does not expose file mutation plans" }
                ledger.plan(expected, value)
            } else transition(ledger.apply(expected, value))
        }
        if (command == "frontier") {
            val frontier = ledger.frontier(FrontierQuery(args.options["--roadmap"]?.let(RoadmapId::parseOrThrow), args.options["--epic"]?.let(EpicId::parseOrThrow)))
            return obj("revision" to StringValue(frontier.revision.value), "tasks" to strings(frontier.tasks.map { it.value }))
        }
        if (command == "seed") {
            return mutate(NativeCodec.decodeSeed(readObject(Path.of(args.need("--file")))))
        }
        if (command == "track") return mutate(Transition.TrackHistory)
        if (command == "revise") {
            val record = DraftDocument.parse(Files.readString(Path.of(args.need("--file")))).record
            require(record.id == TaskId.parseOrThrow(args.positional.single())) { "revision task differs from requested task" }
            return mutate(Transition.ReviseTask(record))
        }
        if (command == "reconcile" && "--file" in args.options) {
            val review = HistoryCodec.decodeReview(readObject(Path.of(args.need("--file"))))
            require(review.task == TaskId.parseOrThrow(args.positional.single())) { "review task differs from requested task" }
            return mutate(Transition.ReconcileTask(review))
        }
        if (command == "verify" || command == "close") {
            val evidence = NativeCodec.decodeEvidence(readObject(Path.of(args.need("--receipt"))))
            require(evidence.receipt.taskId == TaskId.parseOrThrow(args.positional.single())) { "receipt task differs from requested task" }
            if (command == "close") return mutate(Transition.CloseTask(evidence))
            val snapshot = ledger.snapshot()
            val errors = snapshot.closureProblems(evidence)
            require(errors.isEmpty()) { errors.joinToString("\n") }
            return obj("revision" to StringValue(snapshot.revision.value), "valid" to BooleanValue(true),
                "classification" to StringValue("actor-assertion"), "contract" to StringValue(evidence.receipt.contractDigest.value))
        }
        val snapshot = ledger.snapshot()
        val universe = snapshot.universe
        val currency by lazy { snapshot.currency() }
        return when (command) {
            "doctor" -> LedgerReadModels.doctor(snapshot)
            "context" -> LedgerReadModels.context(snapshot)
            "snapshot" -> LedgerReadModels.snapshot(snapshot)
            "status", "affected" -> {
                val selected = args.positional.singleOrNull()?.let(TaskId::parseOrThrow)
                require(selected == null || universe.tasks.any { it.id == selected }) { "unknown task: $selected" }
                val records = currency.values.sortedBy { it.task }.filter { item ->
                    command == "status" || (item.state != Currency.CURRENT && (selected == null || item.task == selected || item.causes.any { selected in it.path }))
                }
                obj("revision" to StringValue(snapshot.revision.value), "tasks" to ArrayValue(records.map { item ->
                    ObjectValue(HistoryCodec.currency(item).fields + ("lifecycle" to StringValue(universe.tasks.single { it.id == item.task }.state)))
                }))
            }
            "history" -> {
                val id = TaskId.parseOrThrow(args.positional.single())
                val history = requireNotNull(snapshot.history) { "history untracked; inspect and explicitly track it" }
                var head: TaskRevisionId? = history.heads[id] ?: error("unknown task: $id")
                val revisions = mutableListOf<Value>()
                while (head != null) {
                    val value = history.revisions.getValue(head)
                    revisions += obj("revision" to StringValue(head.value), "value" to HistoryCodec.revision(value))
                    head = value.parent
                }
                obj("revision" to StringValue(snapshot.revision.value), "origin_ledger_revision" to StringValue(history.origin.value), "revisions" to ArrayValue(revisions),
                    "receipts" to ArrayValue(snapshot.receipts.filter { it.receipt.taskId == id }.map(NativeCodec::evidence)),
                    "imports" to ArrayValue(snapshot.imports.filter { admission -> admission.manifest.universe.tasks.any { it.id == id } }.map { admission ->
                        val source = admission.manifest.sources.single { it.id == id }
                        obj("manifest_id" to StringValue(admission.manifest.id.value), "source_repository" to StringValue(admission.manifest.repository),
                            "source_revision" to StringValue(admission.manifest.revision), "adapter" to StringValue(admission.manifest.adapter),
                            "adapter_version" to StringValue(admission.manifest.adapterVersion), "source_path" to StringValue(source.path),
                            "source_sha256" to StringValue(source.sha256), "source_contract" to optionalString(source.contract?.value),
                            "source_native_protocol" to NullValue, "classification" to StringValue("historical-narrative-unverified"),
                            "source_text" to StringValue(admission.manifest.files.getValue(source.path)), "review" to ImportCodec.review(admission.review))
                    }))
            }
            "reconcile" -> {
                require("--plan" in args.options) { "reconcile requires --plan or --file REVIEW; no review was recorded" }
                require(args.options.keys.none { it in setOf("--file", "--expect-revision") }) { "reconcile --plan is read-only; omit --file and --expect-revision" }
                val id = TaskId.parseOrThrow(args.positional.single())
                val history = requireNotNull(snapshot.history) { "track history before reconciliation" }
                val task = universe.tasks.singleOrNull { it.id == id } ?: error("unknown task: $id")
                val observations = CurrencyEvaluation.observations(snapshot)
                obj("revision" to StringValue(snapshot.revision.value), "reviewed_head" to StringValue(history.heads.getValue(id).value),
                    "task" to StringValue(id.value), "currency" to HistoryCodec.currency(currency.getValue(id)),
                    "observations" to ArrayValue(task.requires.sorted().map { HistoryCodec.dependency(observations.getValue(it)) }),
                    "required_evidence" to strings(task.verification),
                    "outcomes" to strings(ReviewOutcome.entries.map { it.name.lowercase() }),
                    "instructions" to StringValue("Submit taskctl.reconciliation/1 actor-assertion with these exact inputs, outcome, actor, recorded_at, rationale, evidence and successor (null except successor outcome). No review has been recorded."))
            }
            "show" -> {
                val task = universe.tasks.singleOrNull { it.id == TaskId.parseOrThrow(args.positional.single()) } ?: error("unknown task: ${args.positional.single()}")
                ObjectValue(NativeCodec.task(task).fields + mapOf("contract_digest" to StringValue(DraftLifecycle.contract(task).value),
                    "revision" to StringValue(snapshot.revision.value), "head" to optionalString(snapshot.history?.heads?.get(task.id)?.value),
                    "currency" to HistoryCodec.currency(currency.getValue(task.id)), "roadmaps" to strings(universe.roadmapsFor(task.id).map { it.value }),
                    "epics" to strings(universe.epicsFor(task.id).map { it.value })))
            }
            "roadmap", "epic" -> {
                val records: List<PlanningRecord> = if (command == "roadmap") universe.roadmaps else universe.epics
                val encoded = records.map { PlanningRecordCodec.encode(it) }.sortedBy { it.requiredString("id") }
                val selected = args.positional.singleOrNull()?.let { id -> listOf(encoded.singleOrNull { it.requiredString("id") == id } ?: error("unknown $command: $id")) } ?: encoded
                obj("revision" to StringValue(snapshot.revision.value), "records" to ArrayValue(selected))
            }
            else -> error("unsupported command")
        }
    }
    private fun transition(result: TransitionResult) = obj("revision" to StringValue(result.revision.value), "changed" to strings(result.changed.map { it.value }))
    private fun readObject(path: Path) = YamlValues.parse(Files.readString(path)).value as? ObjectValue ?: error("input must be an object")
    private fun render(command: String, result: ObjectValue) {
        when (command) {
            "frontier" -> {
                val tasks = result.requiredArray("tasks")
                println(if (tasks.isEmpty()) "No ready tasks." else tasks.joinToString("\n") { (it as StringValue).value })
                println("Revision: ${result.requiredString("revision")}")
            }
            "info" -> { println("taskctl $version (${ToolRuntime.implementation}; protocol v1 not frozen)"); result.fields.forEach { (key, value) -> println("$key: " + Json.encode(value)) } }
            "doctor" -> {
                println("doctor: ${result.requiredString("health")}")
                result.fields.filterKeys { it != "health" }.forEach { (key, value) -> println("$key: " + Json.encode(value)) }
            }
            "status", "affected" -> {
                result.requiredArray("tasks").forEach { value ->
                    val task = value as ObjectValue
                    println("${task.requiredString("task")}: ${task.requiredString("lifecycle")} / ${task.requiredString("currency")}")
                    task.requiredArray("causes").forEach { println("  " + Json.encode(it)) }
                }
                println("Revision: ${result.requiredString("revision")}")
            }
            else -> { println("$command: OK"); result.fields.forEach { (key, value) -> println("$key: " + if (value is StringValue) value.value else Json.encode(value)) } }
        }
    }
}
