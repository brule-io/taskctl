package io.brule.tasking.conformance

import io.brule.workflow.io.*
import io.brule.workflow.model.TaskRef
import io.brule.workflow.task.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Test-only fault injector. Run with the shipped libraries and runtime; this
 * class is not included in distributions. Its input must be a disposable,
 * completed-checkbox TASK.test.001 -> TASK.test.002 specimen. */
fun main(args: Array<String>) {
    require(args.size == 1)
    val layout = RepositoryLayout.at(Path.of(args[0]))
    val ref = requireNotNull(TaskRef.parse("TASK.test.001"))
    val snapshot = TaskLedger(layout).inspect().second
    val request = CloseTaskRequest(ref, "Packaged recovery probe", listOf("fault injection and recovery passed"),
        "proof-agent", expectedFrontier = listOf(requireNotNull(TaskRef.parse("TASK.test.002"))), expectedSnapshot = snapshot)
    val clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC)
    class Interrupted : Error()
    val mover = PathMover { source, destination ->
        AtomicPathMover.move(source, destination)
        if (destination.parent == layout.closedTasksDirectory) throw Interrupted()
    }
    try { TaskClosureService(layout, mover = mover, clock = clock).close(request); error("injection did not interrupt") }
    catch (_: Interrupted) { /* Durable journal and original backup remain. */ }
    check(TaskLedger(layout).load().report.errors.any { it.code == "TASK_CLOSURE_PENDING" })
    val pending = layout.agentsDirectory.resolve(".taskctl-close-${ref.value}.txn")
    check(Files.exists(pending))
    val outcome = TaskClosureService(layout, clock = clock).close(request)
    check(outcome.closedAt == clock.instant())
    check(!Files.exists(pending))
    check(TaskLedger(layout).load().report.isValid)
    println("packaged-recovery: ok")
}
