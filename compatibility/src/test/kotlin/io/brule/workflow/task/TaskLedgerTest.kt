package io.brule.workflow.task

import io.brule.workflow.io.RepositoryLayout
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TaskLedgerTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `doctor reports malformed front matter and markdown shape`() {
        val layout = hierarchy()
        write(
            layout.openTasksDirectory.resolve("TASK.test.001.malformed.md"),
            """
            ---
            kind: task
            schema: loom.agent/v1
            ref: TASK.test.001
            roadmap: ROADMAP.test.001
            impact: HIGH
            effort: LOW
            blocks:
              - TASK.test.002
            depends:
              - not-a-task-ref
            ---

            # TASK.test.001: Malformed task

            ## Description

            Exercise strict validation.

            ## Deliverables

            - This is not a checkbox.

            ## Requirements

            This is not a bullet.

            ## Closure

            ${openReceipt().replace("\n", "\n            ")}
            """.trimIndent() + "\n",
        )

        val codes =
            TaskLedger(layout)
                .load()
                .report
                .errors
                .map { it.code }
                .toSet()

        assertContains(codes, "DOC_FIELD_UNKNOWN")
        assertContains(codes, "DOC_FIELD_ORDER")
        assertContains(codes, "TASK_DEPENDENCY_REF")
        assertContains(codes, "DOC_SECTION_ORDER")
        assertContains(codes, "TASK_REQUIREMENTS_EMPTY")
        assertContains(codes, "TASK_DELIVERABLES_EMPTY")
    }

    @Test
    fun `doctor reports dependencies whose refs do not exist`() {
        val layout = hierarchy()
        writeTask(layout, "TASK.test.001", listOf("TASK.missing.999"))

        val snapshot = TaskLedger(layout).load()

        assertContains(snapshot.report.errors.map { it.code }, "TASK_DEPENDENCY_MISSING")
        assertTrue(!snapshot.report.isValid)
    }

    @Test
    fun `frontier is sorted and excludes tasks blocked by open dependencies`() {
        val layout = hierarchy()
        writeTask(layout, "TASK.test.000", closed = true)
        writeTask(layout, "TASK.test.004", listOf("TASK.test.002", "TASK.test.003"))
        writeTask(layout, "TASK.test.003", listOf("TASK.test.001"))
        writeTask(layout, "TASK.test.002", listOf("TASK.test.000"))
        writeTask(layout, "TASK.test.001")

        val snapshot = TaskLedger(layout).load()

        assertTrue(snapshot.report.isValid, snapshot.report.errors.joinToString { it.message })
        assertEquals(
            listOf(
                listOf("TASK.test.001", "TASK.test.002"),
                listOf("TASK.test.003"),
                listOf("TASK.test.004"),
            ),
            snapshot.graph.layers.map { layer -> layer.map { it.ref.value } },
        )
        assertEquals(
            listOf("TASK.test.001", "TASK.test.002"),
            snapshot.graph.frontier.map { it.ref.value },
        )
    }

    @Test
    fun `realization tasks form the frontier before their aggregate`() {
        val layout = hierarchy()
        writeTask(layout, "TASK.test.000", closed = true)
        writeTask(layout, "TASK.test.001", listOf("TASK.test.000"))
        writeTask(
            layout,
            "TASK.test.002",
            listOf("TASK.test.000"),
            realizes = "TASK.test.001",
        )
        writeTask(
            layout,
            "TASK.test.003",
            listOf("TASK.test.000"),
            realizes = "TASK.test.001",
        )

        val snapshot = TaskLedger(layout).load()

        assertTrue(snapshot.report.isValid, snapshot.report.errors.joinToString { it.message })
        assertEquals(
            listOf(
                listOf("TASK.test.002", "TASK.test.003"),
                listOf("TASK.test.001"),
            ),
            snapshot.graph.layers.map { layer -> layer.map { it.ref.value } },
        )
        assertEquals(
            listOf("TASK.test.002", "TASK.test.003"),
            snapshot
                .derivedRealizations(
                    requireNotNull(
                        io.brule.workflow.model.TaskRef
                            .parse("TASK.test.001"),
                    ),
                ).map { it.value },
        )
    }

    @Test
    fun `doctor rejects realization without aggregate prerequisite closure`() {
        val layout = hierarchy()
        writeTask(layout, "TASK.test.000")
        writeTask(layout, "TASK.test.001", listOf("TASK.test.000"))
        writeTask(layout, "TASK.test.002", realizes = "TASK.test.001")

        val errors = TaskLedger(layout).load().report.errors

        assertContains(errors.map { it.code }, "TASK_REALIZATION_PREREQUISITE")
    }

    @Test
    fun `doctor rejects nested realization ownership`() {
        val layout = hierarchy()
        writeTask(layout, "TASK.test.001")
        writeTask(layout, "TASK.test.002", realizes = "TASK.test.001")
        writeTask(layout, "TASK.test.003", realizes = "TASK.test.002")

        val errors = TaskLedger(layout).load().report.errors

        assertContains(errors.map { it.code }, "TASK_REALIZATION_NESTED")
    }

    @Test
    fun `doctor rejects missing and self realization authority`() {
        val layout = hierarchy()
        writeTask(layout, "TASK.test.001", realizes = "TASK.missing.999")
        writeTask(layout, "TASK.test.002", realizes = "TASK.test.002")

        val codes =
            TaskLedger(layout)
                .load()
                .report
                .errors
                .map { it.code }

        assertContains(codes, "TASK_REALIZATION_MISSING")
        assertContains(codes, "TASK_REALIZATION_SELF")
    }

    @Test
    fun `doctor rejects cross-roadmap and closed-aggregate realization`() {
        val layout = hierarchy()
        write(
            layout.openRoadmapsDirectory.resolve("ROADMAP.test.002.other.md"),
            roadmap("ROADMAP.test.002", 1),
        )
        writeTask(layout, "TASK.test.001", closed = true)
        writeTask(layout, "TASK.test.002", realizes = "TASK.test.001")
        writeTask(
            layout,
            "TASK.test.003",
            realizes = "TASK.test.001",
            roadmapRef = "ROADMAP.test.002",
        )

        val codes =
            TaskLedger(layout)
                .load()
                .report
                .errors
                .map { it.code }

        assertContains(codes, "TASK_REALIZATION_CLOSED_AGGREGATE")
        assertContains(codes, "TASK_REALIZATION_ROADMAP")
    }

    @Test
    fun `doctor rejects a combined dependency and realization cycle`() {
        val layout = hierarchy()
        writeTask(layout, "TASK.test.001")
        writeTask(
            layout,
            "TASK.test.002",
            listOf("TASK.test.001"),
            realizes = "TASK.test.001",
        )

        val codes =
            TaskLedger(layout)
                .load()
                .report.errors
                .map { it.code }

        assertContains(codes, "TASK_DEPENDENCY_CYCLE")
    }

    private fun hierarchy(): RepositoryLayout {
        val layout = RepositoryLayout.at(root)
        write(layout.openEpicsDirectory.resolve("EPIC.test.001.fixture.md"), epic())
        write(layout.openRoadmapsDirectory.resolve("ROADMAP.test.001.fixture.md"), roadmap())
        return layout
    }

    private fun writeTask(
        layout: RepositoryLayout,
        ref: String,
        dependencies: List<String> = emptyList(),
        closed: Boolean = false,
        realizes: String? = null,
        roadmapRef: String = "ROADMAP.test.001",
    ) {
        val directory = if (closed) layout.closedTasksDirectory else layout.openTasksDirectory
        write(directory.resolve("$ref.fixture.md"), task(ref, dependencies, closed, realizes, roadmapRef))
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

        Test hierarchy.

        ## Outcomes

        - A deterministic frontier.

        ## Boundaries

        - Workflow tests only.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun roadmap(
        ref: String = "ROADMAP.test.001",
        ordinal: Int = 0,
    ): String =
        """
        ---
        kind: roadmap
        schema: loom.agent/v1
        ref: $ref
        epic: EPIC.test.001
        ordinal: $ordinal
        ---

        # $ref: Test roadmap

        ## Description

        Test grouping.

        ## Outcomes

        - Valid task ordering.

        ## Exit Criteria

        - [ ] Tests pass.

        ## Closure

        ${indentedOpenReceipt()}
        """.trimIndent() + "\n"

    private fun task(
        ref: String,
        dependencies: List<String>,
        closed: Boolean,
        realizes: String? = null,
        roadmapRef: String = "ROADMAP.test.001",
    ): String =
        """
        ---
        kind: task
        schema: loom.agent/v1
        ref: $ref
        roadmap: $roadmapRef
        effort: LOW
        impact: HIGH
        depends:
        ${dependencies.joinToString("\n        ") { "  - $it" }}
        ${realizes?.let { "realizes: $it" }.orEmpty()}
        ---

        # $ref: Test task

        ## Description

        Exercise dependency ordering.

        ## Requirements

        - Preserve deterministic order.

        ## Deliverables

        - [${if (closed) "x" else " "}] Ordering is verified.

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
        closed_at: 2026-07-19T20:00:00Z
        closed_by: test
        summary: Verified dependency fixture
        verification:
          - workflow unit test
        follow_ups:
          - none
        ```
        """.trimIndent()

    private fun indentedOpenReceipt(): String = openReceipt().replace("\n", "\n        ")

    private fun indentedClosedReceipt(): String = closedReceipt().replace("\n", "\n        ")

    private fun write(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(requireNotNull(path.parent))
        Files.writeString(path, content, StandardCharsets.UTF_8)
    }
}
