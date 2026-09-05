package io.brule.workflow.task

import io.brule.workflow.diagnostics.WorkflowOperationException
import io.brule.workflow.io.AtomicPathMover
import io.brule.workflow.io.PathMover
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

internal class TaskExpectedFrontierClosureTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `exact simulated frontier is order independent and reasserted after closure`() {
        val layout = ledger()

        val closed =
            TaskClosureService(layout, clock = FIXED_CLOCK).close(
                request(listOf(taskRef("TASK.test.003"), taskRef("TASK.test.002"))),
            )

        val snapshot = TaskLedger(layout).load()
        assertEquals(taskRef("TASK.test.001"), closed.ref)
        assertTrue(snapshot.report.isValid, snapshot.report.errors.joinToString { it.message })
        assertEquals(
            listOf("TASK.test.002", "TASK.test.003"),
            snapshot.graph.frontier.map { it.ref.value },
        )
    }

    @Test
    fun `explicit empty expectation proves the final task leaves no frontier`() {
        val layout = singleTaskLedger()

        val closed = TaskClosureService(layout, clock = FIXED_CLOCK).close(request(emptyList()))

        assertEquals(taskRef("TASK.test.001"), closed.ref)
        assertEquals(emptyList(), TaskLedger(layout).load().graph.frontier)
    }

    @Test
    fun `frontier mismatch is rejected before any closure artifact or ledger path changes`() {
        val layout = ledger()
        val source = layout.openTasksDirectory.resolve(taskFile("TASK.test.001"))
        val original = read(source)

        val failure =
            assertFailsWith<WorkflowOperationException> {
                TaskClosureService(layout, clock = FIXED_CLOCK).close(
                    request(listOf(taskRef("TASK.test.002"))),
                )
            }

        assertTrue("simulated post-closure frontier mismatch" in failure.message.orEmpty())
        assertEquals(original, read(source))
        assertFalse(Files.exists(layout.closedTasksDirectory.resolve(source.fileName)))
        assertTransactionArtifactsAbsent(layout)
    }

    @Test
    fun `post-move frontier drift rolls the task closure back`() {
        val layout = ledger()
        var moves = 0
        val driftingMover =
            PathMover { source, destination ->
                AtomicPathMover.move(source, destination)
                moves += 1
                if (moves == 2) writeTask(layout, "TASK.test.005")
            }

        assertFailsWith<WorkflowOperationException> {
            TaskClosureService(layout, mover = driftingMover, clock = FIXED_CLOCK).close(
                request(listOf(taskRef("TASK.test.002"), taskRef("TASK.test.003"))),
            )
        }

        assertTrue(Files.exists(layout.openTasksDirectory.resolve(taskFile("TASK.test.001"))))
        assertFalse(Files.exists(layout.closedTasksDirectory.resolve(taskFile("TASK.test.001"))))
        assertTrue(Files.exists(layout.openTasksDirectory.resolve(taskFile("TASK.test.005"))))
        assertTransactionArtifactsAbsent(layout)
    }

    @Test
    fun `recovery revalidates the persisted frontier before accepting a committed move`() {
        val layout = ledger()
        var moves = 0
        val interruptedMover =
            PathMover { source, destination ->
                AtomicPathMover.move(source, destination)
                moves += 1
                if (moves == 2) throw SimulatedInterruption()
            }
        val expected = listOf(taskRef("TASK.test.002"), taskRef("TASK.test.003"))

        assertFailsWith<SimulatedInterruption> {
            TaskClosureService(layout, mover = interruptedMover, clock = FIXED_CLOCK).close(request(expected))
        }
        writeTask(layout, "TASK.test.005")

        assertFailsWith<WorkflowOperationException> {
            TaskClosureService(layout, clock = FIXED_CLOCK).close(request(expected))
        }

        assertTrue(Files.exists(layout.openTasksDirectory.resolve(taskFile("TASK.test.001"))))
        assertFalse(Files.exists(layout.closedTasksDirectory.resolve(taskFile("TASK.test.001"))))
        assertTransactionArtifactsAbsent(layout)
    }

    @Test
    fun `rollback quarantine preserves destination content replaced during the move`() {
        val layout = ledger()
        val source = layout.openTasksDirectory.resolve(taskFile("TASK.test.001"))
        val destination = layout.closedTasksDirectory.resolve(taskFile("TASK.test.001"))
        val original = read(source)
        var moves = 0
        val racingMover =
            PathMover { source, destination ->
                AtomicPathMover.move(source, destination)
                moves += 1
                when (moves) {
                    2 -> writeTask(layout, "TASK.test.005")
                    3 -> write(destination, "external replacement during rollback\n")
                }
            }
        val expected = listOf(taskRef("TASK.test.002"), taskRef("TASK.test.003"))

        assertFailsWith<WorkflowOperationException> {
            TaskClosureService(layout, mover = racingMover, clock = FIXED_CLOCK).close(request(expected))
        }

        val quarantine = transactionArtifact(layout, "rollback")
        assertEquals(original, read(source))
        assertFalse(Files.exists(destination))
        assertFalse(Files.exists(transactionArtifact(layout, "open")))
        assertTrue(Files.exists(transactionArtifact(layout, "txn")))
        assertEquals("external replacement during rollback\n", read(quarantine))

        val retryFailure =
            assertFailsWith<WorkflowOperationException> {
                TaskClosureService(layout, clock = FIXED_CLOCK).close(request(expected))
            }

        assertTrue("preserved for manual inspection" in retryFailure.message.orEmpty())
        assertEquals(original, read(source))
        assertFalse(Files.exists(destination))
        assertFalse(Files.exists(transactionArtifact(layout, "open")))
        assertTrue(Files.exists(transactionArtifact(layout, "txn")))
        assertEquals("external replacement during rollback\n", read(quarantine))
    }

    private fun ledger(): RepositoryLayout {
        val layout = RepositoryLayout.at(root)
        write(layout.openEpicsDirectory.resolve("EPIC.test.001.fixture.md"), epic())
        write(layout.openRoadmapsDirectory.resolve("ROADMAP.test.001.fixture.md"), roadmap())
        writeTask(layout, "TASK.test.001")
        writeTask(layout, "TASK.test.002", listOf("TASK.test.001"))
        writeTask(layout, "TASK.test.003")
        writeTask(layout, "TASK.test.004", listOf("TASK.test.002"))
        Files.createDirectories(layout.closedTasksDirectory)
        return layout
    }

    private fun singleTaskLedger(): RepositoryLayout {
        val layout = RepositoryLayout.at(root)
        write(layout.openEpicsDirectory.resolve("EPIC.test.001.fixture.md"), epic())
        write(layout.openRoadmapsDirectory.resolve("ROADMAP.test.001.fixture.md"), roadmap())
        writeTask(layout, "TASK.test.001")
        Files.createDirectories(layout.closedTasksDirectory)
        return layout
    }

    private fun request(expected: List<TaskRef>): CloseTaskRequest =
        CloseTaskRequest(
            ref = taskRef("TASK.test.001"),
            summary = "Closed the exact frontier fixture",
            evidence = listOf("workflow frontier integration test"),
            expectedFrontier = expected,
        )

    private fun writeTask(
        layout: RepositoryLayout,
        ref: String,
        dependencies: List<String> = emptyList(),
    ) {
        write(layout.openTasksDirectory.resolve(taskFile(ref)), task(ref, dependencies))
    }

    private fun task(
        ref: String,
        dependencies: List<String>,
    ): String =
        """
        ---
        kind: task
        schema: loom.agent/v1
        ref: $ref
        roadmap: ROADMAP.test.001
        effort: LOW
        impact: HIGH
        depends:
        ${dependencies.joinToString("\n        ") { "  - $it" }}
        ---

        # $ref: Frontier fixture

        ## Description

        Exercise exact frontier closure.

        ## Requirements

        - Preserve exact Kahn semantics.

        ## Deliverables

        - [x] Frontier behavior is verified.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun epic(): String =
        """
        ---
        kind: epic
        schema: loom.agent/v1
        ref: EPIC.test.001
        ---

        # EPIC.test.001: Test epic

        ## Description

        Test exact task closure.

        ## Outcomes

        - Deterministic closure.

        ## Boundaries

        - Workflow tests only.

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

        Group exact frontier fixtures.

        ## Outcomes

        - Exact task frontier.

        ## Exit Criteria

        - [ ] Fixture remains open.

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

    private fun taskFile(ref: String): String = "$ref.fixture.md"

    private fun taskRef(value: String): TaskRef = requireNotNull(TaskRef.parse(value))

    private fun transactionArtifact(
        layout: RepositoryLayout,
        suffix: String,
    ): Path = layout.agentsDirectory.resolve(".taskctl-close-TASK.test.001.$suffix")

    private fun assertTransactionArtifactsAbsent(layout: RepositoryLayout) {
        assertFalse(Files.exists(transactionArtifact(layout, "open")))
        assertFalse(Files.exists(transactionArtifact(layout, "closed")))
        assertFalse(Files.exists(transactionArtifact(layout, "txn")))
        assertFalse(Files.exists(transactionArtifact(layout, "rollback")))
    }

    private fun read(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)

    private fun write(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(requireNotNull(path.parent))
        Files.writeString(path, content, StandardCharsets.UTF_8)
    }

    private class SimulatedInterruption : Error("simulated process interruption")

    private companion object {
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-07-19T20:00:00Z"), ZoneOffset.UTC)
    }
}
