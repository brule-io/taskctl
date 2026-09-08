package io.brule.tasking.core

/** Explicit planning wire contracts. No legacy task or planning hash is redefined. */
object PlanningHistoryCodec {
    private fun ObjectValue.exact(vararg fields: String) { require(this.fields.keys == fields.toSet()) { "unknown or missing planning history fields" } }
    private fun ObjectValue.objectAt(key: String): ObjectValue = fields[key] as? ObjectValue ?: error("$key must be an object")
    private fun ObjectValue.nullableText(key: String): String? = when (val value = fields.getValue(key)) {
        NullValue -> null
        is StringValue -> value.value
        else -> error("$key must be a string or null")
    }
    private fun <T : Enum<T>> enumValue(value: String, entries: List<T>): T =
        entries.singleOrNull { it.name.lowercase() == value } ?: error("unknown planning value: $value")

    fun audit(value: PlanningAudit): ObjectValue = obj("protocol" to StringValue("taskctl.planning-audit/1"),
        "classification" to StringValue("actor-assertion"), "actor" to StringValue(value.actor), "occurred_at" to StringValue(value.occurredAt.value),
        "reason" to StringValue(value.reason), "evidence" to stringMap(value.evidence))
    fun decodeAudit(value: ObjectValue): PlanningAudit {
        value.exact("protocol", "classification", "actor", "occurred_at", "reason", "evidence")
        require(value.requiredString("protocol") == "taskctl.planning-audit/1" && value.requiredString("classification") == "actor-assertion") { "unsupported planning audit" }
        return PlanningAudit(value.requiredString("actor"), OccurredAt.parseOrThrow(value.requiredString("occurred_at")), value.requiredString("reason"),
            value.objectAt("evidence").fields.mapValues { (it.value as? StringValue)?.value ?: error("planning evidence strings required") })
    }

    private fun change(value: PlanningChange): ObjectValue = when (value) {
        is PlanningChange.Baseline -> obj("kind" to StringValue("baseline"), "ledger_revision" to StringValue(value.ledger.value))
        is PlanningChange.Seeded -> obj("kind" to StringValue("seeded"), "ledger_revision" to StringValue(value.ledger.value))
        is PlanningChange.Imported -> obj("kind" to StringValue("imported"), "manifest" to StringValue(value.manifest.value))
        is PlanningChange.Amended -> obj("kind" to StringValue("amended"), "audit" to audit(value.audit))
        is PlanningChange.DispositionChanged -> obj("kind" to StringValue("disposition_changed"), "audit" to audit(value.audit))
    }
    private fun decodeChange(value: ObjectValue): PlanningChange = when (value.requiredString("kind")) {
        "baseline" -> { value.exact("kind", "ledger_revision"); PlanningChange.Baseline(Revision.parseOrThrow(value.requiredString("ledger_revision"))) }
        "seeded" -> { value.exact("kind", "ledger_revision"); PlanningChange.Seeded(Revision.parseOrThrow(value.requiredString("ledger_revision"))) }
        "imported" -> { value.exact("kind", "manifest"); PlanningChange.Imported(ImportId.parseOrThrow(value.requiredString("manifest"))) }
        "amended" -> { value.exact("kind", "audit"); PlanningChange.Amended(decodeAudit(value.objectAt("audit"))) }
        "disposition_changed" -> { value.exact("kind", "audit"); PlanningChange.DispositionChanged(decodeAudit(value.objectAt("audit"))) }
        else -> error("unknown planning revision origin")
    }

    fun revision(value: PlanningRevision): ObjectValue = obj("protocol" to StringValue("taskctl.planning-revision/1"),
        "parent" to optionalString(value.parent?.value), "record" to PlanningRecordCodec.encode(value.record), "change" to change(value.change))
    fun decodeRevision(value: ObjectValue): PlanningRevision {
        value.exact("protocol", "parent", "record", "change")
        require(value.requiredString("protocol") == "taskctl.planning-revision/1") { "unsupported planning revision" }
        return PlanningRevision(value.nullableText("parent")?.let(PlanningRevisionId::parseOrThrow),
            PlanningRecordCodec.decode(value.objectAt("record")), decodeChange(value.objectAt("change")))
    }

    fun amendment(value: PlanningAmendment): ObjectValue = obj("protocol" to StringValue("taskctl.planning-amendment/1"),
        "reviewed_head" to StringValue(value.reviewedHead.value), "record" to PlanningRecordCodec.encode(value.record), "audit" to audit(value.audit))
    fun decodeAmendment(value: ObjectValue): PlanningAmendment {
        value.exact("protocol", "reviewed_head", "record", "audit")
        require(value.requiredString("protocol") == "taskctl.planning-amendment/1") { "unsupported planning amendment" }
        return PlanningAmendment(PlanningRevisionId.parseOrThrow(value.requiredString("reviewed_head")), PlanningRecordCodec.decode(value.objectAt("record")), decodeAudit(value.objectAt("audit")))
    }

