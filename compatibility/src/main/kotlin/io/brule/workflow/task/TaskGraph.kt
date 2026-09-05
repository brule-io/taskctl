package io.brule.workflow.task

import io.brule.workflow.diagnostics.Diagnostic
import io.brule.workflow.model.TaskRef
import java.util.PriorityQueue

public data class TaskGraphAnalysis(
    public val layers: List<List<TaskDocument>>,
    public val cyclicRefs: List<TaskRef>,
) {
    public val frontier: List<TaskDocument> = layers.firstOrNull().orEmpty()
}

public object TaskGraph {
    public fun analyze(openTasks: Collection<TaskDocument>): TaskGraphAnalysis {
        val tasksByRef = openTasks.associateBy { it.ref }
        val traversal = traverse(tasksByRef)
        val cyclicRefs = (tasksByRef.keys - traversal.seen).sorted()
        return TaskGraphAnalysis(traversal.layers, cyclicRefs)
    }

    private fun traverse(tasksByRef: Map<TaskRef, TaskDocument>): GraphTraversal {
        val indegree = tasksByRef.keys.associateWith { 0 }.toMutableMap()
        val outgoing = tasksByRef.keys.associateWith { mutableListOf<TaskRef>() }.toMutableMap()

        tasksByRef.values.forEach { task ->
            task.depends.filter { it in tasksByRef }.forEach { dependency ->
                indegree[task.ref] = indegree.getValue(task.ref) + 1
                outgoing.getValue(dependency) += task.ref
            }
            task.realizes?.takeIf { it in tasksByRef }?.let { aggregate ->
                indegree[aggregate] = indegree.getValue(aggregate) + 1
                outgoing.getValue(task.ref) += aggregate
            }
        }
        outgoing.values.forEach { refs -> refs.sort() }

        var ready =
            PriorityQueue<TaskRef>().apply {
                addAll(indegree.filterValues { it == 0 }.keys)
            }
        val layers = mutableListOf<List<TaskDocument>>()
        val seen = mutableSetOf<TaskRef>()
        while (ready.isNotEmpty()) {
            val currentRefs =
                buildList {
                    while (ready.isNotEmpty()) add(ready.remove())
                }
            layers += currentRefs.map { tasksByRef.getValue(it) }.sortedWith(TaskDocument.ORDER)
            seen += currentRefs
            val next = PriorityQueue<TaskRef>()
            currentRefs.forEach { ref ->
                outgoing.getValue(ref).forEach { dependent ->
                    val remaining = indegree.getValue(dependent) - 1
                    indegree[dependent] = remaining
                    if (remaining == 0) next += dependent
                }
            }
            ready = next
        }
        return GraphTraversal(layers, seen)
    }

    public fun diagnostics(openTasks: Collection<TaskDocument>): List<Diagnostic> {
        val analysis = analyze(openTasks)
        if (analysis.cyclicRefs.isEmpty()) return emptyList()
        return listOf(
            Diagnostic(
                "TASK_DEPENDENCY_CYCLE",
                "task readiness cycle contains: ${analysis.cyclicRefs.joinToString { it.value }}",
            ),
        )
    }

    private data class GraphTraversal(
        val layers: List<List<TaskDocument>>,
        val seen: Set<TaskRef>,
    )
}
