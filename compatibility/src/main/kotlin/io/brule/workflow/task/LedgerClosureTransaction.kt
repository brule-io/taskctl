package io.brule.workflow.task

import io.brule.workflow.diagnostics.WorkflowOperationException
import io.brule.workflow.io.AtomicPathMover
import io.brule.workflow.io.AtomicTextFileWriter
import io.brule.workflow.io.PathMover
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.io.TextFileWriter
import io.brule.workflow.model.RoadmapRef
import io.brule.workflow.model.TaskRef
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal enum class LedgerClosureKind(
    val journalValue: String,
) {
    TASK("task"),
    ROADMAP("roadmap"),
}

internal sealed interface LedgerClosureTarget {
    val kind: LedgerClosureKind
    val refValue: String
    val recoveryCommand: String

    fun openDirectory(layout: RepositoryLayout): Path

    fun closedDirectory(layout: RepositoryLayout): Path

    fun acceptsFileName(fileName: String): Boolean

    data class Task(
        val ref: TaskRef,
    ) : LedgerClosureTarget {
        override val kind: LedgerClosureKind = LedgerClosureKind.TASK
        override val refValue: String = ref.value
        override val recoveryCommand: String = "taskctl close ${ref.value}"

        override fun openDirectory(layout: RepositoryLayout): Path = layout.openTasksDirectory

        override fun closedDirectory(layout: RepositoryLayout): Path = layout.closedTasksDirectory

        override fun acceptsFileName(fileName: String): Boolean = TaskRecordFileName.parse(fileName)?.ref == ref
    }

    data class Roadmap(
        val ref: RoadmapRef,
    ) : LedgerClosureTarget {
        override val kind: LedgerClosureKind = LedgerClosureKind.ROADMAP
        override val refValue: String = ref.value
        override val recoveryCommand: String = "taskctl close-roadmap ${ref.value}"

        override fun openDirectory(layout: RepositoryLayout): Path = layout.openRoadmapsDirectory

        override fun closedDirectory(layout: RepositoryLayout): Path = layout.closedRoadmapsDirectory

        override fun acceptsFileName(fileName: String): Boolean =
            ROADMAP_FILE.matches(fileName) && fileName.startsWith("${ref.value}.")
    }

    companion object {
        private val ROADMAP_FILE: Regex =
            Regex("ROADMAP\\.[a-z][a-z0-9-]*\\.\\d{3}\\.[a-z0-9]+(?:-[a-z0-9]+)*\\.md")

        fun fromJournalPath(path: Path): LedgerClosureTarget? {
            val fileName = path.fileName.toString()
            if (!fileName.startsWith(JOURNAL_PREFIX) || !fileName.endsWith(JOURNAL_SUFFIX)) return null
            val rawRef = fileName.removePrefix(JOURNAL_PREFIX).removeSuffix(JOURNAL_SUFFIX)
            return TaskRef.parse(rawRef)?.let(::Task) ?: RoadmapRef.parse(rawRef)?.let(::Roadmap)
        }

        private const val JOURNAL_PREFIX: String = ".taskctl-close-"
        private const val JOURNAL_SUFFIX: String = ".txn"
    }
}

