package io.brule.tasking.core

/** Internal transport-neutral seam. No paths, locks, clocks or HTTP types. */
interface TaskLedger {
    fun snapshot(): LedgerSnapshot
    fun task(id: TaskId): DraftRecord? = snapshot().universe.tasks.singleOrNull { it.id == id }
    fun frontier(query: FrontierQuery = FrontierQuery()): Frontier {
        val snapshot = snapshot()
        require(snapshot.dependencyProblems().isEmpty()) { snapshot.dependencyProblems().joinToString("\n") }
        val currency = snapshot.currency()
        return Frontier(snapshot.revision, snapshot.universe.frontier(query.roadmap, query.epic).filter {
            snapshot.history == null || currency.getValue(it).state == Currency.CURRENT
        })
    }
    fun apply(expectedRevision: Revision, transition: Transition): TransitionResult
}

data class LedgerSnapshot(val repositoryId: String, val revision: Revision, val universe: DraftUniverse,
                          val receipts: List<ClosureEvidence> = emptyList(),
                          val dependencyBindings: Map<TaskId, List<Dependency>> = emptyMap(),
                          val history: TaskHistory? = null)
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
    data class ReviseTask(val record: DraftRecord) : Transition
    data class ReconcileTask(val review: Reconciliation) : Transition
    data object TrackHistory : Transition
}

/** Every adapter uses this reducer. Storage only persists the validated result. */
object LedgerTransitions {
    fun reduce(snapshot: LedgerSnapshot, transition: Transition): DraftUniverse {
        snapshot.history?.validate(snapshot.universe, snapshot.receipts)
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
                val errors = snapshot.closureProblems(evidence)
                require(errors.isEmpty()) { errors.joinToString("\n") }
                universe.copy(tasks = universe.tasks.map { if (it.id == evidence.receipt.taskId) it.copy(state = "closed") else it })
            }
            is Transition.ReviseTask -> {
                require(snapshot.history != null) { "track history before revising" }
                val previous = universe.tasks.single { it.id == transition.record.id }
                require(previous.state == transition.record.state) { "revision cannot change lifecycle" }
                universe.copy(tasks = universe.tasks.map { if (it.id == previous.id) transition.record else it })
            }
            is Transition.ReconcileTask -> { validateReview(snapshot, transition.review); universe }
            Transition.TrackHistory -> { require(snapshot.history == null) { "history already tracked" }; universe }
        }
        val errors = updated.indexProblems() + DraftLifecycle.evaluate(updated.tasks).problems
        // Missing capability implementations must not prevent storing/inspecting
        // typed data, but they never confer readiness or closure authority.
        require(errors.filterNot { "required semantic provider unavailable" in it }.isEmpty()) { errors.joinToString("\n") }
        return updated
    }

    fun evolve(snapshot: LedgerSnapshot, transition: Transition): LedgerSnapshot {
        val universe = reduce(snapshot, transition)
        var history = snapshot.history
        if (transition == Transition.TrackHistory) {
            history = TaskHistory(snapshot.revision)
            snapshot.universe.tasks.sortedBy { it.id }.forEach { task ->
                history = requireNotNull(history).append(TaskRevision(null, task, task.requires.map { Dependency(it) }))
            }
        } else if (history != null) {
            val observations = CurrencyEvaluation.observations(snapshot.copy(universe = universe))
            when (transition) {
                is Transition.AddRecords -> {
                    // Construct upstream revisions first so observed revision IDs exist.
                    val pending = transition.tasks.associateBy { it.id }
                    fun add(task: DraftRecord) {
                        if (task.id in requireNotNull(history).heads) return
                        task.requires.mapNotNull { pending[it] }.sortedBy { it.id }.forEach(::add)
                        val edges = task.requires.sorted().map { observations.getValue(it).copy(observedRevision = requireNotNull(history).heads[it]) }
                        history = requireNotNull(history).append(TaskRevision(null, task, edges))
                    }
                    transition.tasks.sortedBy { it.id }.forEach(::add)
                }
                is Transition.CloseTask -> {
                    val id = transition.evidence.receipt.taskId
                    val old = history.head(id)
                    history = history.append(old.copy(parent = old.id, record = universe.tasks.single { it.id == id }))
                }
                is Transition.ReviseTask -> {
                    val record = transition.record
                    val old = history.head(record.id)
                    val review = if (DraftLifecycle.contract(old.record) == DraftLifecycle.contract(record)) old.review else null
                    history = history.append(TaskRevision(old.id, record, record.requires.sorted().map { id -> old.dependencies.singleOrNull { it.upstream == id } ?: Dependency(id) }, review))
                }
                is Transition.ReconcileTask -> {
                    val review = transition.review
                    val old = history.head(review.task)
                    history = history.append(old.copy(parent = old.id,
                        dependencies = if (review.outcome == ReviewOutcome.REVALIDATED) review.observations else old.dependencies, review = review))
                }
                Transition.TrackHistory -> error("handled above")
            }
        }
        return snapshot.copy(universe = universe, history = history,
            receipts = snapshot.receipts + if (transition is Transition.CloseTask) listOf(transition.evidence) else emptyList())
    }

    private fun validateReview(snapshot: LedgerSnapshot, review: Reconciliation) {
        val history = requireNotNull(snapshot.history) { "track history before reconciliation" }
        require(history.heads[review.task] == review.reviewedHead) { "review does not address task HEAD" }
        val task = snapshot.universe.tasks.single { it.id == review.task }
        val now = CurrencyEvaluation.observations(snapshot)
        require(review.observations.sortedBy { it.upstream } == task.requires.sorted().map { now.getValue(it) }) { "review observations do not match current upstream revisions/contracts/inputs" }
        if (review.outcome == ReviewOutcome.REVALIDATED) {
            val currency = snapshot.currency()
            require(task.requires.all { currency.getValue(it).state == Currency.CURRENT }) { "upstream currency must be current before revalidation" }
            require(task.requiredExtensions.isEmpty()) { "required semantic provider unavailable for revalidation" }
            require(review.evidence.values.any { it.isNotBlank() }) { "revalidation evidence is required" }
            require(task.verification.all { !review.evidence[it].isNullOrBlank() }) { "missing required revalidation evidence" }
        }
        review.successor?.let { id ->
            require(id != task.id && snapshot.universe.tasks.any { it.id == id && it.state == "open" }) { "successor must identify distinct existing open work" }
        }
    }
}
