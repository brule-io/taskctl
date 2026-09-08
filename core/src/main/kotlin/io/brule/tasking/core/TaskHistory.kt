package io.brule.tasking.core

enum class Currency { CURRENT, AFFECTED, UNRESOLVED }
enum class ReviewOutcome { REVALIDATED, REVISE, SUCCESSOR, UNRESOLVED }

data class Reconciliation(
    val task: TaskId, val reviewedHead: TaskRevisionId, val outcome: ReviewOutcome,
    val observations: List<Dependency>, val actor: String, val time: AssertionTime,
    val rationale: String, val evidence: Map<String, String>, val successor: TaskId? = null, val profile: ProfileDigest? = null,
) {
    init {
        require(actor.isNotBlank() && rationale.isNotBlank())
        require(observations.distinctBy { it.upstream }.size == observations.size)
        require((outcome == ReviewOutcome.SUCCESSOR) == (successor != null))
        require(profile == null || time is OccurredAt) { "profile reviews require validated occurrence time" }
    }
}

data class TaskRevision(val parent: TaskRevisionId?, val record: DraftRecord,
                        val dependencies: List<Dependency>, val review: Reconciliation? = null, val importedFrom: ImportId? = null,
                        val semantics: RevisionSemantics? = null) {
    val protocol: String get() = if (semantics != null) "taskctl.task-revision/3" else if (importedFrom == null) "taskctl.task-revision/1" else "taskctl.task-revision/2"
    val contract: ContractDigest get() = effectiveContract(record, semantics?.profile)
    val prerequisites: List<TaskId> get() = (record.requires + semantics?.contributedPrerequisites.orEmpty()).distinct().sorted()
    val id: TaskRevisionId get() = TaskRevisionId.parseOrThrow(Canonical.digest(protocol, HistoryCodec.revision(this)))
}

/** Explicit origin binds history adoption to its original ledger; it never
 * asserts that old records or receipts were authored under this model. */
data class TaskHistory(val origin: Revision, val heads: Map<TaskId, TaskRevisionId> = emptyMap(),
                       val revisions: Map<TaskRevisionId, TaskRevision> = emptyMap()) {
    fun head(task: TaskId): TaskRevision = revisions.getValue(heads.getValue(task))
    fun append(revision: TaskRevision): TaskHistory = copy(
        heads = heads + (revision.record.id to revision.id), revisions = revisions + (revision.id to revision))
}

data class CurrencyCause(val path: List<TaskId>, val reason: String,
                         val observed: Dependency? = null, val current: Dependency? = null)
data class TaskCurrency(val task: TaskId, val state: Currency, val causes: List<CurrencyCause>)

object CurrencyEvaluation {
    /** Semantic input identities, independent of presentation and lifecycle.
     * The graph is validated before recursion; memoization bounds DAG traversal. */
    fun observations(snapshot: LedgerSnapshot): Map<TaskId, Dependency> {
        val graph = snapshot.universe.tasks.associateBy { it.id }
        val evaluation = snapshot.effectiveEvaluation()
        require(evaluation.problems.filterNot(::providerUnavailable).isEmpty()) { "invalid prerequisite graph" }
        val memo = mutableMapOf<TaskId, Dependency>()
        fun observe(id: TaskId): Dependency = memo.getOrPut(id) {
            val task = graph.getValue(id)
            val contract = snapshot.effectiveContract(task)
            val inputs = evaluation.dependencies.getValue(id).sorted().associateWith { observe(it).observedInputs }
            val unavailable = snapshot.profileHistory != null && evaluation.problems.any { it.startsWith("$id:") && providerUnavailable(it) }
            val basis = if (unavailable || inputs.values.any { it == null }) null else InputDigest.parseOrThrow(Canonical.digest("taskctl.transitive-input/1", obj(
                "contract" to StringValue(contract.value),
                "inputs" to ObjectValue(inputs.entries.associate { it.key.value to StringValue(requireNotNull(it.value).value) }),
            )))
            Dependency(id, contract, snapshot.history?.heads?.get(id), basis)
        }
        graph.keys.sorted().forEach(::observe)
        return memo.toMap()
    }

