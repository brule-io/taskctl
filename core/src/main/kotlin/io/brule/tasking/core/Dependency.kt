package io.brule.tasking.core

/** The upstream identity is durable; the contract it was observed to satisfy
 * can change independently. Null is explicitly unbound, never "current forever". */
@JvmInline value class ContractDigest(val value: String) { init { require(value.isNotBlank()) } }
data class Dependency(val upstream: TaskId, val observedContract: ContractDigest? = null)

/** Identity-only persisted alpha edges remain unbound. An adapter can supply
 * bindings without changing graph/lifecycle consumers or inventing old evidence. */
fun LedgerSnapshot.dependencies(task: TaskId): List<Dependency> {
    val record = universe.tasks.singleOrNull { it.id == task.value } ?: error("unknown task: ${task.value}")
    return dependencyBindings[task] ?: record.requires.map { Dependency(TaskId(it)) }
}

fun LedgerSnapshot.dependencyProblems(): List<String> {
    val tasks = universe.tasks.associateBy { it.id }
    val errors = mutableListOf<String>()
    dependencyBindings.forEach { (dependent, edges) ->
        val task = tasks[dependent.value]
        if (task == null) errors += "dependency bindings reference unknown task: ${dependent.value}"
        else if (edges.map { it.upstream.value }.toSet() != task.requires.toSet() || edges.distinctBy { it.upstream }.size != edges.size) {
            errors += "dependency bindings disagree with prerequisites: ${dependent.value}"
        }
        edges.forEach { edge ->
            val upstream = tasks[edge.upstream.value]
            if (upstream == null) errors += "unknown dependency: ${edge.upstream.value}"
            else if (edge.observedContract != null && edge.observedContract.value != DraftLifecycle.contract(upstream)) {
                errors += "${dependent.value}: observed upstream contract changed: ${edge.upstream.value}"
            }
        }
    }
    return errors.sorted()
}
