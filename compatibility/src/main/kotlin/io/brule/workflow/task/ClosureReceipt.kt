package io.brule.workflow.task

import io.brule.workflow.diagnostics.Diagnostic
import io.brule.workflow.document.FrontMatterDocument
import io.brule.workflow.document.FrontMatterParser
import io.brule.workflow.document.TypedFields
import io.brule.workflow.model.TaskRef
import java.nio.file.Path
import java.time.Instant

public data class ClosureReceipt(
    public val closedAt: String,
    public val closedBy: String,
    public val summary: String,
    public val verification: List<String>,
    public val followUps: List<String>,
) {
    public val isOpen: Boolean =
        closedAt == "null" &&
            closedBy == "null" &&
            summary == "null" &&
            verification == listOf("null") &&
            followUps == listOf("null")
}

public data class ClosureReceiptParseResult(
    public val receipt: ClosureReceipt?,
    public val diagnostics: List<Diagnostic>,
)

public object ClosureReceiptParser {
    private val fencedYaml: Regex = Regex("\\A```yaml\\n([\\s\\S]*?)\\n```\\z")
    private val fields: List<String> =
        listOf("closed_at", "closed_by", "summary", "verification", "follow_ups")

    public fun parse(
        path: Path,
        content: String,
    ): ClosureReceiptParseResult {
        val match =
            fencedYaml.matchEntire(content.trim())
                ?: return ClosureReceiptParseResult(
                    null,
                    listOf(Diagnostic("CLOSURE_FENCE", "Closure must contain exactly one fenced yaml receipt", path)),
                )
        val synthetic = "---\n${match.groupValues[1]}\n---\n"
        val parsed = FrontMatterParser.parse(path, synthetic)
        val diagnostics = parsed.diagnostics.toMutableList()
        val document = parsed.document ?: return ClosureReceiptParseResult(null, diagnostics.sorted())
        val reader = TypedFields(document, diagnostics)
        reader.rejectUnknown(fields.toSet())
        reader.requireOrder(fields)
        val closedAt = reader.requiredScalar("closed_at")
        val closedBy = reader.requiredScalar("closed_by")
        val summary = reader.requiredScalar("summary")
        val verification = reader.sequence("verification")
        val followUps = reader.sequence("follow_ups")
        if (closedAt == null || closedBy == null || summary == null) {
            return ClosureReceiptParseResult(null, diagnostics.sorted())
        }
        return ClosureReceiptParseResult(
            ClosureReceipt(closedAt, closedBy, summary, verification, followUps),
            diagnostics.sorted(),
        )
    }

    public fun validate(
        path: Path,
        state: LedgerState,
        receipt: ClosureReceipt,
    ): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        when (state) {
            LedgerState.OPEN -> {
                if (!receipt.isOpen) {
                    diagnostics +=
                        Diagnostic("CLOSURE_OPEN_VALUES", "open record closure values must all be null", path)
                }
            }

            LedgerState.CLOSED -> {
                val instant = runCatching { Instant.parse(receipt.closedAt) }.getOrNull()
                if (instant == null || !receipt.closedAt.endsWith('Z')) {
                    diagnostics +=
                        Diagnostic("CLOSURE_TIMESTAMP", "closed_at must be an RFC 3339 UTC timestamp ending in Z", path)
                }
                if (receipt.closedBy.isBlank() || receipt.closedBy == "null") {
                    diagnostics += Diagnostic("CLOSURE_ACTOR", "closed_by must be a stable actor identifier", path)
                }
                if (receipt.summary.isBlank() || receipt.summary == "null" || '\n' in receipt.summary) {
                    diagnostics += Diagnostic("CLOSURE_SUMMARY", "summary must be a non-empty single line", path)
                }
                if (receipt.verification.isEmpty() || receipt.verification.any { it.isBlank() || it == "null" }) {
                    diagnostics +=
                        Diagnostic("CLOSURE_VERIFICATION", "verification must contain concrete evidence", path)
                }
                val followUpsValid =
                    receipt.followUps == listOf("none") ||
                        (receipt.followUps.isNotEmpty() && receipt.followUps.all { TaskRef.parse(it) != null })
                if (!followUpsValid) {
                    diagnostics +=
                        Diagnostic("CLOSURE_FOLLOW_UPS", "follow_ups must contain task refs or the scalar none", path)
                }
            }
        }
        return diagnostics.sorted()
    }

    public fun renderClosed(
        closedAt: Instant,
        closedBy: String,
        summary: String,
        verification: List<String>,
        followUps: List<TaskRef>,
    ): String =
        buildString {
            appendLine("```yaml")
            appendLine("closed_at: $closedAt")
            appendLine("closed_by: ${plainScalar(closedBy)}")
            appendLine("summary: ${plainScalar(summary)}")
            appendLine("verification:")
            verification.forEach { appendLine("  - ${plainScalar(it)}") }
            appendLine("follow_ups:")
            if (followUps.isEmpty()) {
                appendLine(
                    "  - none",
                )
            } else {
                followUps.sorted().forEach { appendLine("  - ${it.value}") }
            }
            append("```")
        }

    private fun plainScalar(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty() && '\n' !in normalized && '\r' !in normalized) {
            "closure values must be non-empty single-line plain scalars"
        }
        require(
            !normalized.startsWith('[') && !normalized.startsWith('{') && !normalized.startsWith('&') &&
                !normalized.startsWith('*') && !normalized.startsWith('!') && !normalized.startsWith('"') &&
                !normalized.startsWith('\''),
        ) {
            "closure value requires unsupported YAML escaping: $normalized"
        }
        return normalized
    }
}
