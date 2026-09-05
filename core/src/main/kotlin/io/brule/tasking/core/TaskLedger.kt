package io.brule.tasking.core

/** Internal transport-neutral seam. No paths, locks, clocks or HTTP types. */
interface TaskLedger {
    fun snapshot(): LedgerSnapshot
    fun task(id: TaskId): DraftRecord? = snapshot().universe.tasks.singleOrNull { it.id == id }
    fun frontier(query: FrontierQuery = FrontierQuery()): Frontier {
        val snapshot = snapshot()
        require(snapshot.dependencyProblems().isEmpty()) { snapshot.dependencyProblems().joinToString("\n") }
        return Frontier(snapshot.revision, snapshot.universe.frontier(query.roadmap, query.epic))
    }
    fun apply(expectedRevision: Revision, transition: Transition): TransitionResult
}

data class LedgerSnapshot(val repositoryId: String, val revision: Revision, val universe: DraftUniverse,
                          val receipts: List<ClosureEvidence> = emptyList(),
                          val dependencyBindings: Map<TaskId, List<Dependency>> = emptyMap())
data class FrontierQuery(val roadmap: RoadmapId? = null, val epic: EpicId? = null)
data class Frontier(val revision: Revision, val tasks: List<TaskId>)
data class TransitionResult(val revision: Revision, val changed: List<RecordId>)
class RevisionConflict(message: String) : IllegalStateException(message)

/** Actor-supplied assertions are labeled honestly; this is not an attestation
 * that taskctl ran tests or independently proved acceptance. */
data class ClosureEvidence(val receipt: Receipt, val actor: String, val recordedAt: String) {
    init { require(actor.isNotBlank() && recordedAt.isNotBlank()) }
}

sealed interface Transition {
    data class AddRecords(val tasks: List<DraftRecord> = emptyList(), val roadmaps: List<DraftRoadmap> = emptyList(),
                          val epics: List<DraftEpic> = emptyList()) : Transition
    data class CloseTask(val evidence: ClosureEvidence) : Transition
}

/** Every adapter uses this reducer. Storage only persists the validated result. */
object LedgerTransitions {
    fun reduce(snapshot: LedgerSnapshot, transition: Transition): DraftUniverse {
        require(snapshot.dependencyProblems().isEmpty()) { snapshot.dependencyProblems().joinToString("\n") }
        val universe = snapshot.universe
        val updated = when (transition) {
            is Transition.AddRecords -> {
                require(transition.tasks.all { it.state == "open" }) { "new tasks must be open; use the closure transition for evidence" }
                universe.copy(tasks = universe.tasks + transition.tasks, roadmaps = universe.roadmaps + transition.roadmaps,
                    epics = universe.epics + transition.epics)
            }
            is Transition.CloseTask -> {
                val evidence = transition.evidence
                val errors = universe.closureProblems(evidence.receipt.taskId, evidence.receipt)
                require(errors.isEmpty()) { errors.joinToString("\n") }
                universe.copy(tasks = universe.tasks.map { if (it.id == evidence.receipt.taskId) it.copy(state = "closed") else it })
            }
        }
        val errors = updated.indexProblems() + DraftLifecycle.evaluate(updated.tasks).problems
        // Missing capability implementations must not prevent storing/inspecting
        // typed data, but they never confer readiness or closure authority.
        require(errors.filterNot { "required semantic provider unavailable" in it }.isEmpty()) { errors.joinToString("\n") }
        return updated
    }
}
