package io.brule.workflow.task

import io.brule.workflow.diagnostics.ValidationReport
import io.brule.workflow.diagnostics.WorkflowOperationException
import io.brule.workflow.diagnostics.WorkflowValidationException
import io.brule.workflow.document.MarkdownDocument
import io.brule.workflow.model.TaskRef
import java.nio.file.Path
import java.time.Instant

internal data class ClosureRequest(
    val summary: String,
    val evidence: List<String>,
    val closedBy: String,
    val followUps: List<TaskRef>,
) {
    fun validate(snapshot: TaskLedgerSnapshot) {
        if (summary.isBlank()) throw WorkflowOperationException("closure summary must not be blank")
        if (evidence.isEmpty() || evidence.any { it.isBlank() }) {
            throw WorkflowOperationException("at least one non-blank evidence item is required")
        }
        followUps.forEach { followUp ->
            if (snapshot.task(followUp) == null) {
                throw WorkflowOperationException("unknown follow-up task $followUp")
            }
        }
    }

    fun render(closedAt: Instant): String =
        try {
            ClosureReceiptParser.renderClosed(closedAt, closedBy, summary, evidence, followUps)
        } catch (failure: IllegalArgumentException) {
            throw WorkflowOperationException(failure.message ?: "invalid closure receipt", failure)
        }

    fun matches(receipt: ClosureReceipt): Boolean {
        val expectedFollowUps = if (followUps.isEmpty()) listOf("none") else followUps.sorted().map { it.value }
        return receipt.closedBy == closedBy.trim() &&
            receipt.summary == summary.trim() &&
            receipt.verification == evidence.map { it.trim() } &&
            receipt.followUps == expectedFollowUps
    }
}

internal fun validateClosedReceipt(
    path: Path,
    receiptText: String,
) {
    val parsed = ClosureReceiptParser.parse(path, receiptText)
    val diagnostics =
        parsed.diagnostics +
            parsed.receipt
                ?.let { receipt -> ClosureReceiptParser.validate(path, LedgerState.CLOSED, receipt) }
                .orEmpty()
    if (diagnostics.isNotEmpty()) throw WorkflowValidationException(ValidationReport(diagnostics.sorted()))
}

internal fun closedReceipt(
    path: Path,
    markdown: MarkdownDocument,
): ClosureReceipt {
    val closure = markdown.section("Closure") ?: throw WorkflowOperationException("closed record has no receipt")
    return ClosureReceiptParser.parse(path, closure.content).receipt
        ?: throw WorkflowOperationException("closed record has an invalid receipt")
}
