package io.brule.tasking.cli

import io.brule.tasking.core.*

/** Pure application projections. Presentation limits never change core readiness,
 * contracts or storage, and no projection performs an external probe. */
internal object LedgerReadModels {
    const val CONTEXT_MAX_BYTES = 32_768
    const val CONTEXT_MAX_ITEMS = 12
    private const val MAX_ID_UNITS = 512
    private const val TITLE_POINTS = 160
    private const val INTENT_POINTS = 240

    private fun protocol(value: LedgerSnapshot) = when {
        value.planningHistory != null -> "taskctl.native/alpha4"
        value.imports.isNotEmpty() -> "taskctl.native/alpha3"
        value.history != null -> "taskctl.native/alpha2"
        else -> "taskctl.native/alpha1"
    }

    private fun unavailable(value: LedgerSnapshot): List<String> = (value.universe.tasks.flatMap { it.requiredExtensions } +
        value.universe.roadmaps.flatMap { it.requiredExtensions } + value.universe.epics.flatMap { it.requiredExtensions }).distinct().sorted()

    private fun currencyCounts(currency: Map<TaskId, TaskCurrency>) = ObjectValue(Currency.entries.associate { state ->
        state.name.lowercase() to integer(currency.values.count { it.state == state })
    })

    private fun ready(value: LedgerSnapshot): Set<TaskId> {
        require(value.dependencyProblems().isEmpty()) { value.dependencyProblems().joinToString("\n") }
        return if (unavailable(value).isNotEmpty()) emptySet() else value.frontier().tasks.toSet()
    }

    fun doctor(value: LedgerSnapshot): ObjectValue {
        val currency = value.currency()
        val missing = unavailable(value)
        val diagnostics = mutableListOf<Value>()
        if (missing.isNotEmpty()) diagnostics += obj("code" to StringValue("REQUIRED_CAPABILITY_UNAVAILABLE"),
            "severity" to StringValue("error"), "capabilities" to strings(missing))
        for (state in listOf(Currency.AFFECTED, Currency.UNRESOLVED)) {
            val count = currency.values.count { it.state == state }
            if (count > 0) diagnostics += obj("code" to StringValue("TASK_CURRENCY_${state.name}"),
                "severity" to StringValue("warning"), "count" to integer(count))
        }
        if (value.history == null) diagnostics += obj("code" to StringValue("HISTORY_UNTRACKED"), "severity" to StringValue("warning"))
        val planningStatuses = value.planningHistory?.let { value.planningAssessmentStatuses(it) }.orEmpty()
        value.planningHistory?.let { history ->
            for (state in listOf(PlanningAssessmentCurrency.HISTORICAL, PlanningAssessmentCurrency.UNRESOLVED)) {
                val count = history.assessmentHeads.values.count { planningStatuses.getValue(it).currency == state }
                if (count > 0) diagnostics += obj("code" to StringValue("PLANNING_ASSESSMENT_${state.name}"),
                    "severity" to StringValue("warning"), "count" to integer(count))
            }
        }
        val result = obj(
            "contract" to StringValue(if (value.planningHistory == null) "taskctl.doctor/alpha1" else "taskctl.doctor/alpha2"), "repository_id" to StringValue(value.repositoryId),
            "protocol" to StringValue(protocol(value)), "profile" to StringValue("minimal/alpha1"),
            "revision" to StringValue(value.revision.value), "valid" to BooleanValue(true),
            "health" to StringValue(if (missing.isNotEmpty()) "blocked" else if (diagnostics.isNotEmpty()) "attention" else "ok"),
            "tasks" to integer(value.universe.tasks.size), "roadmaps" to integer(value.universe.roadmaps.size), "epics" to integer(value.universe.epics.size),
            "required_capabilities_unavailable" to strings(missing), "currency" to currencyCounts(currency),
            "diagnostics" to ArrayValue(diagnostics),
        )
        return if (value.planningHistory == null) result else ObjectValue(result.fields + ("planning" to PlanningReadModels.summary(value, planningStatuses)))
    }

    private data class Snippet(val text: String, val truncated: Boolean) {
        fun encode() = obj("text" to StringValue(text), "truncated" to BooleanValue(truncated))
    }

    private fun snippet(text: String, points: Int): Snippet {
        val count = text.codePointCount(0, text.length)
        return if (count <= points) Snippet(text, false) else Snippet(text.substring(0, text.offsetByCodePoints(0, points)), true)
    }

    private enum class Category(val wire: String) { REVIEW("needs_review"), READY("ready"), WAITING("waiting") }
    private data class Candidate(val task: DraftRecord, val category: Category)

