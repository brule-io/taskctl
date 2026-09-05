package io.brule.workflow.task

import io.brule.workflow.diagnostics.WorkflowOperationException
import io.brule.workflow.diagnostics.WorkflowValidationException
import io.brule.workflow.document.MarkdownEditor
import io.brule.workflow.io.AtomicPathMover
import io.brule.workflow.io.AtomicTextFileWriter
import io.brule.workflow.io.PathMover
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.io.TextFileWriter
import io.brule.workflow.io.withWorkflowLock
import io.brule.workflow.model.TaskRef
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

public data class CloseTaskRequest(
    public val ref: TaskRef,
    public val summary: String,
    public val evidence: List<String>,
    public val closedBy: String = "codex",
    public val followUps: List<TaskRef> = emptyList(),
    public val expectedFrontier: List<TaskRef>? = null,
    public val expectedSnapshot: String? = null,
)

public data class ClosedTask(
    public val ref: TaskRef,
    public val destination: Path,
    public val closedAt: Instant,
)

public class TaskClosureService(
    private val layout: RepositoryLayout,
    private val writer: TextFileWriter = AtomicTextFileWriter,
    private val mover: PathMover = AtomicPathMover,
    private val clock: Clock = Clock.systemUTC(),
) {
    public fun close(request: CloseTaskRequest): ClosedTask =
        withWorkflowLock(layout) {
            val target = LedgerClosureTarget.Task(request.ref)
            RecordSnapshot.assertMatches(layout, request.expectedSnapshot, target)
            val expectedFrontier = normalizeExpectedFrontier(request.expectedFrontier)
            val closure =
                ClosureRequest(request.summary, request.evidence, request.closedBy, request.followUps)
            val transaction = LedgerClosureTransactionManager(layout, writer, mover)
            val postCommit =
                ClosurePostCommitValidator { destination, ignoredJournal, persistedFrontier ->
                    validateCommittedTask(
                        request.ref,
                        destination,
                        ignoredJournal,
                        persistedFrontier,
                        closure,
                    )
                }
            val recoveredDestination = transaction.recover(target, expectedFrontier, postCommit)
            val snapshot = TaskLedger(layout).loadUnlocked()
            if (!snapshot.report.isValid) throw WorkflowValidationException(snapshot.report)
            if (recoveredDestination != null) {
                return@withWorkflowLock recoveredClosure(snapshot, request, recoveredDestination)
            }

            val task =
                snapshot.task(request.ref)
                    ?: throw WorkflowOperationException("unknown task ${request.ref}")
            if (task.state != LedgerState.OPEN) {
                throw WorkflowOperationException("task ${request.ref} is already closed")
            }
            closure.validate(snapshot)
            val unchecked = task.markdown.deliverables().filter { !it.completed }
            if (unchecked.isNotEmpty()) {
                throw WorkflowOperationException(
                    "task ${request.ref} has unchecked deliverables: ${unchecked.joinToString { it.description }}",
                )
            }
            val tasksByRef = snapshot.tasks.associateBy { it.ref }
            val openDependencies = task.depends.filter { tasksByRef[it]?.state != LedgerState.CLOSED }
            if (openDependencies.isNotEmpty()) {
                throw WorkflowOperationException(
                    "task ${request.ref} has unsatisfied dependencies: ${openDependencies.joinToString()}",
                )
            }
            val openRealizations =
                snapshot.tasks.filter { candidate ->
                    candidate.state == LedgerState.OPEN && candidate.realizes == task.ref
                }
            if (openRealizations.isNotEmpty()) {
                throw WorkflowOperationException(
                    "task ${request.ref} has open realizations: " +
                        openRealizations.joinToString { realization -> realization.ref.value },
                )
            }
            expectedFrontier?.let { expected ->
                val simulated =
                    TaskGraph.analyze(snapshot.openTasks.filterNot { it.ref == request.ref })
                assertFrontier(expected, simulated.frontier.map { it.ref }, "simulated post-closure")
            }

            val closedAt = clock.instant()
            val receipt = closure.render(closedAt)
            validateClosedReceipt(task.path, receipt)
            val updated = MarkdownEditor.replaceSection(task.frontMatter.normalizedText, "Closure", receipt)
            val destination = layout.closedTasksDirectory.resolve(task.path.fileName)
            val committedDestination =
                transaction.commit(target, task.path, destination, updated, expectedFrontier, postCommit)
            ClosedTask(request.ref, committedDestination, closedAt)
        }

    private fun validateCommittedTask(
        ref: TaskRef,
        destination: Path,
        ignoredJournal: Path,
        expectedFrontier: List<TaskRef>?,
        closure: ClosureRequest,
    ): ClosurePostCommitResult {
        val snapshot = TaskLedger(layout).loadUnlocked(ignoredJournal)
        if (!snapshot.report.isValid) throw WorkflowValidationException(snapshot.report)
        val task = snapshot.task(ref)
        if (task == null || task.state != LedgerState.CLOSED || task.path != destination) {
            throw WorkflowOperationException("committed closure does not identify a closed task $ref")
        }
        expectedFrontier?.let { expected ->
            assertFrontier(expected, snapshot.graph.frontier.map { it.ref }, "committed post-closure")
        }
        val receipt = closedReceipt(task.path, task.markdown)
        return if (closure.matches(receipt)) {
            ClosurePostCommitResult.Accepted
        } else {
            ClosurePostCommitResult.RequestMismatch(
                "recovered task closure receipt does not match the requested closure for $ref",
            )
        }
    }

    private fun recoveredClosure(
        snapshot: TaskLedgerSnapshot,
        request: CloseTaskRequest,
        destination: Path,
    ): ClosedTask {
        val task = snapshot.task(request.ref)
        if (task == null || task.state != LedgerState.CLOSED || task.path != destination) {
            throw WorkflowOperationException("recovered closure does not identify a closed task ${request.ref}")
        }
        val receipt = closedReceipt(task.path, task.markdown)
        val closure = ClosureRequest(request.summary, request.evidence, request.closedBy, request.followUps)
        if (!closure.matches(receipt)) {
            throw WorkflowOperationException("recovered closure receipt does not match the requested closure")
        }
        return ClosedTask(request.ref, destination, Instant.parse(receipt.closedAt))
    }

    private fun normalizeExpectedFrontier(expected: List<TaskRef>?): List<TaskRef>? {
        if (expected == null) return null
        val duplicates =
            expected
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .sorted()
        if (duplicates.isNotEmpty()) {
            throw WorkflowOperationException(
                "expected frontier contains duplicate refs: ${duplicates.joinToString { it.value }}",
            )
        }
        return expected.sorted()
    }

    private fun assertFrontier(
        expected: List<TaskRef>,
        actual: List<TaskRef>,
        phase: String,
    ) {
        val normalizedActual = actual.sorted()
        if (expected != normalizedActual) {
            throw WorkflowOperationException(
                "$phase frontier mismatch: expected ${renderFrontier(
                    expected,
                )}, actual ${renderFrontier(normalizedActual)}",
            )
        }
    }

    private fun renderFrontier(refs: List<TaskRef>): String =
        if (refs.isEmpty()) "<empty>" else refs.joinToString(prefix = "[", postfix = "]") { it.value }
}
