package io.brule.workflow.task

import io.brule.tasking.core.Canonical
import io.brule.tasking.core.stringMap
import io.brule.workflow.diagnostics.WorkflowOperationException
import io.brule.workflow.io.RepositoryLayout
import java.nio.file.Files

/** Exact authored-record snapshot, distinct from a semantic contract digest.
 * Caller holds the workflow read or write lock. Runtime files are excluded. */
object RecordSnapshot {
    fun digest(layout: RepositoryLayout): String = digest(records(layout))

    internal fun assertMatches(layout: RepositoryLayout, expected: String?, target: LedgerClosureTarget) {
        if (expected == null) return
        val documents = records(layout).toMutableMap()
        val prefix = ".taskctl-close-${target.refValue}"
        val journal = layout.agentsDirectory.resolve("$prefix.txn")
        if (Files.exists(journal)) {
            val parsed = ClosureJournal.parse(journal, target)
            val source = target.openDirectory(layout).resolve(parsed.fileName)
            val backup = layout.agentsDirectory.resolve("$prefix.open")
            val original = when {
                Files.isRegularFile(backup) -> backup
                Files.isRegularFile(source) -> source
                else -> throw WorkflowOperationException("cannot reconstruct guarded pre-closure snapshot; original record unavailable in pending recovery")
            }
            documents.remove(layout.display(target.closedDirectory(layout).resolve(parsed.fileName)))
            documents[layout.display(source)] = Canonical.sha256(Files.readAllBytes(original))
        }
        if (digest(documents) != expected) throw WorkflowOperationException("stale record snapshot; inspect the current ledger before closing")
    }

    private fun digest(documents: Map<String, String>) = Canonical.digest("tasking/fantastikt-loom-agent-2026/record-snapshot/1", stringMap(documents))
    private fun records(layout: RepositoryLayout): Map<String, String> = buildMap {
        listOf(layout.openTasksDirectory, layout.closedTasksDirectory, layout.openRoadmapsDirectory,
            layout.closedRoadmapsDirectory, layout.openEpicsDirectory, layout.closedEpicsDirectory).forEach { directory ->
            if (Files.isDirectory(directory)) Files.list(directory).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }.forEach {
                    put(layout.display(it), Canonical.sha256(Files.readAllBytes(it)))
                }
            }
        }
    }
}
