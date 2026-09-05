package io.brule.workflow.task

import io.brule.workflow.diagnostics.WorkflowOperationException
import io.brule.workflow.diagnostics.WorkflowValidationException
import io.brule.workflow.io.AtomicPathMover
import io.brule.workflow.io.AtomicTextFileWriter
import io.brule.workflow.io.GitClient
import io.brule.workflow.io.PathMover
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.io.TextFileWriter
import io.brule.workflow.model.RoadmapRef
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
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

internal class RoadmapClosureServiceTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `close writes a normalized roadmap receipt without closing its epic or mutating Git`() {
        val layout = ledger(exitCriterion = "[x]", memberOpen = false)
        val epicPath = layout.openEpicsDirectory.resolve(EPIC_FILE)
        val epicBefore = read(epicPath)
        val git = initializeGitRepository()
        val headBefore = git.require(listOf("rev-parse", "HEAD"), root, "read HEAD before roadmap close")
        val indexBefore = git.require(listOf("write-tree"), root, "read index before roadmap close")

        val result = RoadmapClosureService(layout, clock = FIXED_CLOCK).close(request())

        assertFalse(Files.exists(layout.openRoadmapsDirectory.resolve(ROADMAP_FILE)))
        assertTrue(Files.exists(result.destination))
        val closed = read(result.destination)
        assertTrue("closed_at: 2026-07-19T20:00:00Z" in closed)
        assertTrue("summary: Closed the verified roadmap" in closed)
        assertTrue("  - workflow roadmap integration test" in closed)
        assertEquals(0, closed.count { it == '\r' })
        assertEquals(epicBefore, read(epicPath))
        assertFalse(Files.exists(layout.closedEpicsDirectory.resolve(EPIC_FILE)))
        assertEquals(headBefore, git.require(listOf("rev-parse", "HEAD"), root, "read HEAD after roadmap close"))
        assertEquals(indexBefore, git.require(listOf("write-tree"), root, "read index after roadmap close"))
    }

    @Test
    fun `unchecked exit criterion fails without changing roadmap content or location`() {
        val layout = ledger(exitCriterion = "[ ]", memberOpen = false)
        val source = layout.openRoadmapsDirectory.resolve(ROADMAP_FILE)
        val original = read(source)

        assertFailsWith<WorkflowOperationException> {
            RoadmapClosureService(layout, clock = FIXED_CLOCK).close(request())
        }

        assertEquals(original, read(source))
        assertFalse(Files.exists(layout.closedRoadmapsDirectory.resolve(ROADMAP_FILE)))
        assertTransactionArtifactsAbsent(layout)
    }

    @Test
    fun `non-empty open receipt is rejected before roadmap mutation`() {
        val layout = ledger(exitCriterion = "[x]", memberOpen = false)
        val source = layout.openRoadmapsDirectory.resolve(ROADMAP_FILE)
        val invalid =
            read(source).replace(
                "summary: null",
                "summary: premature receipt",
            )
        write(source, invalid)

        assertFailsWith<WorkflowValidationException> {
            RoadmapClosureService(layout, clock = FIXED_CLOCK).close(request())
        }

        assertEquals(invalid, read(source))
        assertFalse(Files.exists(layout.closedRoadmapsDirectory.resolve(ROADMAP_FILE)))
        assertTransactionArtifactsAbsent(layout)
    }

    @Test
    fun `open member task prevents roadmap closure`() {
        val layout = ledger(exitCriterion = "[x]", memberOpen = true)
        val source = layout.openRoadmapsDirectory.resolve(ROADMAP_FILE)

        val failure =
            assertFailsWith<WorkflowOperationException> {
                RoadmapClosureService(layout, clock = FIXED_CLOCK).close(request())
            }

        assertTrue("open member tasks: TASK.test.001" in failure.message.orEmpty())
        assertTrue(Files.exists(source))
        assertFalse(Files.exists(layout.closedRoadmapsDirectory.resolve(ROADMAP_FILE)))
        assertTransactionArtifactsAbsent(layout)
    }

    @Test
    fun `destination collision introduced during staging is rejected before source mutation`() {
        val layout = ledger(exitCriterion = "[x]", memberOpen = false)
        val source = layout.openRoadmapsDirectory.resolve(ROADMAP_FILE)
        val original = read(source)
        val destination = layout.closedRoadmapsDirectory.resolve(ROADMAP_FILE)
        val collidingWriter =
            TextFileWriter { path, content ->
                AtomicTextFileWriter.write(path, content)
                if (path.fileName.toString().endsWith(".closed")) {
                    write(destination, "pre-existing destination\n")
                }
            }

        val failure =
            assertFailsWith<WorkflowOperationException> {
                RoadmapClosureService(layout, writer = collidingWriter, clock = FIXED_CLOCK).close(request())
            }

        assertTrue("closure destination already exists" in failure.message.orEmpty())
        assertEquals(original, read(source))
        assertEquals("pre-existing destination\n", read(destination))
        assertTransactionArtifactsAbsent(layout)
    }

    @Test
    fun `failed destination move rolls roadmap closure back byte identically`() {
        val layout = ledger(exitCriterion = "[x]", memberOpen = false)
        val source = layout.openRoadmapsDirectory.resolve(ROADMAP_FILE)
        val original = read(source)

        assertFailsWith<WorkflowOperationException> {
            RoadmapClosureService(
                layout,
                mover = FailingPathMover(setOf(2)),
                clock = FIXED_CLOCK,
            ).close(request())
        }

        assertEquals(original, read(source))
        assertFalse(Files.exists(layout.closedRoadmapsDirectory.resolve(ROADMAP_FILE)))
        assertTransactionArtifactsAbsent(layout)
    }

    @Test
    fun `committed interruption is recovered through the roadmap transaction journal`() {
        val layout = ledger(exitCriterion = "[x]", memberOpen = false)
        var moves = 0
        val interruptedMover =
            PathMover { source, destination ->
                AtomicPathMover.move(source, destination)
                moves += 1
                if (moves == 2) throw SimulatedInterruption()
            }

        assertFailsWith<SimulatedInterruption> {
            RoadmapClosureService(layout, mover = interruptedMover, clock = FIXED_CLOCK).close(request())
        }
        val pending = TaskLedger(layout).load()
        assertTrue(pending.report.errors.any { it.code == "ROADMAP_CLOSURE_PENDING" })

        val recovered = RoadmapClosureService(layout, clock = FIXED_CLOCK).close(request())

        assertTrue(Files.exists(recovered.destination))
        assertFalse(Files.exists(layout.openRoadmapsDirectory.resolve(ROADMAP_FILE)))
        assertTransactionArtifactsAbsent(layout)
    }

    @Test
    fun `wrong roadmap retry receipt retains the transaction for a correct retry`() {
        val layout = ledger(exitCriterion = "[x]", memberOpen = false)
        var moves = 0
        val interruptedMover =
            PathMover { source, destination ->
                AtomicPathMover.move(source, destination)
                moves += 1
                if (moves == 2) throw SimulatedInterruption()
            }
        assertFailsWith<SimulatedInterruption> {
            RoadmapClosureService(layout, mover = interruptedMover, clock = FIXED_CLOCK).close(request())
        }

        val failure =
            assertFailsWith<WorkflowOperationException> {
                RoadmapClosureService(layout, clock = FIXED_CLOCK).close(
                    request().copy(evidence = listOf("different retry evidence")),
                )
            }

        assertTrue("receipt does not match" in failure.message.orEmpty())
        assertFalse(Files.exists(layout.openRoadmapsDirectory.resolve(ROADMAP_FILE)))
        assertTrue(Files.exists(layout.closedRoadmapsDirectory.resolve(ROADMAP_FILE)))
        assertTrue(Files.exists(transactionArtifact(layout, "open")))
        assertTrue(Files.exists(transactionArtifact(layout, "txn")))

        val recovered = RoadmapClosureService(layout, clock = FIXED_CLOCK).close(request())

        assertTrue(Files.exists(recovered.destination))
        assertTransactionArtifactsAbsent(layout)
    }

    private fun ledger(
        exitCriterion: String,
        memberOpen: Boolean,
    ): RepositoryLayout {
        val layout = RepositoryLayout.at(root)
        write(layout.openEpicsDirectory.resolve(EPIC_FILE), epic())
        write(layout.openRoadmapsDirectory.resolve(ROADMAP_FILE), roadmap(exitCriterion))
        val taskDirectory = if (memberOpen) layout.openTasksDirectory else layout.closedTasksDirectory
        write(taskDirectory.resolve(TASK_FILE), task(closed = !memberOpen))
        Files.createDirectories(layout.closedRoadmapsDirectory)
        return layout
    }

    private fun request(): CloseRoadmapRequest =
        CloseRoadmapRequest(
            ref = requireNotNull(RoadmapRef.parse("ROADMAP.test.001")),
            summary = "Closed the verified roadmap",
            evidence = listOf("workflow roadmap integration test"),
        )

    private fun initializeGitRepository(): GitClient {
        val git = GitClient()
        git.run(listOf("init", "-b", "main"), root).requireSuccess("initialize Git fixture")
        git.run(listOf("config", "user.name", "Workflow Test"), root).requireSuccess("configure Git user")
        git
            .run(listOf("config", "user.email", "workflow@example.invalid"), root)
            .requireSuccess("configure Git email")
        git.run(listOf("add", ".agents"), root).requireSuccess("stage ledger fixture")
        git.run(listOf("commit", "-m", "ledger fixture"), root).requireSuccess("commit ledger fixture")
        return git
    }

    private fun epic(): String =
        """
        ---
        kind: epic
        schema: loom.agent/v1
        ref: EPIC.test.001
        ---

        # EPIC.test.001: Test epic

        ## Description

        Test roadmap closure.

        ## Outcomes

        - Roadmap closure remains explicit.

        ## Boundaries

        - Epic closure is out of scope.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun roadmap(exitCriterion: String): String =
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

        Exercise roadmap closure.

        ## Outcomes

        - Evidence-bearing roadmap lifecycle.

        ## Exit Criteria

        - $exitCriterion Roadmap evidence exists.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun task(closed: Boolean): String =
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

        # TASK.test.001: Roadmap member

        ## Description

        Provide one roadmap member.

        ## Requirements

        - Exercise member lifecycle validation.

        ## Deliverables

        - [${if (closed) "x" else " "}] Member lifecycle is explicit.

        ## Closure

        ${if (closed) indentedClosedReceipt() else indentedOpenReceipt()}
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

    private fun closedReceipt(): String =
        """
        ```yaml
        closed_at: 2026-07-19T19:00:00Z
        closed_by: test
        summary: Closed the roadmap member fixture
        verification:
          - workflow roadmap fixture
        follow_ups:
          - none
        ```
        """.trimIndent()

    private fun indentedOpenReceipt(): String = openReceipt().replace("\n", "\n        ")

    private fun indentedClosedReceipt(): String = closedReceipt().replace("\n", "\n        ")

    private fun transactionArtifact(
        layout: RepositoryLayout,
        suffix: String,
    ): Path = layout.agentsDirectory.resolve(".taskctl-close-ROADMAP.test.001.$suffix")

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

    private class FailingPathMover(
        private val failingCalls: Set<Int>,
    ) : PathMover {
        private var callCount: Int = 0

        override fun move(
            source: Path,
            destination: Path,
        ) {
            callCount += 1
            if (callCount in failingCalls) throw IOException("injected move failure $callCount")
            AtomicPathMover.move(source, destination)
        }
    }

    private class SimulatedInterruption : Error("simulated process interruption")

    private companion object {
        const val EPIC_FILE: String = "EPIC.test.001.fixture.md"
        const val ROADMAP_FILE: String = "ROADMAP.test.001.fixture.md"
        const val TASK_FILE: String = "TASK.test.001.fixture.md"
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-07-19T20:00:00Z"), ZoneOffset.UTC)
    }
}