    fun disposition(value: PlanningDispositionChange): ObjectValue = obj("protocol" to StringValue("taskctl.planning-disposition/1"),
        "planning" to StringValue(value.planning.value), "reviewed_head" to StringValue(value.reviewedHead.value),
        "disposition" to StringValue(value.disposition.name.lowercase()), "audit" to audit(value.audit))
    fun decodeDisposition(value: ObjectValue): PlanningDispositionChange {
        value.exact("protocol", "planning", "reviewed_head", "disposition", "audit")
        require(value.requiredString("protocol") == "taskctl.planning-disposition/1") { "unsupported planning disposition" }
        return PlanningDispositionChange(PlanningId.parseOrThrow(value.requiredString("planning")), PlanningRevisionId.parseOrThrow(value.requiredString("reviewed_head")),
            enumValue(value.requiredString("disposition"), PlanningDisposition.entries), decodeAudit(value.objectAt("audit")))
    }

    fun observation(value: PlanningTaskObservation): ObjectValue = obj("task" to StringValue(value.task.value), "contract" to StringValue(value.contract.value),
        "revision" to StringValue(value.revision.value), "inputs" to StringValue(value.inputs.value))
    private fun decodeObservation(value: ObjectValue): PlanningTaskObservation {
        value.exact("task", "contract", "revision", "inputs")
        return PlanningTaskObservation(TaskId.parseOrThrow(value.requiredString("task")), ContractDigest.parseOrThrow(value.requiredString("contract")),
            TaskRevisionId.parseOrThrow(value.requiredString("revision")), InputDigest.parseOrThrow(value.requiredString("inputs")))
    }

    fun assessment(value: PlanningAssessment): ObjectValue = obj("protocol" to StringValue("taskctl.planning-assessment/1"),
        "parent" to optionalString(value.parent?.value), "planning" to StringValue(value.planning.value), "reviewed_head" to StringValue(value.reviewedHead.value),
        "observations" to ArrayValue(value.observations.sortedBy { it.task }.map(::observation)), "outcome" to StringValue(value.outcome.name.lowercase()),
        "audit" to audit(value.audit), "criterion_evidence" to strings(value.criterionEvidence))
    fun decodeAssessment(value: ObjectValue): PlanningAssessment {
        value.exact("protocol", "parent", "planning", "reviewed_head", "observations", "outcome", "audit", "criterion_evidence")
        require(value.requiredString("protocol") == "taskctl.planning-assessment/1") { "unsupported planning assessment" }
        return PlanningAssessment(value.nullableText("parent")?.let(PlanningAssessmentId::parseOrThrow), PlanningId.parseOrThrow(value.requiredString("planning")),
            PlanningRevisionId.parseOrThrow(value.requiredString("reviewed_head")), value.requiredArray("observations").map { decodeObservation(it as? ObjectValue ?: error("planning observation object required")) },
            enumValue(value.requiredString("outcome"), PlanningAssessmentOutcome.entries), decodeAudit(value.objectAt("audit")),
            value.requiredArray("criterion_evidence").map { (it as? StringValue)?.value ?: error("criterion evidence string required") })
    }

    fun heads(value: PlanningHistory): ObjectValue = obj("protocol" to StringValue("taskctl.planning-history/1"),
        "origin_ledger_revision" to StringValue(value.origin.value),
        "heads" to ObjectValue(value.heads.keys.sortedBy { it.value }.associate { it.value to StringValue(value.heads.getValue(it).value) }),
        "assessment_heads" to ObjectValue(value.assessmentHeads.keys.sortedBy { it.value }.associate { it.value to StringValue(value.assessmentHeads.getValue(it).value) }))
    fun decodeHeads(value: ObjectValue, revisions: Map<PlanningRevisionId, PlanningRevision>, assessments: Map<PlanningAssessmentId, PlanningAssessment>): PlanningHistory {
        value.exact("protocol", "origin_ledger_revision", "heads", "assessment_heads")
        require(value.requiredString("protocol") == "taskctl.planning-history/1") { "unsupported planning history" }
        return PlanningHistory(Revision.parseOrThrow(value.requiredString("origin_ledger_revision")),
            value.objectAt("heads").fields.map { (id, head) -> PlanningId.parseOrThrow(id) to PlanningRevisionId.parseOrThrow((head as? StringValue)?.value ?: error("planning HEAD string required")) }.toMap(), revisions,
            value.objectAt("assessment_heads").fields.map { (id, head) -> PlanningId.parseOrThrow(id) to PlanningAssessmentId.parseOrThrow((head as? StringValue)?.value ?: error("assessment HEAD string required")) }.toMap(), assessments)
    }
    fun status(value: PlanningAssessmentStatus): ObjectValue = obj("assessment" to StringValue(value.assessment.value),
        "currency" to StringValue(value.currency.name.lowercase()), "reasons" to strings(value.reasons))
}