    fun context(value: LedgerSnapshot): ObjectValue {
        val currency = value.currency()
        val ready = ready(value)
        val candidates = value.universe.tasks.mapNotNull { task ->
            val category = when {
                task.id in ready -> Category.READY
                currency.getValue(task.id).state != Currency.CURRENT -> Category.REVIEW
                task.state == "open" -> Category.WAITING
                else -> null
            }
            category?.let { Candidate(task, it) }
        }.sortedWith(compareBy<Candidate> { it.category.ordinal }.thenBy { it.task.id })
        val repository = snippet(value.repositoryId, TITLE_POINTS)
        val selected = mutableListOf<ObjectValue>()
        val overlongIdentities = candidates.count { it.task.id.value.length > MAX_ID_UNITS }
        val counts = obj("tasks" to integer(value.universe.tasks.size), "roadmaps" to integer(value.universe.roadmaps.size),
            "epics" to integer(value.universe.epics.size), "closed" to integer(value.universe.tasks.count { it.state == "closed" }),
            "ready" to integer(ready.size), "needs_review" to integer(candidates.count { it.category == Category.REVIEW }),
            "waiting" to integer(candidates.count { it.category == Category.WAITING }),
            "required_capabilities_unavailable" to integer(unavailable(value).size))
        fun projection(items: List<ObjectValue>): ObjectValue = obj(
            "contract" to StringValue("taskctl.context/alpha1"), "repository_display" to repository.encode(),
            "protocol" to StringValue(protocol(value)), "profile" to StringValue("minimal/alpha1"), "revision" to StringValue(value.revision.value),
            "counts" to counts, "currency" to currencyCounts(currency),
            "limits" to obj("max_result_utf8_bytes" to integer(CONTEXT_MAX_BYTES), "max_items" to integer(CONTEXT_MAX_ITEMS),
                "max_identity_utf16_units" to integer(MAX_ID_UNITS), "title_code_points" to integer(TITLE_POINTS), "intent_code_points" to integer(INTENT_POINTS)),
            "work" to ArrayValue(items), "omitted_items" to integer(candidates.size - items.size),
            "omitted_overlong_identities" to integer(overlongIdentities),
            "truncated" to BooleanValue(repository.truncated || items.size < candidates.size || items.any { it.fields["text_truncated"] == BooleanValue(true) }),
            "complete_ledger" to BooleanValue(false),
            "follow_up" to strings(listOf("frontier --format json", "affected --format json", "show TASK --format json", "doctor --format json", "snapshot --format json")),
            "instructions" to StringValue("Read AGENTS.md. This is a bounded briefing; use show for a full record and snapshot for complete typed state. Presentation order grants no priority or execution authority. Mutations require the inspected revision."),
        )
        for ((task, category) in candidates) {
            if (selected.size == CONTEXT_MAX_ITEMS) break
            // Never emit a truncated identity as though it were a valid TaskId.
            // Snapshot remains the complete locator when an identity cannot fit.
            if (task.id.value.length > MAX_ID_UNITS) continue
            val title = snippet(task.title, TITLE_POINTS)
            val intent = snippet(task.intent, INTENT_POINTS)
            val item = obj("id" to StringValue(task.id.value), "category" to StringValue(category.wire),
                "lifecycle" to StringValue(task.state), "currency" to StringValue(currency.getValue(task.id).state.name.lowercase()),
                "title" to StringValue(title.text), "intent" to StringValue(intent.text),
                "text_truncated" to BooleanValue(title.truncated || intent.truncated),
                "prerequisite_count" to integer(task.requires.size),
                "contract" to StringValue(DraftLifecycle.contract(task).value), "head" to optionalString(value.history?.heads?.get(task.id)?.value))
            if (Json.encode(projection(selected + item)).toByteArray(Charsets.UTF_8).size <= CONTEXT_MAX_BYTES) selected += item
        }
        return projection(selected).also { check(Json.encode(it).toByteArray(Charsets.UTF_8).size <= CONTEXT_MAX_BYTES) }
    }

    /** Complete typed snapshot, not an archive of source formatting or wrappers.
     * Its ledger Revision remains an observed storage identity, never a digest
     * recomputed from this presentation projection. */
    fun snapshot(value: LedgerSnapshot): ObjectValue {
        val currency = value.currency()
        val history = value.history?.let { history -> obj("heads" to HistoryCodec.heads(history),
            "revisions" to ObjectValue(history.revisions.entries.sortedBy { it.key.value }.associate { it.key.value to HistoryCodec.revision(it.value) })) } ?: NullValue
        val result = obj(
            "contract" to StringValue(if (value.planningHistory == null) "taskctl.snapshot/alpha1" else "taskctl.snapshot/alpha2"), "repository_id" to StringValue(value.repositoryId),
            "protocol" to StringValue(protocol(value)), "profile" to StringValue("minimal/alpha1"), "revision" to StringValue(value.revision.value),
            "records" to obj("tasks" to ArrayValue(value.universe.tasks.sortedBy { it.id }.map(NativeCodec::task)),
                "roadmaps" to ArrayValue(value.universe.roadmaps.sortedBy { it.id.value }.map(PlanningRecordCodec::encode)),
                "epics" to ArrayValue(value.universe.epics.sortedBy { it.id.value }.map(PlanningRecordCodec::encode))),
            "receipts" to ArrayValue(value.receipts.map(NativeCodec::evidence).sortedBy(Json::encode)),
            "dependency_bindings" to ObjectValue(value.dependencyBindings.entries.sortedBy { it.key.value }.associate { (id, edges) ->
                id.value to ArrayValue(edges.sortedBy { it.upstream }.map(HistoryCodec::dependency))
            }),
            "history" to history, "imports" to ArrayValue(value.imports.sortedBy { it.manifest.id.value }.map(ImportCodec::admission)),
            "required_capabilities_unavailable" to strings(unavailable(value)),
            "derived" to obj("frontier" to strings(ready(value).sorted().map { it.value }),
                "currency" to ArrayValue(currency.values.sortedBy { it.task }.map(HistoryCodec::currency))),
        )
        return if (value.planningHistory == null) result else ObjectValue(result.fields + ("planning_history" to PlanningReadModels.complete(value)))
    }
}
