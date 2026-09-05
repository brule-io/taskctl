package io.brule.workflow.task

import io.brule.workflow.diagnostics.WorkflowOperationException
import io.brule.workflow.diagnostics.WorkflowValidationException
import io.brule.workflow.io.AtomicPathMover
import io.brule.workflow.io.GitClient
import io.brule.workflow.io.PathMover
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.model.TaskRef
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TaskClosureTransactionInvariantTest {
    @org.junit.jupiter.api.io.TempDir
    lateinit var root: Path

    @kotlin.test.Test
    fun `successful close leaves Git HEAD and index tree unchanged`() {
        val layout = ledger()
        val git = initializeGitRepository()
        val headBefore = git.require(listOf("rev-parse", "HEAD"), root, "read HEAD before close")
        val indexBefore = git.require(listOf("write-tree"), root, "read index tree before close")

        val result = TaskClosureService(layout, clock = FIXED_CLOCK).close(request())

        assertTrue(Files.exists(result.destination))
        assertEquals(headBefore, git.require(listOf("rev-parse", "HEAD"), root, "read HEAD after close"))
        assertEquals(indexBefore, git.require(listOf("write-tree"), root, "read index tree after close"))
        assertTrue(git.require(listOf("status", "--porcelain"), root, "read close worktree changes").isNotBlank())
    }

    @kotlin.test.Test
    fun `failed staged move rolls back to byte-identical open task`() {
        val layout = ledger()
        val source = layout.openTasksDirectory.resolve(TASK_FILE)
        val original = read(source)
        val mover = FailingPathMover(setOf(2))

        assertFailsWith<WorkflowOperationException> {
            TaskClosureService(layout, mover = mover, clock = FIXED_CLOCK).close(request())
        }

        assertEquals(original, read(source))
        assertFalse(Files.exists(layout.closedTasksDirectory.resolve(TASK_FILE)))
        assertTransactionArtifactsAbsent(layout)
    }

    @kotlin.test.Test
    fun `orphaned backup without a journal is preserved for manual inspection`() {
        val layout = ledger()
        val source = layout.openTasksDirectory.resolve(TASK_FILE)
        val original = read(source)
        val orphan = transactionArtifact(layout, "open")
        write(orphan, "orphaned backup evidence\n")

        val failure =
            assertFailsWith<WorkflowOperationException> {
                TaskClosureService(layout, clock = FIXED_CLOCK).close(request())
            }

        assertTrue("orphaned closure transaction artifacts" in failure.message.orEmpty())
        assertEquals(original, read(source))
        assertEquals("orphaned backup evidence\n", read(orphan))
        assertFalse(Files.exists(layout.closedTasksDirectory.resolve(TASK_FILE)))
        assertFalse(Files.exists(transactionArtifact(layout, "txn")))
    }

    @kotlin.test.Test
    fun `literal null closure values fail before mutation`() {
        val layout = ledger()
        val source = layout.openTasksDirectory.resolve(TASK_FILE)
        val original = read(source)
        val valid = request()
        val invalidRequests =
            listOf(
                valid.copy(summary = "null"),
                valid.copy(closedBy = "null"),
                valid.copy(evidence = listOf("null")),
            )

        invalidRequests.forEach { invalid ->
            assertFailsWith<WorkflowValidationException> {
                TaskClosureService(layout, clock = FIXED_CLOCK).close(invalid)
            }
            assertEquals(original, read(source))
            assertFalse(Files.exists(layout.closedTasksDirectory.resolve(TASK_FILE)))
            assertTransactionArtifactsAbsent(layout)
        }
    }

    @kotlin.test.Test
    fun `pending failed transaction is recovered before a retry completes`() {
        val layout = ledger()
        val source = layout.openTasksDirectory.resolve(TASK_FILE)
        val interruptedMover = FailingPathMover(setOf(2, 3))

        assertFailsWith<WorkflowOperationException> {
            TaskClosureService(layout, mover = interruptedMover, clock = FIXED_CLOCK).close(request())
        }

        assertFalse(Files.exists(source))
        assertTrue(Files.exists(transactionArtifact(layout, "open")))
        assertTrue(Files.exists(transactionArtifact(layout, "closed")))
        assertTrue(Files.exists(transactionArtifact(layout, "txn")))
        val pendingSnapshot = TaskLedger(layout).load()
        assertTrue(pendingSnapshot.report.errors.any { it.code == "TASK_CLOSURE_PENDING" })

        val result = TaskClosureService(layout, clock = FIXED_CLOCK).close(request())

        assertTrue(Files.exists(result.destination))
        assertFalse(Files.exists(source))
        assertTrue("summary: Implemented and verified" in read(result.destination))
        assertTransactionArtifactsAbsent(layout)
    }

    @kotlin.test.Test
    fun `retry after committed interruption returns the matching closure`() {
        val layout = ledger()
        var moves = 0
        val interruptAfterCommit =
            PathMover { source, destination ->
                AtomicPathMover.move(source, destination)
                moves += 1
                if (moves == 2) throw SimulatedInterruption()
            }

        assertFailsWith<SimulatedInterruption> {
            TaskClosureService(layout, mover = interruptAfterCommit, clock = FIXED_CLOCK).close(request())
        }

        val recovered = TaskClosureService(layout, clock = FIXED_CLOCK).close(request())

        assertEquals(Instant.parse("2026-07-19T20:00:00Z"), recovered.closedAt)
        assertTrue(Files.exists(recovered.destination))
        assertTransactionArtifactsAbsent(layout)
    }

    @kotlin.test.Test
    fun `wrong retry receipt retains the committed transaction for a correct retry`() {
        val layout = ledger()
        var moves = 0
        val interruptAfterCommit =
            PathMover { source, destination ->
                AtomicPathMover.move(source, destination)
                moves += 1
                if (moves == 2) throw SimulatedInterruption()
            }
        assertFailsWith<SimulatedInterruption> {
            TaskClosureService(layout, mover = interruptAfterCommit, clock = FIXED_CLOCK).close(request())
        }

        val failure =
            assertFailsWith<WorkflowOperationException> {
                TaskClosureService(layout, clock = FIXED_CLOCK).close(
                    request().copy(evidence = listOf("different retry evidence")),
                )
            }

        assertTrue("receipt does not match" in failure.message.orEmpty())
        assertFalse(Files.exists(layout.openTasksDirectory.resolve(TASK_FILE)))
        assertTrue(Files.exists(layout.closedTasksDirectory.resolve(TASK_FILE)))
        assertTrue(Files.exists(transactionArtifact(layout, "open")))
        assertTrue(Files.exists(transactionArtifact(layout, "txn")))

        val recovered = TaskClosureService(layout, clock = FIXED_CLOCK).close(request())

        assertTrue(Files.exists(recovered.destination))
        assertTransactionArtifactsAbsent(layout)
    }

    @kotlin.test.Test
    fun `retry refuses a committed destination whose content diverged`() {
        val layout = ledger()
        var moves = 0
        val interruptAfterCommit =
            PathMover { source, destination ->
                AtomicPathMover.move(source, destination)
                moves += 1
                if (moves == 2) throw SimulatedInterruption()
            }
        assertFailsWith<SimulatedInterruption> {
            TaskClosureService(layout, mover = interruptAfterCommit, clock = FIXED_CLOCK).close(request())
        }
        val destination = layout.closedTasksDirectory.resolve(TASK_FILE)
        Files.writeString(destination, read(destination) + "tampered\n", StandardCharsets.UTF_8)

        assertFailsWith<WorkflowOperationException> {
            TaskClosureService(layout, clock = FIXED_CLOCK).close(request())
        }

        assertTrue(Files.exists(transactionArtifact(layout, "txn")))
        assertTrue(Files.exists(transactionArtifact(layout, "open")))
    }

    @kotlin.test.Test
    fun `ledger readers wait for an in flight closure transaction`() {
        val layout = ledger()
        val mutationEntered = CountDownLatch(1)
        val releaseMutation = CountDownLatch(1)
        val pausingMover =
            PathMover { source, destination ->
                mutationEntered.countDown()
                check(releaseMutation.await(5, TimeUnit.SECONDS)) { "test did not release closure mutation" }
                AtomicPathMover.move(source, destination)
            }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val close =
                executor.submit<ClosedTask> {
                    TaskClosureService(layout, mover = pausingMover, clock = FIXED_CLOCK).close(request())
                }
            assertTrue(mutationEntered.await(5, TimeUnit.SECONDS))
            val read = executor.submit<TaskLedgerSnapshot> { TaskLedger(layout).load() }

            assertFailsWith<TimeoutException> { read.get(250, TimeUnit.MILLISECONDS) }
            releaseMutation.countDown()

            assertTrue(Files.exists(close.get(5, TimeUnit.SECONDS).destination))
            val snapshot = read.get(5, TimeUnit.SECONDS)
            assertTrue(snapshot.report.isValid)
            assertEquals(LedgerState.CLOSED, snapshot.task(request().ref)?.state)
        } finally {
            releaseMutation.countDown()
            executor.shutdown()
        }
    }

    private fun ledger(): RepositoryLayout {
        val layout = RepositoryLayout.at(root)
        write(layout.openEpicsDirectory.resolve("EPIC.test.001.example.md"), epic())
        write(layout.openRoadmapsDirectory.resolve("ROADMAP.test.001.example.md"), roadmap())
        write(layout.openTasksDirectory.resolve(TASK_FILE), task())
        Files.createDirectories(layout.closedTasksDirectory)
        return layout
    }

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

    private fun request(): CloseTaskRequest =
        CloseTaskRequest(
            ref = requireNotNull(TaskRef.parse("TASK.test.001")),
            summary = "Implemented and verified",
            evidence = listOf("workflow unit test"),
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

        Test transaction hierarchy.

        ## Outcomes

        - Recoverable closure.

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

        Test transaction grouping.

        ## Outcomes

        - Closed task.

        ## Exit Criteria

        - [ ] Task closes.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun task(): String =
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

        # TASK.test.001: Test closure transaction

        ## Description

        Exercise recoverable task closure.

        ## Requirements

        - Preserve the original task until commit.

        ## Deliverables

        - [x] Closure transaction works.

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
        const val TASK_FILE: String = "TASK.test.001.test-closure-transaction.md"
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-07-19T20:00:00Z"), ZoneOffset.UTC)
    }
}
