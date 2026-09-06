package io.brule.tasking.core

/** Pins identify semantic behavior, including schema-only validators. Merely
 * installing a provider never activates it. A declared profile does. */
data class Profile(val pins: Map<String, String> = emptyMap())
data class Contribution(val prerequisites: List<TaskId> = emptyList(), val blockers: List<String> = emptyList(), val evidenceRequirements: List<String> = emptyList())
data class Receipt(val taskId: TaskId, val contractDigest: ContractDigest, val evidence: Map<String, String>)
interface SemanticProvider {
    val identity: String
    val pin: String
    fun evaluate(record: DraftRecord): Contribution
    fun verify(record: DraftRecord, evidence: Map<String, String>): List<String>
}
/** External probes deliberately have a separate interface and are never called
 * by deterministic evaluation. Results would need explicit evidence binding. */
fun interface EnvironmentProbe { fun probe(): Map<String, String> }

data class DraftEvaluation(
    val records: Map<TaskId, DraftRecord>, val dependencies: Map<TaskId, List<TaskId>>,
    val contributions: Map<TaskId, Contribution>, val problems: List<String>,
) {
    val frontier: List<DraftRecord> get() = if (problems.isNotEmpty()) emptyList() else records.values.filter { task ->
        task.state == "open" && dependencies.getValue(task.id).all { records[it]?.state == "closed" } &&
            contributions.getValue(task.id).blockers.isEmpty()
    }.sortedBy { it.id }
}

object DraftLifecycle {
    fun contract(record: DraftRecord, profile: Profile = Profile()): ContractDigest {
        if (record.protocol == "tasking/core-draft-2") return ContractDigest.parseOrThrow(
            Canonical.digest("taskctl.semantic-contract/2", projection(record, profile)))
        val active = (profile.pins.keys + record.requiredExtensions).sorted()
        return ContractDigest.parseOrThrow(Canonical.digest("tasking/core-draft-1/semantic-contract/1", obj(
            "id" to StringValue(record.id.value), "title" to SemanticMarkdown.value(record.title),
            "intent" to SemanticMarkdown.value(record.intent), "requires" to strings(record.requires.sorted().map { it.value }),
            "requirements" to ArrayValue(record.requirements.map { SemanticMarkdown.value(it) }),
            "acceptance" to ArrayValue(record.acceptance.map { SemanticMarkdown.value(it, acceptance = true) }),
            "required_extensions" to strings(record.requiredExtensions.sorted()), "profile" to stringMap(profile.pins),
            "semantic_extensions" to ObjectValue(active.associateWith { record.extensions.fields[it] ?: NullValue }),
        )))
    }

    /** Versioned public projection. Title is presentation; bounded intent is contractual. */
    fun projection(record: DraftRecord, profile: Profile = Profile()): ObjectValue = obj(
        "id" to StringValue(record.id.value), "intent" to SemanticMarkdown.value(record.intent),
        "requires" to strings(record.requires.sorted().map { it.value }),
        "requirements" to ArrayValue(record.requirements.map { SemanticMarkdown.value(it) }),
        "acceptance" to ArrayValue(record.acceptance.map { SemanticMarkdown.value(it, acceptance = true) }),
        "verification" to strings(record.verification.sorted()),
        "required_extensions" to strings(record.requiredExtensions.sorted()), "profile" to stringMap(profile.pins),
        "semantic_extensions" to ObjectValue((profile.pins.keys + record.requiredExtensions).sorted().associateWith { record.extensions.fields[it] ?: NullValue }),
    )

    /** A matching digest identifies the contract a receipt addresses; this
     * alone does not attest that its supplied evidence is true. */
    fun addresses(receipt: Receipt, record: DraftRecord, profile: Profile = Profile()): Boolean =
        receipt.taskId == record.id && receipt.contractDigest == contract(record, profile)

