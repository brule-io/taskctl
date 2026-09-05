package io.brule.tasking.cli

import io.brule.tasking.core.*
import io.brule.tasking.compatibility.FantastiktAdapter
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.task.TaskLedger
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // A process protocol has one encoding even when Windows stdout is redirected.
    System.setOut(java.io.PrintStream(System.out, true, Charsets.UTF_8))
    System.setErr(java.io.PrintStream(System.err, true, Charsets.UTF_8))
    try {
        if (ToolRuntime.versionCommand(args.toList())) return
        if (args.firstOrNull() != "--adapter") {
            NativeCommands.run(args.toList())
            return
        }
        if (args.contentEquals(arrayOf("info")) || args.contentEquals(arrayOf("--adapter", FantastiktAdapter.ID, "info"))) {
            println(Json.encode(stringMap(mapOf("tool" to "taskctl", "version" to NativeCommands.version, "adapter_version" to FantastiktAdapter.VERSION,
                "adapter" to FantastiktAdapter.ID, "donor_revision" to FantastiktAdapter.SOURCE_REVISION,
                "native_v1" to "not-frozen", "implementation" to ToolRuntime.implementation, "java" to System.getProperty("java.version")))))
            return
        }
        if (args.isEmpty() || args.contentEquals(arrayOf("--help"))) {
            println("taskctl info | --adapter ${FantastiktAdapter.ID} <doctor|list|show|frontier|plan|close|close-roadmap|snapshot> [options]")
            println("The consumer wrapper supplies the pinned historical adapter. No native migration is enabled.")
            return
        }
        require(args.size >= 3 && args[0] == "--adapter" && args[1] == FantastiktAdapter.ID) {
            "select the historical adapter explicitly with --adapter ${FantastiktAdapter.ID}; schema labels alone do not select dialects"
        }
        val command = args.drop(2).toTypedArray()
        if (command[0] == "snapshot") {
            require(command.size == 1 || (command.size == 3 && command[1] == "--repo")) { "snapshot [--repo PATH]" }
            val layout = if (command.size == 3) RepositoryLayout.at(Path.of(command[2])) else RepositoryLayout.discover()
            val (ledger, snapshotId) = TaskLedger(layout).inspect()
            require(ledger.report.isValid) { ledger.report.errors.joinToString("\n") { it.render(layout.root) } }
            println(Json.encode(ObjectValue(FantastiktAdapter.snapshot(layout, ledger).fields + ("snapshot_id" to StringValue(snapshotId)))))
        } else {
            io.brule.workflow.cli.main(command)
        }
    } catch (failure: Exception) {
        val code = when (failure) {
            is RevisionConflict -> 4
            is java.io.IOException -> 5
            else -> if (failure.message?.contains("required") == true && failure.message?.contains("provider") == true) 3 else 2
        }
        val message = failure.message ?: failure.javaClass.simpleName
        if (args.toList().windowed(2).any { it == listOf("--format", "json") }) {
            println(Json.encode(obj("api" to StringValue("taskctl.cli/alpha1"), "error" to obj("code" to integer(code), "message" to StringValue(message)))))
        } else System.err.println(message)
        exitProcess(code)
    }
}
