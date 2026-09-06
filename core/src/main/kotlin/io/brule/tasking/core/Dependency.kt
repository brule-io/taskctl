package io.brule.tasking.core

/** The upstream identity is durable; the contract it was observed to satisfy
 * can change independently. Null is explicitly unbound, never "current forever". */
data class Dependency(val upstream: TaskId, val observedContract: ContractDigest? = null,
                      val observedRevision: TaskRevisionId? = null, val observedInputs: InputDigest? = null)

/** Identity-only persisted alpha edges remain unbound. An adapter can supply
 * bindings without changing graph/lifecycle consumers or inventing old evidence. */
fun LedgerSnapshot.dependencies(task: TaskId): List<Dependency> {
    val record = universe.tasks.singleOrNull { it.id == task } ?: error("unknown task: ${task.value}")
    return history?.heads?.get(task)?.let { history.revisions.getValue(it).dependencies }
        ?: dependencyBindings[task] ?: record.requires.map { Dependency(it) }
}

fun LedgerSnapshot.dependencyProblems(): List<String> {
    val tasks = universe.tasks.associateBy { it.id }
    val errors = mutableListOf<String>()
    dependencyBindings.forEach { (dependent, edges) ->
        val task = tasks[dependent]
        if (task == null) errors += "dependency bindings reference unknown task: ${dependent.value}"
        else if (edges.map { it.upstream }.toSet() != task.requires.toSet() || edges.distinctBy { it.upstream }.size != edges.size) {
            errors += "dependency bindings disagree with prerequisites: ${dependent.value}"
        }
        edges.forEach { edge ->
            val upstream = tasks[edge.upstream]
            if (upstream == null) errors += "unknown dependency: ${edge.upstream.value}"
        }
    }
    return errors.sorted()
}