    fun evaluate(records: List<DraftRecord>, profile: Profile = Profile(),
                 providers: List<SemanticProvider> = emptyList()): DraftEvaluation {
        val errors = mutableListOf<String>()
        if (records.map { it.id }.distinct().size != records.size) return DraftEvaluation(emptyMap(), emptyMap(), emptyMap(), listOf("duplicate task identity"))
        val graph = records.associateBy { it.id }
        val contributed = mutableMapOf<TaskId, Contribution>()
        if (providers.map { it.identity }.distinct().size != providers.size) return DraftEvaluation(graph, emptyMap(), emptyMap(), listOf("duplicate provider identity"))
        val installed = providers.associateBy { it.identity }
        records.forEach { record ->
            val contributions = mutableListOf<Contribution>()
            (profile.pins.keys + record.requiredExtensions).sorted().forEach { identity ->
                val pin = profile.pins[identity]
                val provider = installed[identity]
                if (pin == null || provider == null || provider.pin != pin) {
                    errors += "${record.id}: required semantic provider unavailable or unpinned: $identity"
                } else {
                    try { contributions += provider.evaluate(record) }
                    catch (_: Exception) { errors += "${record.id}: provider evaluation failed: $identity" }
                }
            }
            contributed[record.id] = Contribution(contributions.flatMap { it.prerequisites }.distinct(),
                contributions.flatMap { it.blockers }, contributions.flatMap { it.evidenceRequirements }.distinct())
        }
        val edges = graph.mapValues { (id, record) -> (record.requires + contributed.getValue(id).prerequisites).distinct() }
        edges.forEach { (id, dependencies) -> dependencies.forEach { if (it !in graph) errors += "$id: missing prerequisite: $it" } }
        val visiting = mutableSetOf<TaskId>()
        val visited = mutableSetOf<TaskId>()
        fun visit(id: TaskId) {
            if (id in visited) return
            if (!visiting.add(id)) { errors += "dependency cycle: $id"; return }
            edges[id].orEmpty().filter { it in graph }.forEach(::visit)
            visiting.remove(id); visited.add(id)
        }
        graph.keys.sorted().forEach(::visit)
        return DraftEvaluation(graph, edges, contributed, errors.distinct().sorted())
    }

    fun closureProblems(records: List<DraftRecord>, taskId: TaskId, receipt: Receipt,
                        profile: Profile = Profile(), providers: List<SemanticProvider> = emptyList()): List<String> {
        val evaluation = evaluate(records, profile, providers)
        if (evaluation.problems.any { it == "duplicate task identity" || it == "duplicate provider identity" }) return evaluation.problems
        val graph = evaluation.records
        val edges = evaluation.dependencies
        val contributed = evaluation.contributions
        val task = graph[taskId] ?: return listOf("unknown task: $taskId")
        val errors = evaluation.problems.toMutableList()
        if (task.state != "open") errors += "task is not open"
        edges.getValue(taskId).forEach { if (graph[it]?.state != "closed") errors += "prerequisite not closed: $it" }
        errors += contributed.getValue(taskId).blockers
        if (!addresses(receipt, task, profile)) errors += "receipt does not address current contract"
        if (receipt.evidence.values.none { it.isNotBlank() }) errors += "closure evidence is required"
        task.verification.forEach { if (receipt.evidence[it].isNullOrBlank()) errors += "missing evidence: $it" }
        contributed.getValue(taskId).evidenceRequirements.forEach {
            if (receipt.evidence[it].isNullOrBlank()) errors += "missing evidence: $it"
        }
        (profile.pins.keys + task.requiredExtensions).sorted().forEach { identity ->
            providers.singleOrNull { it.identity == identity }?.takeIf { it.pin == profile.pins[identity] }?.let { provider ->
                try { errors += provider.verify(task, receipt.evidence) }
                catch (_: Exception) { errors += "evidence verification failed: $identity" }
            }
        }
        return errors.distinct().sorted()
    }
}