internal data class ClosureJournal(
    val target: LedgerClosureTarget,
    val fileName: String,
    val contentHash: String,
    val expectedFrontier: List<TaskRef>?,
) {
    init {
        require(expectedFrontier == expectedFrontier?.distinct()?.sorted()) {
            "journal frontier must be a sorted exact set"
        }
        require(target.kind == LedgerClosureKind.TASK || expectedFrontier == null) {
            "only task closure may carry a frontier assertion"
        }
    }

    fun render(): String =
        buildString {
            appendLine("schema=$SCHEMA")
            appendLine("kind=${target.kind.journalValue}")
            appendLine("ref=${target.refValue}")
            appendLine("file=$fileName")
            appendLine("sha256=$contentHash")
            appendLine("frontier-asserted=${expectedFrontier != null}")
            appendLine("frontier-count=${expectedFrontier?.size ?: 0}")
            expectedFrontier.orEmpty().forEach { ref -> appendLine("frontier=${ref.value}") }
        }

    companion object {
        private const val SCHEMA: String = "loom.taskctl/closure-v1"
        private val SHA256: Regex = Regex("[0-9a-f]{64}")

        fun parse(
            path: Path,
            expectedTarget: LedgerClosureTarget,
        ): ClosureJournal {
            val lines = Files.readAllLines(path)
            if (lines.size < HEADER_LINES) invalidJournal(expectedTarget)
            val schema = value(lines[0], "schema=")
            val kind = value(lines[1], "kind=")
            val ref = value(lines[2], "ref=")
            val fileName = value(lines[3], "file=")
            val contentHash = value(lines[4], "sha256=")
            val frontierAsserted = value(lines[5], "frontier-asserted=").toBooleanStrictOrNull()
            val frontierCount = value(lines[6], "frontier-count=").toIntOrNull()
            val rawFrontier = lines.drop(HEADER_LINES).map { line -> value(line, "frontier=") }
            val frontier = rawFrontier.mapNotNull(TaskRef::parse)
            val valid =
                schema == SCHEMA &&
                    kind == expectedTarget.kind.journalValue &&
                    ref == expectedTarget.refValue &&
                    expectedTarget.acceptsFileName(fileName) &&
                    SHA256.matches(contentHash) &&
                    frontierAsserted != null &&
                    frontierCount != null &&
                    frontierCount >= 0 &&
                    rawFrontier.size == frontierCount &&
                    frontier.size == rawFrontier.size &&
                    frontier == frontier.distinct().sorted() &&
                    (frontierAsserted || frontier.isEmpty()) &&
                    (expectedTarget.kind == LedgerClosureKind.TASK || !frontierAsserted)
            if (!valid) invalidJournal(expectedTarget)
            return ClosureJournal(
                expectedTarget,
                fileName,
                contentHash,
                if (frontierAsserted == true) frontier else null,
            )
        }

        private fun value(
            line: String,
            prefix: String,
        ): String =
            line.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
                ?: throw WorkflowOperationException("invalid closure transaction journal field")

        private fun invalidJournal(target: LedgerClosureTarget): Nothing =
            throw WorkflowOperationException("invalid pending closure transaction for ${target.refValue}")

        private const val HEADER_LINES: Int = 7
    }
}

internal sealed interface ClosurePostCommitResult {
    data object Accepted : ClosurePostCommitResult

    data class RequestMismatch(
        val message: String,
    ) : ClosurePostCommitResult
}

internal fun interface ClosurePostCommitValidator {
    fun validate(
        destination: Path,
        ignoredJournal: Path,
        expectedFrontier: List<TaskRef>?,
    ): ClosurePostCommitResult
}

