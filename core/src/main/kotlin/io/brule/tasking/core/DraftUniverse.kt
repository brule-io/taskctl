package io.brule.tasking.core

/** Each association is authored once: roadmap/epic -> task IDs. Reverse and
 * cross-planning indexes are derived. No roadmap -> epic ownership is stored. */
data class DraftUniverse(
    val tasks: List<DraftRecord>,
    val roadmaps: List<DraftRoadmap> = emptyList(),
    val epics: List<DraftEpic> = emptyList(),
) {
    fun indexProblems(): List<String> {
        val errors = mutableListOf<String>()
        if (tasks.map { it.id }.distinct().size != tasks.size) errors += "duplicate task identity"
        if (roadmaps.map { it.id }.distinct().size != roadmaps.size) errors += "duplicate roadmap identity"
        if (epics.map { it.id }.distinct().size != epics.size) errors += "duplicate epic identity"
        val taskIds = tasks.map { it.id }.toSet()
        roadmaps.forEach { roadmap -> roadmap.tasks.filter { it !in taskIds }.forEach {
            errors += "${roadmap.id.value}: missing task member: ${it.value}"
        } }
        epics.forEach { epic -> epic.tasks.filter { it !in taskIds }.forEach {
            errors += "${epic.id.value}: missing task association: ${it.value}"
        } }
        return errors.sorted()
    }

    fun roadmapsFor(task: TaskId): List<RoadmapId> {
        requireTask(task)
        return roadmaps.filter { task in it.tasks }.map { it.id }.sortedBy { it.value }
    }
    fun epicsFor(task: TaskId): List<EpicId> {
        requireTask(task)
        return epics.filter { task in it.tasks }.map { it.id }.sortedBy { it.value }
    }
    fun epicsFor(roadmap: RoadmapId): List<EpicId> {
        val members = requireRoadmap(roadmap).tasks.toSet()
        return epics.filter { epic -> epic.tasks.any { it in members } }.map { it.id }.sortedBy { it.value }
    }
    fun roadmapsFor(epic: EpicId): List<RoadmapId> {
        val members = requireEpic(epic).tasks.toSet()
        return roadmaps.filter { roadmap -> roadmap.tasks.any { it in members } }.map { it.id }.sortedBy { it.value }
    }

    /** Evaluate prerequisites and capability predicates over the WHOLE task
     * universe before applying planning selections. Order is presentation only. */
    fun frontier(roadmap: RoadmapId? = null, epic: EpicId? = null,
                 profile: Profile = Profile(), providers: List<SemanticProvider> = emptyList()): List<TaskId> {
        requireValidIndexes()
        val selectedRoadmap = roadmap?.let(::requireRoadmap)
        val selectedEpic = epic?.let(::requireEpic)
        val requiredPlanning = (roadmaps.flatMap { it.requiredExtensions } + epics.flatMap { it.requiredExtensions }).distinct()
        require(requiredPlanning.isEmpty()) { "required planning providers are not implemented in this draft: $requiredPlanning" }
        val evaluation = DraftLifecycle.evaluate(tasks, profile, providers)
        require(evaluation.problems.isEmpty()) { evaluation.problems.joinToString("\n") }
        val ready = evaluation.frontier.map { it.id }.toSet()
        val order = selectedRoadmap?.tasks ?: tasks.map { it.id }.sortedBy { it.value }
        return order.filter { it in ready && (selectedEpic == null || it in selectedEpic.tasks) }
    }

    /** Inspection of authored prerequisite boundaries, not a readiness grant.
     * Provider-contributed prerequisites are evaluated by frontier/closure. */
    fun prerequisitesOutside(roadmap: RoadmapId): Map<TaskId, List<TaskId>> {
        val members = requireRoadmap(roadmap).tasks.toSet()
        return tasks.filter { it.id in members }.sortedBy { it.id }.associate { task ->
            task.id to task.requires.filter { it !in members }.sortedBy { it.value }
        }.filterValues { it.isNotEmpty() }
    }

    fun closureProblems(task: TaskId, receipt: Receipt, profile: Profile = Profile(),
                        providers: List<SemanticProvider> = emptyList()): List<String> {
        val planningProblems = indexProblems() + (roadmaps.flatMap { it.requiredExtensions } + epics.flatMap { it.requiredExtensions })
            .distinct().map { "required planning provider unavailable in this draft: $it" }
        return (planningProblems + DraftLifecycle.closureProblems(tasks, task, receipt, profile, providers)).distinct().sorted()
    }

    /** Planning membership/order/scope changes invalidate the universe view,
     * without inventing a change to a member task's executable contract. */
    fun snapshotDigest(profile: Profile = Profile()): String {
        requireValidIndexes()
        fun planning(record: PlanningRecord): ObjectValue {
            val encoded = PlanningRecordCodec.encode(record)
            val contentKey = when (record) { is DraftRoadmap -> "intent"; is DraftEpic -> "scope" }
            val membership = when (record) { is DraftRoadmap -> record.tasks; is DraftEpic -> record.tasks.sortedBy { it.value } }
            return ObjectValue(encoded.fields + mapOf(
                "title" to SemanticMarkdown.value(record.title),
                contentKey to SemanticMarkdown.value(encoded.requiredString(contentKey)),
                "tasks" to strings(membership.map { it.value }),
                "required_extensions" to strings(record.requiredExtensions.sorted()),
            ))
        }
        return Canonical.digest("tasking/universe-draft-1/snapshot/1", obj(
            "tasks" to ArrayValue(tasks.sortedBy { it.id }.map { task -> obj(
                "id" to StringValue(task.id.value), "state" to StringValue(task.state),
                "contract" to StringValue(DraftLifecycle.contract(task, profile).value), "extensions" to task.extensions,
            ) }),
            "roadmaps" to ArrayValue(roadmaps.sortedBy { it.id.value }.map(::planning)),
            "epics" to ArrayValue(epics.sortedBy { it.id.value }.map(::planning)),
        ))
    }

    private fun requireValidIndexes() { require(indexProblems().isEmpty()) { indexProblems().joinToString("\n") } }
    private fun requireTask(id: TaskId): DraftRecord {
        requireValidIndexes()
        return tasks.singleOrNull { it.id == id } ?: error("unknown task: ${id.value}")
    }
    private fun requireRoadmap(id: RoadmapId): DraftRoadmap {
        requireValidIndexes()
        return roadmaps.singleOrNull { it.id == id } ?: error("unknown roadmap: ${id.value}")
    }
    private fun requireEpic(id: EpicId): DraftEpic {
        requireValidIndexes()
        return epics.singleOrNull { it.id == id } ?: error("unknown epic: ${id.value}")
    }
}
