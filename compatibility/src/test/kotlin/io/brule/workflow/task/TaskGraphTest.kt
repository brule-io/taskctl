package io.brule.workflow.task

import io.brule.workflow.document.FrontMatterDocument
import io.brule.workflow.document.MarkdownDocument
import io.brule.workflow.model.RoadmapRef
import io.brule.workflow.model.TaskRef
import java.nio.file.Path
import kotlin.test.assertEquals

internal class TaskGraphTest {
    @kotlin.test.Test
    fun `kahn analysis emits deterministic simultaneous layers`() {
        val rootA = task("TASK.test.001")
        val rootB = task("TASK.test.002")
        val join = task("TASK.test.003", listOf(rootA.ref, rootB.ref))
        val leaf = task("TASK.test.004", listOf(join.ref))

        val analysis = TaskGraph.analyze(listOf(leaf, rootB, join, rootA))

        assertEquals(
            listOf(
                listOf("TASK.test.001", "TASK.test.002"),
                listOf("TASK.test.003"),
                listOf("TASK.test.004"),
            ),
            analysis.layers.map { layer -> layer.map { it.ref.value } },
        )
        assertEquals(emptyList(), analysis.cyclicRefs)
    }

    @kotlin.test.Test
    fun `kahn analysis retains cyclic vertices for diagnostics`() {
        val firstRef = requireNotNull(TaskRef.parse("TASK.test.001"))
        val secondRef = requireNotNull(TaskRef.parse("TASK.test.002"))
        val analysis =
            TaskGraph.analyze(
                listOf(
                    task(firstRef.value, listOf(secondRef)),
                    task(secondRef.value, listOf(firstRef)),
                ),
            )

        assertEquals(emptyList(), analysis.layers)
        assertEquals(listOf(firstRef, secondRef), analysis.cyclicRefs)
    }

    @kotlin.test.Test
    fun `parallel realizations precede their aggregate without rewriting its dependencies`() {
        val prerequisite = task("TASK.test.001")
        val aggregate = task("TASK.test.002", listOf(prerequisite.ref))
        val first = task("TASK.test.003", listOf(prerequisite.ref), aggregate.ref)
        val second = task("TASK.test.004", listOf(prerequisite.ref), aggregate.ref)

        val analysis = TaskGraph.analyze(listOf(aggregate, second, first))

        assertEquals(
            listOf(
                listOf("TASK.test.003", "TASK.test.004"),
                listOf("TASK.test.002"),
            ),
            analysis.layers.map { layer -> layer.map { it.ref.value } },
        )
        assertEquals(listOf(prerequisite.ref), aggregate.depends)
    }

    @kotlin.test.Test
    fun `realization edges expose combined graph cycles for rejection`() {
        val aggregate = task("TASK.test.001")
        val realization = task("TASK.test.002", listOf(aggregate.ref), aggregate.ref)

        val analysis = TaskGraph.analyze(listOf(realization, aggregate))

        assertEquals(emptyList(), analysis.layers)
        assertEquals(listOf(aggregate.ref, realization.ref), analysis.cyclicRefs)
    }

    private fun task(
        value: String,
        dependencies: List<TaskRef> = emptyList(),
        realizes: TaskRef? = null,
    ): TaskDocument {
        val ref = requireNotNull(TaskRef.parse(value))
        val path = Path.of("$value.example.md")
        return TaskDocument(
            path = path,
            state = LedgerState.OPEN,
            ref = ref,
            roadmap = requireNotNull(RoadmapRef.parse("ROADMAP.test.001")),
            effort = Effort.MEDIUM,
            impact = Impact.MEDIUM,
            depends = dependencies,
            realizes = realizes,
            title = value,
            frontMatter = FrontMatterDocument(path, emptyMap(), "", "", 1),
            markdown = MarkdownDocument(value, emptyMap(), emptyList()),
        )
    }
}
