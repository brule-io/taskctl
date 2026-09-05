package io.brule.tasking.cli

import io.brule.tasking.core.*
import io.brule.tasking.compatibility.FantastiktAdapter
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.task.TaskLedger
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        if (args.contentEquals(arrayOf("info")) || args.contentEquals(arrayOf("info", "--format", "json"))) {
            val version = object {}.javaClass.getResourceAsStream("/VERSION")!!.bufferedReader().use { it.readText().trim() }
            println(Json.encode(obj("tool" to StringValue("taskctl"), "version" to StringValue(version),
                "native_v1" to StringValue("not-frozen"))))
            return
        }
        if (args.contentEquals(arrayOf("info")) || args.contentEquals(arrayOf("--adapter", FantastiktAdapter.ID, "info"))) {
            println(Json.encode(stringMap(mapOf("tool" to "taskctl", "version" to FantastiktAdapter.VERSION,
                "adapter" to FantastiktAdapter.ID, "donor_revision" to FantastiktAdapter.SOURCE_REVISION,
                "native_v1" to "not-frozen", "java" to System.getProperty("java.version")))))
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
        System.err.println(failure.message ?: failure.javaClass.simpleName)
        exitProcess(2)
    }
}