internal class LedgerClosureTransactionManager(
    private val layout: RepositoryLayout,
    private val writer: TextFileWriter = AtomicTextFileWriter,
    private val mover: PathMover = AtomicPathMover,
) {
    fun commit(
        target: LedgerClosureTarget,
        source: Path,
        destination: Path,
        content: String,
        expectedFrontier: List<TaskRef>?,
        postCommitValidator: ClosurePostCommitValidator,
    ): Path {
        require(
            target.acceptsFileName(source.fileName.toString()),
        ) { "source path does not identify ${target.refValue}" }
        require(source.fileName == destination.fileName) { "closure must preserve the record filename" }
        if (Files.exists(destination)) {
            throw WorkflowOperationException("closure destination already exists: ${layout.display(destination)}")
        }
        val transaction = transaction(target, source, destination)
        requireNoOrphanedTransaction(transaction)
        val journal =
            ClosureJournal(
                target,
                source.fileName.toString(),
                documentHash(content),
                expectedFrontier,
            )
        try {
            Files.deleteIfExists(transaction.staged)
            writer.write(transaction.staged, content)
            requireDestinationAbsent(destination)
            writer.write(transaction.journal, journal.render())
            if (Files.exists(destination)) {
                abort(transaction)
                throw WorkflowOperationException(
                    "closure destination already exists: ${layout.display(destination)}",
                )
            }
            mover.move(source, transaction.backup)
            mover.move(transaction.staged, destination)
            when (val result = postCommitValidator.validate(destination, transaction.journal, expectedFrontier)) {
                ClosurePostCommitResult.Accepted -> Unit
                is ClosurePostCommitResult.RequestMismatch -> throw RetainClosureTransaction(result.message)
            }
            finishBestEffort(transaction)
            return destination
        } catch (failure: RetainClosureTransaction) {
            throw WorkflowOperationException(failure.message ?: "closure request does not match transaction", failure)
        } catch (failure: Exception) {
            val recovery = runCatching { recover(target, expectedFrontier, postCommitValidator) }
            val committedDestination = recovery.getOrNull()
            if (committedDestination != null) return committedDestination
            if (!Files.exists(transaction.journal)) {
                Files.deleteIfExists(transaction.staged)
                if (failure is WorkflowOperationException) throw failure
            }
            recovery.exceptionOrNull()?.let(failure::addSuppressed)
            throw WorkflowOperationException(
                "${target.kind.journalValue} closure failed; the recoverable transaction was rolled back",
                failure,
            )
        }
    }

    fun recover(
        target: LedgerClosureTarget,
        requestedFrontier: List<TaskRef>?,
        postCommitValidator: ClosurePostCommitValidator,
    ): Path? {
        val initial = transaction(target)
        if (!Files.exists(initial.journal)) return null
        val journal = ClosureJournal.parse(initial.journal, target)
        if (journal.expectedFrontier != requestedFrontier) {
            throw WorkflowOperationException(
                "pending closure transaction for ${target.refValue} has a different expected frontier",
            )
        }
        val resolved =
            transaction(
                target,
                target.openDirectory(layout).resolve(journal.fileName),
                target.closedDirectory(layout).resolve(journal.fileName),
            )
        recoverQuarantine(resolved, journal.contentHash)
        when {
            Files.exists(resolved.destination) -> {
                if (Files.exists(resolved.source)) {
                    throw WorkflowOperationException("ambiguous pending closure transaction for ${target.refValue}")
                }
                if (fileHash(resolved.destination) != journal.contentHash) {
                    throw WorkflowOperationException(
                        "recovered closure destination content diverged for ${target.refValue}",
                    )
                }
                val validation =
                    try {
                        postCommitValidator.validate(
                            resolved.destination,
                            resolved.journal,
                            journal.expectedFrontier,
                        )
                    } catch (failure: Exception) {
                        if (!Files.exists(resolved.backup)) {
                            throw WorkflowOperationException(
                                "committed closure postcondition failed and cannot be rolled back for ${target.refValue}",
                                failure,
                            )
                        }
                        rollbackCommitted(resolved, journal.contentHash)
                        return null
                    }
                if (validation is ClosurePostCommitResult.RequestMismatch) {
                    throw WorkflowOperationException(validation.message)
                }
                finish(resolved)
                return resolved.destination
            }

            Files.exists(resolved.backup) -> {
                if (Files.exists(resolved.source)) {
                    throw WorkflowOperationException("ambiguous pending closure transaction for ${target.refValue}")
                }
                mover.move(resolved.backup, resolved.source)
                abort(resolved)
                return null
            }

            Files.exists(resolved.source) -> {
                abort(resolved)
                return null
            }

            else -> {
                throw WorkflowOperationException(
                    "pending closure transaction for ${target.refValue} cannot be recovered",
                )
            }
        }
    }

    private fun recoverQuarantine(
        transaction: ClosureTransaction,
        expectedHash: String,
    ) {
        if (!Files.exists(transaction.quarantine)) return
        if (Files.exists(transaction.destination)) {
            throw WorkflowOperationException(
                "ambiguous closure rollback quarantine for ${transaction.destination.fileName}",
            )
        }
        if (fileHash(transaction.quarantine) != expectedHash) {
            preserveDivergentQuarantine(transaction)
        }
        restoreOpenBackup(transaction)
        Files.delete(transaction.quarantine)
    }

    private fun rollbackCommitted(
        transaction: ClosureTransaction,
        expectedHash: String,
    ) {
        mover.move(transaction.destination, transaction.quarantine)
        if (fileHash(transaction.quarantine) != expectedHash) {
            preserveDivergentQuarantine(transaction)
        }
        restoreOpenBackup(transaction)
        Files.delete(transaction.quarantine)
        abort(transaction)
    }

    private fun preserveDivergentQuarantine(transaction: ClosureTransaction): Nothing {
        val restorationFailure = runCatching { restoreOpenBackup(transaction) }.exceptionOrNull()
        val failure =
            WorkflowOperationException(
                "closure rollback quarantine diverged and is preserved for manual inspection: " +
                    layout.display(transaction.quarantine),
            )
        restorationFailure?.let(failure::addSuppressed)
        throw failure
    }

    private fun restoreOpenBackup(transaction: ClosureTransaction) {
        val backupExists = Files.exists(transaction.backup)
        val sourceExists = Files.exists(transaction.source)
        when {
            backupExists && sourceExists -> {
                throw WorkflowOperationException(
                    "ambiguous closure rollback source for ${transaction.destination.fileName}",
                )
            }

            backupExists -> {
                mover.move(transaction.backup, transaction.source)
            }

            !sourceExists -> {
                throw WorkflowOperationException(
                    "closure rollback has neither an open source nor a backup for ${transaction.destination.fileName}",
                )
            }
        }
    }

    private fun requireNoOrphanedTransaction(transaction: ClosureTransaction) {
        if (
            Files.exists(transaction.backup) ||
            Files.exists(transaction.journal) ||
            Files.exists(transaction.quarantine)
        ) {
            throw WorkflowOperationException(
                "orphaned closure transaction artifacts require manual inspection for ${transaction.source.fileName}",
            )
        }
    }

    private fun requireDestinationAbsent(destination: Path) {
        if (Files.exists(destination)) {
            throw WorkflowOperationException("closure destination already exists: ${layout.display(destination)}")
        }
    }

    private fun finish(transaction: ClosureTransaction) {
        Files.deleteIfExists(transaction.backup)
        Files.deleteIfExists(transaction.staged)
        Files.deleteIfExists(transaction.journal)
    }

    private fun finishBestEffort(transaction: ClosureTransaction) {
        try {
            finish(transaction)
        } catch (_: Exception) {
            // The destination and its postconditions are committed; the journal makes cleanup recoverable.
        }
    }

    private fun abort(transaction: ClosureTransaction) {
        Files.deleteIfExists(transaction.staged)
        Files.deleteIfExists(transaction.backup)
        Files.deleteIfExists(transaction.journal)
    }

    private fun transaction(
        target: LedgerClosureTarget,
        source: Path? = null,
        destination: Path? = null,
    ): ClosureTransaction {
        val prefix = ".taskctl-close-${target.refValue}"
        return ClosureTransaction(
            source = source ?: target.openDirectory(layout),
            destination = destination ?: target.closedDirectory(layout),
            staged = layout.agentsDirectory.resolve("$prefix.closed"),
            backup = layout.agentsDirectory.resolve("$prefix.open"),
            journal = layout.agentsDirectory.resolve("$prefix.txn"),
            quarantine = layout.agentsDirectory.resolve("$prefix.rollback"),
        )
    }

    private fun documentHash(content: String): String {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n').trimEnd() + "\n"
        return sha256(normalized.toByteArray(Charsets.UTF_8))
    }

    private fun fileHash(path: Path): String = sha256(Files.readAllBytes(path))

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private data class ClosureTransaction(
        val source: Path,
        val destination: Path,
        val staged: Path,
        val backup: Path,
        val journal: Path,
        val quarantine: Path,
    )

    private class RetainClosureTransaction(
        message: String,
    ) : IllegalStateException(message)
}
