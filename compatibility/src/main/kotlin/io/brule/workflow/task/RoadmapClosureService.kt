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
import io.brule.workflow.model.RoadmapRef
import io.brule.workflow.model.TaskRef
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

public data class CloseRoadmapRequest(
    public val ref: RoadmapRef,
    public val summary: String,
    public val evidence: List<String>,
    public val closedBy: String = "codex",
    public val followUps: List<TaskRef> = emptyList(),
    public val expectedSnapshot: String? = null,
)

public data class ClosedRoadmap(
    public val ref: RoadmapRef,
    public val destination: Path,
    public val closedAt: Instant,
)

public class RoadmapClosureService(
    private val layout: RepositoryLayout,
    private val writer: TextFileWriter = AtomicTextFileWriter,
    private val mover: PathMover = AtomicPathMover,
    private val clock: Clock = Clock.systemUTC(),
) {
    public fun close(request: CloseRoadmapRequest): ClosedRoadmap =
        withWorkflowLock(layout) {
            val target = LedgerClosureTarget.Roadmap(request.ref)
            RecordSnapshot.assertMatches(layout, request.expectedSnapshot, target)
            val closure =
                ClosureRequest(request.summary, request.evidence, request.closedBy, request.followUps)
            val transaction = LedgerClosureTransactionManager(layout, writer, mover)
            val postCommit =
                ClosurePostCommitValidator { destination, ignoredJournal, expectedFrontier ->
                    check(expectedFrontier == null) { "roadmap closure cannot carry a task frontier assertion" }
                    validateCommittedRoadmap(request.ref, destination, ignoredJournal, closure)
                }
            val recoveredDestination = transaction.recover(target, null, postCommit)
            val snapshot = TaskLedger(layout).loadUnlocked()
            if (!snapshot.report.isValid) throw WorkflowValidationException(snapshot.report)
            if (recoveredDestination != null) {
                return@withWorkflowLock recoveredClosure(snapshot, request, recoveredDestination)
            }

            val roadmap =
                snapshot.roadmap(request.ref)
                    ?: throw WorkflowOperationException("unknown roadmap ${request.ref}")
            if (roadmap.state != LedgerState.OPEN) {
                throw WorkflowOperationException("roadmap ${request.ref} is already closed")
            }
            closure.validate(snapshot)
            val unchecked = roadmap.markdown.checklist("Exit Criteria").filter { !it.completed }
            if (unchecked.isNotEmpty()) {
                throw WorkflowOperationException(
                    "roadmap ${request.ref} has unchecked exit criteria: ${unchecked.joinToString { it.description }}",
                )
            }
            val openMembers =
                snapshot.openTasks
                    .filter { task -> task.roadmap == request.ref }
                    .map { it.ref }
                    .sorted()
            if (openMembers.isNotEmpty()) {
                throw WorkflowOperationException(
                    "roadmap ${request.ref} has open member tasks: ${openMembers.joinToString()}",
                )
            }
            requireOpenReceipt(roadmap)

            val closedAt = clock.instant()
            val receipt = closure.render(closedAt)
            validateClosedReceipt(roadmap.path, receipt)
            val updated = MarkdownEditor.replaceSection(roadmap.frontMatter.normalizedText, "Closure", receipt)
            val destination = layout.closedRoadmapsDirectory.resolve(roadmap.path.fileName)
            val committedDestination =
                transaction.commit(target, roadmap.path, destination, updated, null, postCommit)
            ClosedRoadmap(request.ref, committedDestination, closedAt)
        }

    private fun requireOpenReceipt(roadmap: RoadmapDocument) {
        val closure =
            roadmap.markdown.section("Closure")
                ?: throw WorkflowOperationException("roadmap ${roadmap.ref} has no closure receipt")
        val receipt =
            ClosureReceiptParser.parse(roadmap.path, closure.content).receipt
                ?: throw WorkflowOperationException("roadmap ${roadmap.ref} has an invalid closure receipt")
        if (!receipt.isOpen) {
            throw WorkflowOperationException("roadmap ${roadmap.ref} closure receipt is not empty")
        }
    }

    private fun validateCommittedRoadmap(
        ref: RoadmapRef,
        destination: Path,
        ignoredJournal: Path,
        closure: ClosureRequest,
    ): ClosurePostCommitResult {
        val snapshot = TaskLedger(layout).loadUnlocked(ignoredJournal)
        if (!snapshot.report.isValid) throw WorkflowValidationException(snapshot.report)
        val roadmap = snapshot.roadmap(ref)
        if (roadmap == null || roadmap.state != LedgerState.CLOSED || roadmap.path != destination) {
            throw WorkflowOperationException("committed closure does not identify a closed roadmap $ref")
        }
        val receipt = closedReceipt(roadmap.path, roadmap.markdown)
        return if (closure.matches(receipt)) {
            ClosurePostCommitResult.Accepted
        } else {
            ClosurePostCommitResult.RequestMismatch(
                "recovered roadmap closure receipt does not match the requested closure for $ref",
            )
        }
    }

    private fun recoveredClosure(
        snapshot: TaskLedgerSnapshot,
        request: CloseRoadmapRequest,
        destination: Path,
    ): ClosedRoadmap {
        val roadmap = snapshot.roadmap(request.ref)
        if (roadmap == null || roadmap.state != LedgerState.CLOSED || roadmap.path != destination) {
            throw WorkflowOperationException("recovered closure does not identify a closed roadmap ${request.ref}")
        }
        val receipt = closedReceipt(roadmap.path, roadmap.markdown)
        val closure = ClosureRequest(request.summary, request.evidence, request.closedBy, request.followUps)
        if (!closure.matches(receipt)) {
            throw WorkflowOperationException("recovered closure receipt does not match the requested closure")
        }
        return ClosedRoadmap(request.ref, destination, Instant.parse(receipt.closedAt))
    }
}
