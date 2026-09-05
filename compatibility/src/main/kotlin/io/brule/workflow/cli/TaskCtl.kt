@file:JvmName("TaskCtlKt")

package io.brule.workflow.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.model.RoadmapRef
import io.brule.workflow.model.TaskRef
import io.brule.workflow.task.CloseRoadmapRequest
import io.brule.workflow.task.CloseTaskRequest
import io.brule.workflow.task.LedgerState
import io.brule.workflow.task.RoadmapClosureService
import io.brule.workflow.task.TaskClosureService
import io.brule.workflow.task.TaskDocument
import io.brule.workflow.task.TaskLedger
import io.brule.workflow.task.TaskLedgerSnapshot

public class TaskCtl : CliktCommand(name = "taskctl") {
    override val printHelpOnEmptyArgs: Boolean = true

    override fun run(): Unit = Unit
}

private class TaskDoctor : RepositoryCommand("doctor", "Validate the complete task, roadmap, and epic ledger.") {
    override fun run(): Unit =
        inRepository { layout ->
            val snapshot = TaskLedger(layout).load()
            echo("tasks: ${snapshot.openTasks.size} open, ${snapshot.closedTasks.size} closed")
            echo(
                "roadmaps: ${snapshot.roadmaps.count {
                    it.state == LedgerState.OPEN
                }} open, ${snapshot.roadmaps.count { it.state == LedgerState.CLOSED }} closed",
            )
            echo(
                "epics: ${snapshot.epics.count {
                    it.state == LedgerState.OPEN
                }} open, ${snapshot.epics.count { it.state == LedgerState.CLOSED }} closed",
            )
            snapshot.report.diagnostics.sorted().forEach { diagnostic ->
                echo(
                    diagnostic.render(layout.root),
                    err =
                        diagnostic in snapshot.report.errors,
                )
            }
            if (!snapshot.report.isValid) {
                throw PrintMessage("doctor: ${snapshot.report.errors.size} error(s)", statusCode = 1, printError = true)
            }
            echo("doctor: ok")
        }
}

private class TaskList : RepositoryCommand("list", "List tasks deterministically.") {
    private val state: String by option("--state", metavar = "STATE", help = "open, closed, or all").default("open")
    private val roadmap: String? by option("--roadmap", metavar = "REF")

    override fun run(): Unit =
        inRepository { layout ->
            val snapshot = validSnapshot(layout)
            val tasks =
                snapshot.tasks
                    .filter { task ->
                        val stateMatches =
                            when (state) {
                                "open" -> task.state == LedgerState.OPEN
                                "closed" -> task.state == LedgerState.CLOSED
                                "all" -> true
                                else -> throw IllegalArgumentException("--state must be open, closed, or all")
                            }
                        stateMatches && (roadmap == null || task.roadmap.value == roadmap)
                    }.sortedBy { it.ref }
            tasks.forEach { task ->
                echo(
                    "${task.ref.value}\t${task.state.name.lowercase()}\t${task.roadmap.value}\t${layout.display(
                        task.path,
                    )}",
                )
            }
        }
}

private class TaskShow : RepositoryCommand("show", "Print a task document and its derived blocking edges.") {
    private val rawRef: String by argument("REF")

    override fun run(): Unit =
        inRepository { layout ->
            val snapshot = validSnapshot(layout)
            val ref = taskRef(rawRef)
            val task = snapshot.task(ref) ?: throw IllegalArgumentException("unknown task $ref")
            echo(task.frontMatter.normalizedText.trimEnd())
            echo("")
            echo("Derived blocks:")
            val blocks = snapshot.derivedBlocks(ref)
            if (blocks.isEmpty()) echo("  <none>") else blocks.forEach { echo("  ${it.value}") }
        }
}

private class TaskFrontier : RepositoryCommand("frontier", "Show the open Kahn frontier grouped by roadmap.") {
    private val roadmap: String? by option("--roadmap", metavar = "REF")

    override fun run(): Unit =
        inRepository { layout ->
            val snapshot = validSnapshot(layout)
            val tasks = snapshot.graph.frontier.filter { roadmap == null || it.roadmap.value == roadmap }
            renderGrouped(snapshot, tasks)
        }
}

private class TaskPlan : RepositoryCommand("plan", "Show every Kahn layer grouped by roadmap.") {
    override fun run(): Unit =
        inRepository { layout ->
            val snapshot = validSnapshot(layout)
            if (snapshot.graph.layers.isEmpty()) {
                echo("<empty>")
            } else {
                snapshot.graph.layers.forEachIndexed { index, tasks ->
                    echo("layer $index")
                    renderGrouped(snapshot, tasks, indent = "  ")
                }
            }
        }
}