    fun evaluate(snapshot: LedgerSnapshot): Map<TaskId, TaskCurrency> {
        val current = observations(snapshot)
        val graph = snapshot.universe.tasks.associateBy { it.id }
        val evaluation = snapshot.effectiveEvaluation()
        val memo = mutableMapOf<TaskId, TaskCurrency>()
        fun witnesses(causes: List<CurrencyCause>): List<CurrencyCause> = causes
            .sortedWith(compareBy<CurrencyCause> { it.path.size }.thenBy { it.path.joinToString("/") })
            .distinctBy { it.copy(path = listOf(it.path.last())) }
        val differenceMemo = mutableMapOf<Dependency, List<CurrencyCause>>()
        fun differences(edge: Dependency, visiting: Set<TaskRevisionId> = emptySet()): List<CurrencyCause> {
            differenceMemo[edge]?.let { return it }
            val now = current[edge.upstream] ?: return listOf(CurrencyCause(listOf(edge.upstream), "historically observed dependency is no longer in the current graph", edge))
            if (edge.observedContract == now.observedContract && edge.observedInputs == now.observedInputs) return emptyList()
            val reason = if (edge.observedContract != now.observedContract) "observed upstream contract changed" else "observed upstream semantic inputs changed"
            val direct = CurrencyCause(listOf(edge.upstream), reason, edge, now)
            val observedRevision = edge.observedRevision ?: return listOf(direct)
            if (observedRevision in visiting) return listOf(direct)
            val old = snapshot.history?.revisions?.get(observedRevision) ?: return listOf(direct)
            val result = witnesses(listOf(direct) + old.dependencies.sortedBy { it.upstream }.flatMap { upstream ->
                differences(upstream, visiting + observedRevision).map { it.copy(path = listOf(edge.upstream) + it.path) }
            })
            differenceMemo[edge] = result
            return result
        }
        fun inspect(id: TaskId): TaskCurrency = memo.getOrPut(id) {
            val task = graph.getValue(id)
            val causes = mutableListOf<CurrencyCause>()
            var unresolved = false
            val previousEdges = snapshot.dependencies(id)
            val effectiveEdges = evaluation.dependencies.getValue(id)
            if (previousEdges.map { it.upstream }.toSet() != effectiveEdges.toSet()) causes += CurrencyCause(listOf(id), "effective prerequisite set changed; explicit review required")
            if (current.getValue(id).observedInputs == null) {
                unresolved = true
                causes += CurrencyCause(listOf(id), "required provider or upstream evaluation unavailable; semantic inputs are unknown")
            }
            effectiveEdges.sorted().forEach { upstreamId ->
                val edge = previousEdges.singleOrNull { it.upstream == upstreamId } ?: Dependency(upstreamId)
                val now = current.getValue(edge.upstream)
                if (edge.observedContract == null || edge.observedInputs == null || now.observedInputs == null) {
                    unresolved = true
                    causes += CurrencyCause(listOf(id, edge.upstream), "dependency has no recorded semantic observation", edge, now)
                } else if (edge.observedContract != now.observedContract || edge.observedInputs != now.observedInputs) {
                    causes += differences(edge).map { it.copy(path = listOf(id) + it.path) }
                }
                val upstream = inspect(edge.upstream)
                if (upstream.state != Currency.CURRENT) {
                    if (upstream.state == Currency.UNRESOLVED) unresolved = true
                    causes += upstream.causes.map { it.copy(path = listOf(id) + it.path) }
                }
            }
            val head = snapshot.history?.heads?.get(id)?.let { snapshot.history.revisions.getValue(it) }
            if (head != null && head.contract != snapshot.effectiveContract(task)) {
                causes += CurrencyCause(listOf(id), "effective semantic profile changed; explicit review required")
            }
            if (head?.review?.outcome in setOf(ReviewOutcome.REVISE, ReviewOutcome.SUCCESSOR, ReviewOutcome.UNRESOLVED)) {
                unresolved = true
                causes += CurrencyCause(listOf(id), "review requires ${head?.review?.outcome?.name?.lowercase()}: ${head?.review?.rationale}")
            }
            if (task.state == "closed" && snapshot.receipts.none { it.receipt.taskId == task.id && it.receipt.contractDigest == snapshot.effectiveContract(task) } && head?.review?.outcome != ReviewOutcome.REVALIDATED) {
                causes += CurrencyCause(listOf(id), "historical closure does not prove the HEAD contract")
            }
            // Keep one deterministic witness path per cause, not exponentially
            // many paths through a diamond-shaped prerequisite graph.
            TaskCurrency(id, if (unresolved) Currency.UNRESOLVED else if (causes.isNotEmpty()) Currency.AFFECTED else Currency.CURRENT, witnesses(causes))
        }
        graph.keys.sorted().forEach(::inspect)
        return memo.toMap()
    }
}

