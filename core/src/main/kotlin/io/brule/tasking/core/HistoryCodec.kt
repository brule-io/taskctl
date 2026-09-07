package io.brule.tasking.core

/** Explicit typed wire projection shared by file storage and CLI consumers. */
object HistoryCodec {
    private fun ObjectValue.exact(vararg names: String) { require(fields.keys == names.toSet()) { "unknown or missing history fields: ${fields.keys}" } }
    private fun ObjectValue.objectAt(name: String): ObjectValue = fields[name] as? ObjectValue ?: error("$name must be an object")
    private fun ObjectValue.nullableText(name: String): String? = when (val value = fields.getValue(name)) {
        NullValue -> null
        is StringValue -> value.value
        else -> error("$name must be a string or null")
    }
    fun dependency(value: Dependency): ObjectValue = obj(
        "upstream" to StringValue(value.upstream.value), "contract" to optionalString(value.observedContract?.value),
        "revision" to optionalString(value.observedRevision?.value), "inputs" to optionalString(value.observedInputs?.value),
    )
    fun decodeDependency(value: ObjectValue): Dependency {
        value.exact("upstream", "contract", "revision", "inputs")
        return Dependency(TaskId.parseOrThrow(value.requiredString("upstream")), value.nullableText("contract")?.let(ContractDigest::parseOrThrow),
            value.nullableText("revision")?.let(TaskRevisionId::parseOrThrow), value.nullableText("inputs")?.let(InputDigest::parseOrThrow))
    }
    fun review(value: Reconciliation): ObjectValue = obj(
        "protocol" to StringValue(value.time.version.protocol("taskctl.reconciliation/1", "taskctl.reconciliation/2")), "classification" to StringValue("actor-assertion"),
        "task" to StringValue(value.task.value), "reviewed_head" to StringValue(value.reviewedHead.value),
        "outcome" to StringValue(value.outcome.name.lowercase()), "observations" to ArrayValue(value.observations.sortedBy { it.upstream }.map(::dependency)),
        "actor" to StringValue(value.actor), value.time.version.field to StringValue(value.time.value),
        "rationale" to StringValue(value.rationale), "evidence" to stringMap(value.evidence), "successor" to optionalString(value.successor?.value),
    )
    fun decodeReview(value: ObjectValue): Reconciliation {
        val time = AssertionTimeVersion.select(value.requiredString("protocol"), "taskctl.reconciliation/1", "taskctl.reconciliation/2")
        value.exact("protocol", "classification", "task", "reviewed_head", "outcome", "observations", "actor", time.field, "rationale", "evidence", "successor")
        require(value.requiredString("classification") == "actor-assertion") { "unsupported reconciliation" }
        return Reconciliation(TaskId.parseOrThrow(value.requiredString("task")), TaskRevisionId.parseOrThrow(value.requiredString("reviewed_head")),
            ReviewOutcome.entries.singleOrNull { it.name.lowercase() == value.requiredString("outcome") } ?: error("unknown reconciliation outcome"),
            value.requiredArray("observations").map { decodeDependency(it as? ObjectValue ?: error("observation object required")) },
            value.requiredString("actor"), time.decode(value), value.requiredString("rationale"),
            value.objectAt("evidence").fields.mapValues { (it.value as? StringValue)?.value ?: error("evidence string required") },
            value.nullableText("successor")?.let(TaskId::parseOrThrow))
    }
    fun revision(value: TaskRevision): ObjectValue = obj(
        "protocol" to StringValue(if (value.importedFrom == null) "taskctl.task-revision/1" else "taskctl.task-revision/2"), "parent" to optionalString(value.parent?.value),
        "record" to NativeCodec.task(value.record), "contract" to StringValue(DraftLifecycle.contract(value.record).value),
        "dependencies" to ArrayValue(value.dependencies.sortedBy { it.upstream }.map(::dependency)),
        "review" to (value.review?.let(::review) ?: NullValue),
    ).let { if (value.importedFrom == null) it else ObjectValue(it.fields + ("imported_from" to StringValue(value.importedFrom.value))) }
    fun decodeRevision(value: ObjectValue): TaskRevision {
        val imported = when (value.requiredString("protocol")) {
            "taskctl.task-revision/1" -> { value.exact("protocol", "parent", "record", "contract", "dependencies", "review"); null }
            "taskctl.task-revision/2" -> { value.exact("protocol", "parent", "record", "contract", "dependencies", "review", "imported_from"); ImportId.parseOrThrow(value.requiredString("imported_from")) }
            else -> error("unsupported task revision")
        }
        val record = DraftDocument.parse(Json.encode(value.objectAt("record"))).record
        require(DraftLifecycle.contract(record).value == value.requiredString("contract")) { "revision contract digest mismatch" }
        return TaskRevision(value.nullableText("parent")?.let(TaskRevisionId::parseOrThrow), record,
            value.requiredArray("dependencies").map { decodeDependency(it as? ObjectValue ?: error("dependency object required")) },
            if (value.fields["review"] == NullValue) null else decodeReview(value.objectAt("review")), imported)
    }
    fun heads(value: TaskHistory): ObjectValue = obj(
        "protocol" to StringValue("taskctl.history/1"), "origin_ledger_revision" to StringValue(value.origin.value),
        "heads" to ObjectValue(value.heads.keys.sorted().associate { it.value to StringValue(value.heads.getValue(it).value) }),
    )
    fun decodeHeads(value: ObjectValue, revisions: Map<TaskRevisionId, TaskRevision>): TaskHistory {
        value.exact("protocol", "origin_ledger_revision", "heads")
        require(value.requiredString("protocol") == "taskctl.history/1") { "unsupported history" }
        return TaskHistory(Revision.parseOrThrow(value.requiredString("origin_ledger_revision")),
            value.objectAt("heads").fields.map { (id, head) -> TaskId.parseOrThrow(id) to TaskRevisionId.parseOrThrow((head as? StringValue)?.value ?: error("HEAD string required")) }.toMap(), revisions)
    }
    fun currency(value: TaskCurrency): ObjectValue = obj(
        "task" to StringValue(value.task.value), "currency" to StringValue(value.state.name.lowercase()),
        "causes" to ArrayValue(value.causes.map { obj("path" to strings(it.path.map { id -> id.value }), "reason" to StringValue(it.reason),
            "observed" to (it.observed?.let(::dependency) ?: NullValue), "current" to (it.current?.let(::dependency) ?: NullValue)) }),
    )
}
