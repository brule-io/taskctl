package io.brule.workflow.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.option
import io.brule.workflow.diagnostics.WorkflowOperationException
import io.brule.workflow.diagnostics.WorkflowValidationException
import io.brule.workflow.io.RepositoryLayout
import java.nio.file.Path

public abstract class RepositoryCommand(
    name: String,
    private val helpText: String,
) : CliktCommand(name = name) {
    override fun help(context: Context): String = helpText

    private val repository: String? by option(
        "--repo",
        metavar = "PATH",
        help = "Repository root (auto-discovered by default).",
    )

    protected fun <T> inRepository(operation: (RepositoryLayout) -> T): T {
        val layout = repository?.let { RepositoryLayout.at(Path.of(it)) } ?: RepositoryLayout.discover()
        return try {
            operation(layout)
        } catch (failure: WorkflowValidationException) {
            val message = failure.report.errors.joinToString("\n") { it.render(layout.root) }
            throw PrintMessage(message, statusCode = 1, printError = true)
        } catch (failure: WorkflowOperationException) {
            throw PrintMessage(failure.message ?: "workflow operation failed", statusCode = 1, printError = true)
        } catch (failure: IllegalArgumentException) {
            throw PrintMessage(failure.message ?: "invalid argument", statusCode = 2, printError = true)
        }
    }
}
