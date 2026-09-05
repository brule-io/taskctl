package io.brule.tasking.cli

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import java.nio.file.Files
import java.nio.file.Path

internal object NativeCommands {
    val version: String get() = ToolRuntime.version
    private class Arguments(args: List<String>) {
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
                doctor | context | snapshot | frontier [--roadmap ID] [--epic ID]
                show TASK | roadmap [ID] | epic [ID]
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
            "info" -> { options.allow(); ToolRuntime.info() }
            "init" -> {
                options.allow("--id", "--toolchain", "--profile", "--seed", "--plan", "--contract")
                require(options.options["--profile"] in setOf(null, "minimal/alpha1")) { "only minimal/alpha1 is bundled" }
                require(options.options["--contract"] in setOf(null, Bootstrap.CONTRACT)) { "unsupported init contract" }
                val distribution = ToolRuntime.distribution() ?: error("init requires the standalone distribution (bootstrap templates bundled)")
                val seed = options.options["--seed"]?.let { NativeCodec.decodeSeed(readObject(Path.of(it))) } ?: Transition.AddRecords()
                val plan = Bootstrap.plan(options.root(), options.need("--id"), version, distribution, Files.readString(Path.of(options.need("--toolchain"))), seed)
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
            "doctor", "context", "snapshot" -> args.allow()
            "frontier" -> args.allow("--roadmap", "--epic")
            "show" -> args.allow(positions = 1)
            "roadmap", "epic" -> args.allow(positions = args.positional.size.also { require(it <= 1) })
            "seed" -> args.allow("--file", "--expect-revision")
            "verify" -> args.allow("--receipt", positions = 1)
            "close" -> args.allow("--receipt", "--expect-revision", positions = 1)
            else -> error("unknown command: $command; run taskctl help")
        }
        if (command == "frontier") {
            val frontier = ledger.frontier(FrontierQuery(args.options["--roadmap"]?.let(RoadmapId::parseOrThrow), args.options["--epic"]?.let(EpicId::parseOrThrow)))
            return obj("revision" to StringValue(frontier.revision.value), "tasks" to strings(frontier.tasks.map { it.value }))
        }
        if (command == "seed") {
            val result = ledger.apply(Revision.parseOrThrow(args.need("--expect-revision")), NativeCodec.decodeSeed(readObject(Path.of(args.need("--file")))))
            return transition(result)
        }
        if (command == "verify" || command == "close") {
            val evidence = NativeCodec.decodeEvidence(readObject(Path.of(args.need("--receipt"))))
            require(evidence.receipt.taskId == TaskId.parseOrThrow(args.positional.single())) { "receipt task differs from requested task" }
            if (command == "close") return transition(ledger.apply(Revision.parseOrThrow(args.need("--expect-revision")), Transition.CloseTask(evidence)))
            val snapshot = ledger.snapshot()
            val errors = snapshot.dependencyProblems() + snapshot.universe.closureProblems(evidence.receipt.taskId, evidence.receipt)
            require(errors.isEmpty()) { errors.joinToString("\n") }
            return obj("revision" to StringValue(snapshot.revision.value), "valid" to BooleanValue(true),
                "classification" to StringValue("actor-assertion"), "contract" to StringValue(evidence.receipt.contractDigest.value))
        }
        val snapshot = ledger.snapshot()
        val universe = snapshot.universe
        return when (command) {
            "doctor", "context", "snapshot" -> obj(
                "repository_id" to StringValue(snapshot.repositoryId), "protocol" to StringValue("taskctl.native/alpha1"),
                "profile" to StringValue("minimal/alpha1"), "revision" to StringValue(snapshot.revision.value),
                "tasks" to integer(universe.tasks.size), "roadmaps" to integer(universe.roadmaps.size), "epics" to integer(universe.epics.size),
                "required_capabilities_unavailable" to strings((universe.tasks.flatMap { it.requiredExtensions } + universe.roadmaps.flatMap { it.requiredExtensions } + universe.epics.flatMap { it.requiredExtensions }).distinct().sorted()),
                "instructions" to StringValue("Read AGENTS.md. Use frontier, show, roadmap and epic; mutations require the inspected revision."),
            )
            "show" -> {
                val task = universe.tasks.singleOrNull { it.id == TaskId.parseOrThrow(args.positional.single()) } ?: error("unknown task: ${args.positional.single()}")
                ObjectValue(NativeCodec.task(task).fields + mapOf("contract_digest" to StringValue(DraftLifecycle.contract(task).value),
                    "revision" to StringValue(snapshot.revision.value), "roadmaps" to strings(universe.roadmapsFor(task.id).map { it.value }),
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
            else -> { println("$command: OK"); result.fields.forEach { (key, value) -> println("$key: " + if (value is StringValue) value.value else Json.encode(value)) } }
        }
    }
}
