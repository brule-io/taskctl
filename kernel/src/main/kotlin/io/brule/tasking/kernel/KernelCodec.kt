package io.brule.tasking.kernel

import io.brule.tasking.core.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Experimental transport/storage envelopes around existing typed core codecs. */
object KernelCodec {
    const val MAX_BODY_BYTES = 1_000_000
    const val MAX_OBJECT_BYTES = 250_000
    internal fun Value.objectValue(): ObjectValue = this as? ObjectValue ?: error("object required")
    internal fun ObjectValue.objectAt(name: String): ObjectValue = fields.getValue(name).objectValue()
    internal fun ObjectValue.exact(vararg names: String) { require(fields.keys == names.toSet()) { "unknown or missing kernel fields" } }
    internal fun ObjectValue.nullableText(name: String): String? = when (val value = fields.getValue(name)) {
        NullValue -> null
        is StringValue -> value.value
        else -> error("string or null required")
    }
    internal fun text(value: Value): String = Json.encode(value).also {
        require(it.toByteArray(Charsets.UTF_8).size <= MAX_OBJECT_BYTES) { "bounded kernel object exceeds byte limit" }
    }
    fun parse(bytes: ByteArray): ObjectValue {
        require(bytes.size <= MAX_BODY_BYTES) { "bounded kernel body exceeds byte limit" }
        val source = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val value = YamlValues.parse(source).value.objectValue()
        // This experimental wire accepts the exact JSON emitted by the typed
        // writer, not YAML's wider language or a permissive second JSON codec.
        require(Json.encode(value) == source) { "canonical typed JSON required" }
        return value
    }
    internal fun parse(text: String): ObjectValue = parse(text.toByteArray(Charsets.UTF_8))

