package io.brule.workflow.task

import io.brule.workflow.diagnostics.WorkflowOperationException
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.model.TaskRef
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TaskClosureServiceTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `unchecked deliverable fails without changing task content or location`() {
        val layout = ledger(deliverable = "[ ]")
        val taskPath = layout.openTasksDirectory.resolve(TASK_FILE)
        val before = Files.readString(taskPath, StandardCharsets.UTF_8)

        assertFailsWith<WorkflowOperationException> {
            TaskClosureService(layout, clock = FIXED_CLOCK).close(request())
        }

        assertTrue(Files.exists(taskPath))
        assertFalse(Files.exists(layout.closedTasksDirectory.resolve(TASK_FILE)))
        assertEquals(before, Files.readString(taskPath, StandardCharsets.UTF_8))
    }

    @Test
    fun `close writes normalized receipt and moves task without committing`() {
        val layout = ledger(deliverable = "[x]")

        val result = TaskClosureService(layout, clock = FIXED_CLOCK).close(request())

        assertFalse(Files.exists(layout.openTasksDirectory.resolve(TASK_FILE)))
        assertTrue(Files.exists(result.destination))
        val closed = Files.readString(result.destination, StandardCharsets.UTF_8)
        assertTrue("closed_at: 2026-07-19T20:00:00Z" in closed)
        assertTrue("summary: Implemented and verified" in closed)
        assertTrue("  - ./gradlew check" in closed)
        assertTrue("  - none" in closed)
        assertEquals(0, closed.count { it == '\r' })
    }

    @Test
    fun `aggregate closure rejects an open realization`() {
        val layout = ledger(deliverable = "[x]")
        write(
            layout.openTasksDirectory.resolve("TASK.test.002.realization.md"),
            realizationTask(),
        )

        val failure =
            assertFailsWith<WorkflowOperationException> {
                TaskClosureService(layout, clock = FIXED_CLOCK).close(request())
            }

        assertTrue("open realizations: TASK.test.002" in failure.message.orEmpty())
        assertTrue(Files.exists(layout.openTasksDirectory.resolve(TASK_FILE)))
    }

    private fun ledger(deliverable: String): RepositoryLayout {
        val layout = RepositoryLayout.at(root)
        write(layout.openEpicsDirectory.resolve("EPIC.test.001.example.md"), epic())
        write(layout.openRoadmapsDirectory.resolve("ROADMAP.test.001.example.md"), roadmap())
        write(layout.openTasksDirectory.resolve(TASK_FILE), task(deliverable))
        Files.createDirectories(layout.closedTasksDirectory)
        return layout
    }

    private fun request(): CloseTaskRequest =
        CloseTaskRequest(
            ref = requireNotNull(TaskRef.parse("TASK.test.001")),
            summary = "Implemented and verified",
            evidence = listOf("./gradlew check"),
            closedBy = "codex",
        )

    private fun epic(): String =
        """
        ---
        kind: epic
        schema: loom.agent/v1
        ref: EPIC.test.001
        ---

        # EPIC.test.001: Test epic

        ## Description

        Test hierarchy.

        ## Outcomes

        - Working tests.

        ## Boundaries

        - Test only.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun roadmap(): String =
        """
        ---
        kind: roadmap
        schema: loom.agent/v1
        ref: ROADMAP.test.001
        epic: EPIC.test.001
        ordinal: 0
        ---

        # ROADMAP.test.001: Test roadmap

        ## Description

        Test grouping.

        ## Outcomes

        - Closed task.

        ## Exit Criteria

        - [ ] Task closes.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun task(deliverable: String): String =
        """
        ---
        kind: task
        schema: loom.agent/v1
        ref: TASK.test.001
        roadmap: ROADMAP.test.001
        effort: LOW
        impact: HIGH
        depends:
        ---

        # TASK.test.001: Test closure

        ## Description

        Exercise task closure.

        ## Requirements

        - Preserve failure atomicity.

        ## Deliverables

        - $deliverable Closure works.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun realizationTask(): String =
        """
        ---
        kind: task
        schema: loom.agent/v1
        ref: TASK.test.002
        roadmap: ROADMAP.test.001
        effort: LOW
        impact: HIGH
        depends:
        realizes: TASK.test.001
        ---

        # TASK.test.002: Test realization

        ## Description

        Realize the aggregate task.

        ## Requirements

        - Preserve aggregate custody.

        ## Deliverables

        - [x] Realization remains open.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun openReceipt(): String =
        """
        ```yaml
        closed_at: null
        closed_by: null
        summary: null
        verification:
          - null
        follow_ups:
          - null
        ```
        """.trimIndent()

    private fun indentedOpenReceipt(): String = openReceipt().replace("\n", "\n        ")

    private fun write(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(requireNotNull(path.parent))
        Files.writeString(path, content, StandardCharsets.UTF_8)
    }

    private companion object {
        const val TASK_FILE: String = "TASK.test.001.test-closure.md"
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-07-19T20:00:00Z"), ZoneOffset.UTC)
    }
}
