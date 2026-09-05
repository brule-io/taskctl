package io.brule.tasking.conformance

import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.io.withWorkflowLock
import io.brule.workflow.io.withWorkflowRead
import io.brule.workflow.task.TaskLedger
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ReadBoundaryTest {
    @TempDir lateinit var root: Path

    @Test fun `empty consumer inspection creates neither ledger nor lock`() {
        val before = Files.list(root).use { it.toList() }
        TaskLedger(RepositoryLayout.at(root)).load()
        assertEquals(before, Files.list(root).use { it.toList() })
    }

    @Test fun `read rechecks under existing lock when first writer appeared`() {
        val layout = RepositoryLayout.at(root)
        var attempts = 0
        val result = withWorkflowRead(layout) {
            attempts++
            if (attempts == 1) {
                // Model the first cooperating writer winning the race while
                // the reader observes an incomplete transaction.
                Files.createDirectories(layout.agentsDirectory)
                Files.writeString(layout.workflowLock, "")
                error("intermediate read must be discarded")
            }
            "stable"
        }
        assertEquals("stable", result)
        assertEquals(2, attempts)
        val stamp = Files.getLastModifiedTime(layout.workflowLock)
        assertEquals("again", withWorkflowRead(layout) { "again" })
        assertEquals(stamp, Files.getLastModifiedTime(layout.workflowLock))
    }

    @Test fun `read waits for a cooperating writer without making consumer writes`() {
        val layout = RepositoryLayout.at(root)
        withWorkflowLock(layout) { Files.writeString(layout.agentsDirectory.resolve("sentinel"), "stable") }
        val before = Files.readAllBytes(layout.workflowLock)
        assertEquals("stable", withWorkflowRead(layout) { Files.readString(layout.agentsDirectory.resolve("sentinel")) })
        assertContentEquals(before, Files.readAllBytes(layout.workflowLock))
    }
}