    fun validate(value: LedgerSnapshot) {
        require(value.repositoryId.isNotBlank()) { "repository identity required" }
        require(value.profileHistory == null && value.providers.providers.isEmpty()) { "kernel supports only the unadopted minimal profile" }
        require(value.structuralProblems().isEmpty()) { value.structuralProblems().joinToString("\n") }
        value.history?.validate(value.universe, value.receipts, value.imports)
        value.planningHistory?.validate(value)
        require(value.planningHistory != null || value.universe.planningRecords.all { it.protocol == PlanningRecordCodec.PROTOCOL }) { "audited planning requires history" }
        value.validateProfiles()
    }
    fun state(value: LedgerSnapshot): ObjectValue {
        validate(value)
        return obj("protocol" to StringValue("taskctl.kernel-state/alpha1"), "repository_id" to StringValue(value.repositoryId),
            "records" to obj("tasks" to ArrayValue(value.universe.tasks.map(NativeCodec::task)),
                "roadmaps" to ArrayValue(value.universe.roadmaps.map(PlanningRecordCodec::encode)), "epics" to ArrayValue(value.universe.epics.map(PlanningRecordCodec::encode))),
            "receipts" to ArrayValue(value.receipts.map(NativeCodec::evidence)),
            "dependency_bindings" to ObjectValue(value.dependencyBindings.keys.sorted().associate { key ->
                key.value to ArrayValue(value.dependencyBindings.getValue(key).map(HistoryCodec::dependency)) }),
            "history" to (value.history?.let { history -> obj("heads" to HistoryCodec.heads(history),
                "revisions" to ObjectValue(history.revisions.entries.sortedBy { it.key.value }.associate { it.key.value to HistoryCodec.revision(it.value) })) } ?: NullValue),
            "imports" to ArrayValue(value.imports.map(ImportCodec::admission)),
            "planning_history" to (value.planningHistory?.let { history -> obj("heads" to PlanningHistoryCodec.heads(history),
                "revisions" to ObjectValue(history.revisions.entries.sortedBy { it.key.value }.associate { it.key.value to PlanningHistoryCodec.revision(it.value) }),
                "assessments" to ObjectValue(history.assessments.entries.sortedBy { it.key.value }.associate { it.key.value to PlanningHistoryCodec.assessment(it.value) })) } ?: NullValue))
    }
    fun revision(value: LedgerSnapshot): Revision = Revision.parseOrThrow(Canonical.digest("taskctl.kernel-snapshot/alpha1", state(value)))
    fun snapshot(value: LedgerSnapshot): ObjectValue = obj("protocol" to StringValue("taskctl.kernel-snapshot/alpha1"),
        "revision" to StringValue(value.revision.value), "state" to state(value))
    fun decodeSnapshot(value: ObjectValue): LedgerSnapshot {
        value.exact("protocol", "revision", "state")
        require(value.requiredString("protocol") == "taskctl.kernel-snapshot/alpha1") { "unsupported kernel snapshot" }
        val state = value.objectAt("state")
        state.exact("protocol", "repository_id", "records", "receipts", "dependency_bindings", "history", "imports", "planning_history")
        require(state.requiredString("protocol") == "taskctl.kernel-state/alpha1") { "unsupported kernel state" }
        val records = state.objectAt("records")
        records.exact("tasks", "roadmaps", "epics")
        val universe = DraftUniverse(records.requiredArray("tasks").map { DraftDocument.parse(Json.encode(it)).record },
            records.requiredArray("roadmaps").map { PlanningRecordCodec.decode(it.objectValue()) as? DraftRoadmap ?: error("roadmap required") },
            records.requiredArray("epics").map { PlanningRecordCodec.decode(it.objectValue()) as? DraftEpic ?: error("epic required") })
        val history = if (state.fields["history"] == NullValue) null else state.objectAt("history").let { h ->
            h.exact("heads", "revisions")
            HistoryCodec.decodeHeads(h.objectAt("heads"), h.objectAt("revisions").fields.entries.associate {
                TaskRevisionId.parseOrThrow(it.key) to HistoryCodec.decodeRevision(it.value.objectValue()) })
        }
        val planning = if (state.fields["planning_history"] == NullValue) null else state.objectAt("planning_history").let { h ->
            h.exact("heads", "revisions", "assessments")
            PlanningHistoryCodec.decodeHeads(h.objectAt("heads"), h.objectAt("revisions").fields.entries.associate {
                PlanningRevisionId.parseOrThrow(it.key) to PlanningHistoryCodec.decodeRevision(it.value.objectValue()) },
                h.objectAt("assessments").fields.entries.associate { PlanningAssessmentId.parseOrThrow(it.key) to PlanningHistoryCodec.decodeAssessment(it.value.objectValue()) })
        }
        val snapshot = LedgerSnapshot(state.requiredString("repository_id"), Revision.parseOrThrow(value.requiredString("revision")),
            universe, state.requiredArray("receipts").map { NativeCodec.decodeEvidence(it.objectValue()) },
            state.objectAt("dependency_bindings").fields.entries.associate { (id, edges) -> TaskId.parseOrThrow(id) to
                (edges as? ArrayValue ?: error("dependency array required")).values.map { HistoryCodec.decodeDependency(it.objectValue()) } },
            history, state.requiredArray("imports").map { ImportCodec.decodeAdmission(it.objectValue()) }, planning)
        validate(snapshot)
        require(snapshot.revision == revision(snapshot)) { "kernel snapshot digest mismatch" }
        return snapshot
    }
    private fun seed(value: Transition.AddRecords): ObjectValue = obj("contract" to StringValue("taskctl.seed/alpha1"),
        "tasks" to ArrayValue(value.tasks.map(NativeCodec::task)), "roadmaps" to ArrayValue(value.roadmaps.map(PlanningRecordCodec::encode)),
        "epics" to ArrayValue(value.epics.map(PlanningRecordCodec::encode)))
    fun transition(value: Transition): ObjectValue {
        val (kind, data) = when (value) {
            is Transition.AddRecords -> "seed" to seed(value)
            is Transition.CloseTask -> "close" to NativeCodec.evidence(value.evidence)
            is Transition.ReviseTask -> "revise" to NativeCodec.task(value.record)
            is Transition.ReconcileTask -> "reconcile" to HistoryCodec.review(value.review)
            Transition.TrackHistory -> "track_history" to NullValue
            is Transition.ImportRecords -> "import" to ImportCodec.admission(value.admission)
            Transition.TrackPlanning -> "track_planning" to NullValue
            is Transition.AmendPlanning -> "amend_planning" to PlanningHistoryCodec.amendment(value.amendment)
            is Transition.SetPlanningDisposition -> "planning_disposition" to PlanningHistoryCodec.disposition(value.change)
            is Transition.AssessPlanning -> "assess_planning" to PlanningHistoryCodec.assessment(value.assessment)
            is Transition.SetProfile -> error("kernel supports only the unadopted minimal profile")
        }
        return obj("protocol" to StringValue("taskctl.kernel-transition/alpha1"), "kind" to StringValue(kind), "data" to data)
    }
    fun decodeTransition(value: ObjectValue): Transition {
        value.exact("protocol", "kind", "data")
        require(value.requiredString("protocol") == "taskctl.kernel-transition/alpha1") { "unsupported kernel transition" }
        fun empty() { require(value.fields["data"] == NullValue) { "transition data must be null" } }
        return when (value.requiredString("kind")) {
            "seed" -> NativeCodec.decodeSeed(value.objectAt("data"))
            "close" -> Transition.CloseTask(NativeCodec.decodeEvidence(value.objectAt("data")))
            "revise" -> Transition.ReviseTask(DraftDocument.parse(Json.encode(value.objectAt("data"))).record)
            "reconcile" -> Transition.ReconcileTask(HistoryCodec.decodeReview(value.objectAt("data")))
            "track_history" -> { empty(); Transition.TrackHistory }
            "import" -> Transition.ImportRecords(ImportCodec.decodeAdmission(value.objectAt("data")))
            "track_planning" -> { empty(); Transition.TrackPlanning }
            "amend_planning" -> Transition.AmendPlanning(PlanningHistoryCodec.decodeAmendment(value.objectAt("data")))
            "planning_disposition" -> Transition.SetPlanningDisposition(PlanningHistoryCodec.decodeDisposition(value.objectAt("data")))
            "assess_planning" -> Transition.AssessPlanning(PlanningHistoryCodec.decodeAssessment(value.objectAt("data")))
            else -> error("unsupported kernel transition kind")
        }
    }
    fun request(revision: Revision, transition: Transition): ObjectValue = obj("protocol" to StringValue("taskctl.kernel-apply/alpha1"),
        "expected_revision" to StringValue(revision.value), "transition" to transition(transition))
    fun decodeRequest(value: ObjectValue): Pair<Revision, Transition> {
        value.exact("protocol", "expected_revision", "transition")
        require(value.requiredString("protocol") == "taskctl.kernel-apply/alpha1") { "unsupported kernel request" }
        return Revision.parseOrThrow(value.requiredString("expected_revision")) to decodeTransition(value.objectAt("transition"))
    }
    fun result(value: TransitionResult): ObjectValue = obj("protocol" to StringValue("taskctl.kernel-result/alpha1"),
        "revision" to StringValue(value.revision.value), "changed" to strings(value.changed.map { it.value }),
        "accepted_at" to StringValue(requireNotNull(value.acceptedAt) { "kernel success requires acceptance metadata" }.value))
    fun decodeResult(value: ObjectValue): TransitionResult {
        value.exact("protocol", "revision", "changed", "accepted_at")
        require(value.requiredString("protocol") == "taskctl.kernel-result/alpha1") { "unsupported kernel result" }
        return TransitionResult(Revision.parseOrThrow(value.requiredString("revision")), value.requiredArray("changed").map {
            val id = (it as? StringValue)?.value ?: error("changed identity string required")
            TaskId.parse(id) ?: PlanningId.parseOrThrow(id)
        }, AcceptedAt.parseOrThrow(value.requiredString("accepted_at"))).also {
            require(it.changed.distinct().size == it.changed.size) { "duplicate changed identity" }
        }
    }
}