private class TaskClose : RepositoryCommand("close", "Validate, receipt, and move one completed task.") {
    private val expectedSnapshot: String? by option("--expect-snapshot", metavar = "DIGEST", help = "Require the exact authored-record snapshot returned by snapshot.")
    private val rawRef: String by argument("REF")
    private val summary: String by option("--summary", metavar = "TEXT").required()
    private val evidence: List<String> by option("--evidence", metavar = "EVIDENCE").multiple()
    private val closedBy: String by option("--closed-by", metavar = "ACTOR").default("codex")
    private val followUps: List<String> by option("--follow-up", metavar = "TASK_REF").multiple()
    private val expectedFrontier: List<String> by
        option(
            "--expect-frontier",
            metavar = "TASK_REF",
            help = "Require the exact Kahn frontier after this closure; repeat for each expected task.",
        ).multiple()
    private val expectEmptyFrontier: Boolean by
        option(
            "--expect-empty-frontier",
            help = "Require the Kahn frontier to be empty after this closure.",
        ).flag()

    override fun run(): Unit =
        inRepository { layout ->
            val request =
                CloseTaskRequest(
                    ref = taskRef(rawRef),
                    summary = summary,
                    evidence = evidence,
                    closedBy = closedBy,
                    followUps = followUps.map(::taskRef),
                    expectedFrontier = taskFrontierExpectation(expectedFrontier, expectEmptyFrontier),
                    expectedSnapshot = expectedSnapshot,
                )
            val closed = TaskClosureService(layout).close(request)
            echo("closed ${closed.ref.value}\t${layout.display(closed.destination)}\t${closed.closedAt}")
        }
}

private class RoadmapClose :
    RepositoryCommand("close-roadmap", "Validate, receipt, and move one completed roadmap.") {
    private val expectedSnapshot: String? by option("--expect-snapshot", metavar = "DIGEST")
    private val rawRef: String by argument("REF")
    private val summary: String by option("--summary", metavar = "TEXT").required()
    private val evidence: List<String> by option("--evidence", metavar = "EVIDENCE").multiple()
    private val closedBy: String by option("--closed-by", metavar = "ACTOR").default("codex")
    private val followUps: List<String> by option("--follow-up", metavar = "TASK_REF").multiple()

    override fun run(): Unit =
        inRepository { layout ->
            val request =
                CloseRoadmapRequest(
                    ref = roadmapRef(rawRef),
                    expectedSnapshot = expectedSnapshot,
                    summary = summary,
                    evidence = evidence,
                    closedBy = closedBy,
                    followUps = followUps.map(::taskRef),
                )
            val closed = RoadmapClosureService(layout).close(request)
            echo("closed ${closed.ref.value}\t${layout.display(closed.destination)}\t${closed.closedAt}")
        }
}

private fun RepositoryCommand.validSnapshot(layout: RepositoryLayout): TaskLedgerSnapshot {
    val snapshot = TaskLedger(layout).load()
    if (!snapshot.report.isValid) {
        throw io.brule.workflow.diagnostics
            .WorkflowValidationException(snapshot.report)
    }
    return snapshot
}

private fun CliktCommand.renderGrouped(
    snapshot: TaskLedgerSnapshot,
    tasks: List<TaskDocument>,
    indent: String = "",
) {
    if (tasks.isEmpty()) {
        echo("$indent<empty>")
        return
    }
    val roadmapOrder = snapshot.roadmaps.sortedWith(compareBy({ it.ordinal }, { it.ref.value }))
    val grouped = tasks.groupBy { it.roadmap }
    roadmapOrder.filter { it.ref in grouped }.forEach { roadmap ->
        echo("$indent${roadmap.ref.value}\t${roadmap.title}")
        grouped.getValue(roadmap.ref).sortedBy { it.ref }.forEach { task ->
            echo("$indent  ${task.ref.value}\timpact=${task.impact.name}\teffort=${task.effort.name}")
        }
    }
}

private fun taskRef(value: String): TaskRef =
    TaskRef.parse(value) ?: throw IllegalArgumentException("task ref must match ${TaskRef.EXPECTED_FORMS}: $value")

private fun roadmapRef(value: String): RoadmapRef =
    RoadmapRef.parse(value)
        ?: throw IllegalArgumentException("roadmap ref must match ROADMAP.<namespace>.<NNN>: $value")

internal fun taskFrontierExpectation(
    rawRefs: List<String>,
    expectEmpty: Boolean,
): List<TaskRef>? {
    if (expectEmpty && rawRefs.isNotEmpty()) {
        throw IllegalArgumentException("--expect-frontier and --expect-empty-frontier are mutually exclusive")
    }
    return when {
        expectEmpty -> emptyList()
        rawRefs.isNotEmpty() -> rawRefs.map(::taskRef)
        else -> null
    }
}

public fun main(args: Array<String>): Unit =
    TaskCtl()
        .subcommands(TaskDoctor(), TaskList(), TaskShow(), TaskFrontier(), TaskPlan(), TaskClose(), RoadmapClose())
        .main(args)
