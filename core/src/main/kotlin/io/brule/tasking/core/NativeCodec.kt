package io.brule.tasking.core

/** Typed process/storage envelopes; protocol drafts remain explicitly versioned. */
object NativeCodec {
    fun task(record: DraftRecord): ObjectValue = obj(
        "protocol" to StringValue("tasking/core-draft-1"), "id" to StringValue(record.id.value),
        "title" to StringValue(record.title), "state" to StringValue(record.state), "intent" to StringValue(record.intent),
        "requires" to strings(record.requires.map { it.value }), "requirements" to strings(record.requirements), "acceptance" to strings(record.acceptance),
        "required_extensions" to strings(record.requiredExtensions), "extensions" to record.extensions,
    )
    fun evidence(value: ClosureEvidence): ObjectValue = obj(
        "protocol" to StringValue("taskctl.receipt/alpha1"), "classification" to StringValue("actor-assertion"),
        "task" to StringValue(value.receipt.taskId.value), "contract" to StringValue(value.receipt.contractDigest.value),
        "actor" to StringValue(value.actor), "recorded_at" to StringValue(value.recordedAt),
        "evidence" to stringMap(value.receipt.evidence),
    )
    fun decodeEvidence(root: ObjectValue): ClosureEvidence {
        require(root.fields.keys == setOf("protocol", "classification", "task", "contract", "actor", "recorded_at", "evidence")) { "unknown or missing receipt fields" }
        require(root.requiredString("protocol") == "taskctl.receipt/alpha1" && root.requiredString("classification") == "actor-assertion") { "unsupported evidence contract" }
        val entries = (root.fields["evidence"] as? ObjectValue ?: error("evidence must be an object")).fields.mapValues {
            (it.value as? StringValue)?.value ?: error("evidence must contain strings")
        }
        return ClosureEvidence(Receipt(TaskId.parseOrThrow(root.requiredString("task")), ContractDigest.parseOrThrow(root.requiredString("contract")), entries), root.requiredString("actor"), root.requiredString("recorded_at"))
    }
    fun decodeSeed(root: ObjectValue): Transition.AddRecords {
        require((root.fields.keys - setOf("contract", "tasks", "roadmaps", "epics")).isEmpty()) { "unknown seed fields" }
        require(root.requiredString("contract") == "taskctl.seed/alpha1") { "unsupported seed contract" }
        fun values(key: String) = if (key in root.fields) root.requiredArray(key) else emptyList()
        val tasks = values("tasks").map { DraftDocument.parse(Json.encode(it)).record }
        val roadmaps = values("roadmaps").map { PlanningRecordCodec.decode(it as? ObjectValue ?: error("roadmap must be an object")) as? DraftRoadmap ?: error("roadmap kind required") }
        val epics = values("epics").map { PlanningRecordCodec.decode(it as? ObjectValue ?: error("epic must be an object")) as? DraftEpic ?: error("epic kind required") }
        return Transition.AddRecords(tasks, roadmaps, epics)
    }
}
