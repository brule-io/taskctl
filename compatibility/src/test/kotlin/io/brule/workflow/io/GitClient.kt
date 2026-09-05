package io.brule.workflow.io

import io.brule.workflow.diagnostics.WorkflowOperationException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

public data class ProcessResult(
    public val exitCode: Int,
    public val standardOutput: String,
    public val standardError: String,
) {
    public fun requireSuccess(description: String) {
        if (exitCode != 0) {
            val detail = standardError.trim().ifEmpty { standardOutput.trim() }
            throw WorkflowOperationException("$description failed${if (detail.isEmpty()) "" else ": $detail"}")
        }
    }
}

public fun interface CommandRunner {
    public fun run(
        command: List<String>,
        workingDirectory: Path?,
    ): ProcessResult
}

public object SystemCommandRunner : CommandRunner {
    override fun run(
        command: List<String>,
        workingDirectory: Path?,
    ): ProcessResult {
        val builder = ProcessBuilder(command)
        if (workingDirectory != null) builder.directory(workingDirectory.toFile())
        val process = builder.start()
        val readers = Executors.newFixedThreadPool(2)
        try {
            val standardOutput =
                CompletableFuture.supplyAsync(
                    { process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8) },
                    readers,
                )
            val standardError =
                CompletableFuture.supplyAsync(
                    { process.errorStream.readAllBytes().toString(StandardCharsets.UTF_8) },
                    readers,
                )
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                throw WorkflowOperationException("command timed out: ${command.firstOrNull() ?: "<empty>"}")
            }
            return ProcessResult(process.exitValue(), standardOutput.get(), standardError.get())
        } finally {
            readers.shutdownNow()
        }
    }
}

public class GitClient(
    private val runner: CommandRunner = SystemCommandRunner,
) {
    public fun run(
        arguments: List<String>,
        directory: Path? = null,
    ): ProcessResult = runner.run(listOf("git") + arguments, directory)

    public fun require(
        arguments: List<String>,
        directory: Path? = null,
        description: String,
    ): String {
        val result = run(arguments, directory)
        result.requireSuccess(description)
        return result.standardOutput.trim()
    }
}