fun LedgerSnapshot.currency(): Map<TaskId, TaskCurrency> = CurrencyEvaluation.evaluate(this)

fun LedgerSnapshot.closureProblems(evidence: ClosureEvidence): List<String> =
    dependencyProblems() + profiledClosureProblems(evidence) +
        if (history != null && currency()[evidence.receipt.taskId]?.state != Currency.CURRENT) listOf("task currency is not current; reconcile its inputs before closure") else emptyList()

fun TaskHistory.validate(universe: DraftUniverse, receipts: List<ClosureEvidence>, imports: List<ImportAdmission> = emptyList()) {
    val history = this
    require(history.heads.keys == universe.tasks.map { it.id }.toSet()) { "task identities and history HEADs disagree" }
    history.revisions.forEach { (id, value) ->
        require(id == value.id) { "revision identity mismatch" }
        value.importedFrom?.let { origin ->
            val manifest = imports.singleOrNull { it.manifest.id == origin }?.manifest ?: error("import manifest absent from history")
            val projected = manifest.universe.tasks.singleOrNull { it.id == value.record.id } ?: error("imported identity absent from manifest")
            require(DraftLifecycle.contract(projected) == DraftLifecycle.contract(value.record)) { "import projection contract mismatch" }
        }
        require(value.dependencies.map { it.upstream }.toSet() == value.prerequisites.toSet() && value.dependencies.distinctBy { it.upstream }.size == value.dependencies.size) { "revision dependencies disagree" }
        value.parent?.let { parent -> require(history.revisions[parent]?.record?.id == value.record.id) { "missing or wrong task revision parent" } }
        value.dependencies.forEach { edge -> edge.observedRevision?.let { observed ->
            val upstream = history.revisions[observed] ?: error("observed revision absent from history")
            require(upstream.record.id == edge.upstream && upstream.contract == edge.observedContract) { "observed revision/contract identity mismatch" }
        } }
    }
    universe.tasks.forEach { task ->
        val head = history.head(task.id)
        require(head.record.id == task.id && head.record.state == task.state && head.contract == effectiveContract(task, head.semantics?.profile)) {
            "task projection differs from recorded HEAD: ${task.id}; restore it and use revise --file with CAS"
        }
        val ancestors = mutableSetOf<TaskRevisionId>()
        var current: TaskRevision? = head
        while (current != null) {
            require(ancestors.add(current.id)) { "revision parent cycle" }
            current = current.parent?.let { history.revisions.getValue(it) }
        }
        if (task.state == "closed") require(ancestors.any { revision ->
            val value = history.revisions.getValue(revision)
            val historical = value.record
            historical.state == "closed" && (receipts.any { it.receipt.taskId == historical.id && it.receipt.contractDigest == value.contract } ||
                imports.any { it.manifest.id == value.importedFrom && it.manifest.universe.tasks.any { task ->
                    task.id == historical.id && task.state == "closed" && DraftLifecycle.contract(task) == DraftLifecycle.contract(historical)
                } })
        }) { "closed task lacks historical closure evidence: ${task.id}" }
    }
}
