package io.brule.workflow.diagnostics

import java.nio.file.Path

public enum class DiagnosticSeverity {
    ERROR,
    WARNING,
}

public data class Diagnostic(
    public val code: String,
    public val message: String,
    public val path: Path? = null,
    public val line: Int? = null,
    public val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
) : Comparable<Diagnostic> {
    override fun compareTo(other: Diagnostic): Int =
        compareValuesBy(
            this,
            other,
            { it.path?.toString()?.replace('\\', '/') ?: "" },
            { it.line ?: 0 },
            { it.code },
            { it.message },
        )

    public fun render(root: Path): String {
        val renderedPath =
            path?.let { candidate ->
                val normalized = candidate.toAbsolutePath().normalize()
                val relative = if (normalized.startsWith(root)) root.relativize(normalized) else normalized
                relative.toString().replace('\\', '/')
            }
        val location =
            buildString {
                if (renderedPath != null) append(renderedPath)
                if (line != null) append(":$line")
                if (isNotEmpty()) append(": ")
            }
        return "${severity.name} [$code] $location$message"
    }
}

public data class ValidationReport(
    public val diagnostics: List<Diagnostic>,
) {
    public val errors: List<Diagnostic> = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }.sorted()
    public val warnings: List<Diagnostic> = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }.sorted()
    public val isValid: Boolean = errors.isEmpty()

    public companion object {
        public val EMPTY: ValidationReport = ValidationReport(emptyList())
    }
}

public class WorkflowValidationException(
    public val report: ValidationReport,
) : IllegalStateException(report.errors.joinToString(System.lineSeparator()) { it.message })

public class WorkflowOperationException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
