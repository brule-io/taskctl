package io.brule.workflow.task

import io.brule.workflow.io.RepositoryLayout
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

internal class TaskLedgerCycleInvariantTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `dependency cycles are diagnosed even when they cross lifecycle directories`() {
        val layout = RepositoryLayout.at(root)
        write(layout.openEpicsDirectory.resolve("EPIC.test.001.fixture.md"), epic())
        write(layout.openRoadmapsDirectory.resolve("ROADMAP.test.001.fixture.md"), roadmap())
        write(
            layout.closedTasksDirectory.resolve("TASK.test.001.closed-cycle.md"),
            task("TASK.test.001", "TASK.test.002", closed = true),
        )
        write(
            layout.openTasksDirectory.resolve("TASK.test.002.open-cycle.md"),
            task("TASK.test.002", "TASK.test.001", closed = false),
        )

        val codes =
            TaskLedger(layout)
                .load()
                .report.errors
                .map { it.code }

        assertContains(codes, "TASK_CLOSED_DEPENDS_OPEN")
        assertContains(codes, "TASK_DEPENDENCY_CYCLE")
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

        Own the cycle fixture.

        ## Outcomes

        - Invalid cycles are diagnosed.

        ## Boundaries

        - Workflow tests only.

        ## Closure

        ${indented(openReceipt())}
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

        Own the cycle fixture.

        ## Outcomes

        - Cross-lifecycle cycles are rejected.

        ## Exit Criteria

        - [ ] Cycle is diagnosed.

        ## Closure

        ${indented(openReceipt())}
        """.trimIndent() + "\n"

    private fun task(
        ref: String,
        dependency: String,
        closed: Boolean,
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
          - $dependency
        ---

        # $ref: Cycle fixture

        ## Description

        Exercise global dependency validation.

        ## Requirements

        - Diagnose all dependency cycles.

        ## Deliverables

        - [${if (closed) "x" else " "}] Cycle handling is explicit.

        ## Closure

        ${indented(if (closed) closedReceipt() else openReceipt())}
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
        closed_at: 2026-07-19T20:00:00Z
        closed_by: test
        summary: Closed cycle fixture
        verification:
          - workflow unit test
        follow_ups:
          - none
        ```
        """.trimIndent()

    private fun indented(receipt: String): String = receipt.replace("\n", "\n        ")

    private fun write(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(requireNotNull(path.parent))
        Files.writeString(path, content, StandardCharsets.UTF_8)
    }
}
