package io.brule.tasking.core

/** Planning assertions are explicit actor evidence, not independently executed checks. */
data class PlanningAudit(val actor: String, val occurredAt: OccurredAt, val reason: String, val evidence: Map<String, String>) {
    init {
        require(actor.isNotBlank() && reason.isNotBlank()) { "planning actor and reason are required" }
        require(evidence.isNotEmpty() && evidence.all { (key, value) -> key.isNotBlank() && value.isNotBlank() }) { "planning audit evidence is required" }
    }
}

sealed interface PlanningChange {
    data class Baseline(val ledger: Revision) : PlanningChange
    data class Seeded(val ledger: Revision) : PlanningChange
    data class Imported(val manifest: ImportId) : PlanningChange
    data class Amended(val audit: PlanningAudit) : PlanningChange
    data class DispositionChanged(val audit: PlanningAudit) : PlanningChange
}

data class PlanningRevision(val parent: PlanningRevisionId?, val record: PlanningRecord, val change: PlanningChange) {
    val id: PlanningRevisionId get() = PlanningRevisionId.parseOrThrow(Canonical.digest("taskctl.planning-revision/1", PlanningHistoryCodec.revision(this)))
}

data class PlanningAmendment(val reviewedHead: PlanningRevisionId, val record: PlanningRecord, val audit: PlanningAudit)
data class PlanningDispositionChange(val planning: PlanningId, val reviewedHead: PlanningRevisionId,
                                     val disposition: PlanningDisposition, val audit: PlanningAudit)

/** An index observation, never a task prerequisite or a grant of execution authority. */
data class PlanningTaskObservation(val task: TaskId, val contract: ContractDigest, val revision: TaskRevisionId, val inputs: InputDigest)
enum class PlanningAssessmentOutcome { ACCEPTED, NOT_ACCEPTED, UNRESOLVED }
enum class PlanningAssessmentCurrency { CURRENT, HISTORICAL, UNRESOLVED }
data class PlanningAssessment(val parent: PlanningAssessmentId?, val planning: PlanningId, val reviewedHead: PlanningRevisionId,
                              val observations: List<PlanningTaskObservation>, val outcome: PlanningAssessmentOutcome,
                              val audit: PlanningAudit, val criterionEvidence: List<String>) {
    init {
        require(observations.distinctBy { it.task }.size == observations.size) { "duplicate planning assessment observation" }
        require(criterionEvidence.all { it.isNotBlank() }) { "criterion evidence must be nonblank" }
    }
    val id: PlanningAssessmentId get() = PlanningAssessmentId.parseOrThrow(Canonical.digest("taskctl.planning-assessment/1", PlanningHistoryCodec.assessment(this)))
}
data class PlanningAssessmentStatus(val assessment: PlanningAssessmentId, val currency: PlanningAssessmentCurrency, val reasons: List<String>)

data class PlanningHistory(val origin: Revision, val heads: Map<PlanningId, PlanningRevisionId> = emptyMap(),
                           val revisions: Map<PlanningRevisionId, PlanningRevision> = emptyMap(),
                           val assessmentHeads: Map<PlanningId, PlanningAssessmentId> = emptyMap(),
                           val assessments: Map<PlanningAssessmentId, PlanningAssessment> = emptyMap()) {
    fun head(id: PlanningId): PlanningRevision = revisions.getValue(heads.getValue(id))
    fun append(revision: PlanningRevision): PlanningHistory = copy(
        heads = heads + (revision.record.id to revision.id), revisions = revisions + (revision.id to revision))
    fun append(assessment: PlanningAssessment): PlanningHistory = copy(
        assessmentHeads = assessmentHeads + (assessment.planning to assessment.id), assessments = assessments + (assessment.id to assessment))
}

val DraftUniverse.planningRecords: List<PlanningRecord> get() = roadmaps + epics
fun DraftUniverse.planning(id: PlanningId): PlanningRecord = planningRecords.singleOrNull { it.id == id } ?: error("unknown planning identity: $id")

/** Validating a legacy baseline never invents prior planning revisions. */
fun PlanningHistory.validate(snapshot: LedgerSnapshot) {
    val taskHistory = requireNotNull(snapshot.history) { "planning history requires tracked task history" }
    require(heads.keys == snapshot.universe.planningRecords.map { it.id }.toSet()) { "planning identities and HEADs disagree" }
    val checkedRevisions = mutableSetOf<PlanningRevisionId>()
    revisions.forEach { (id, value) ->
        require(id == value.id) { "planning revision identity mismatch" }
        val parent = value.parent?.let { revisions[it] ?: error("missing planning parent") }
        require(parent == null || parent.record.id == value.record.id) { "planning parent has a different identity" }
        when (val change = value.change) {
            is PlanningChange.Baseline -> require(parent == null && change.ledger == origin) { "invalid planning baseline origin" }
            is PlanningChange.Seeded -> require(parent == null && value.record.protocol == PlanningRecordCodec.AUDITED_PROTOCOL && value.record.disposition == PlanningDisposition.ACTIVE) { "invalid native planning creation" }
            is PlanningChange.Imported -> {
                require(parent == null) { "imported planning root cannot have a native parent" }
                val source = snapshot.imports.singleOrNull { it.manifest.id == change.manifest }?.manifest ?: error("planning import manifest absent")
                require(source.universe.planningRecords.singleOrNull { it.id == value.record.id } == value.record) { "planning import projection mismatch" }
            }
            is PlanningChange.Amended -> require(parent != null && value.record.protocol == PlanningRecordCodec.AUDITED_PROTOCOL &&
                value.record.disposition == parent.record.disposition) { "planning amendment cannot change disposition" }
            is PlanningChange.DispositionChanged -> require(parent != null && value.record.disposition != parent.record.disposition &&
                value.record == parent.record.withDisposition(value.record.disposition)) { "disposition transition changed planning scope or membership" }
        }
        val seen = mutableSetOf<PlanningRevisionId>()
        var cursor: PlanningRevisionId? = id
        while (cursor != null && cursor !in checkedRevisions) {
            require(seen.add(cursor)) { "planning revision parent cycle" }
            cursor = (revisions[cursor] ?: error("missing planning ancestor")).parent
        }
        checkedRevisions += seen
    }
    heads.forEach { (id, head) -> require(revisions[head]?.record == snapshot.universe.planning(id)) { "planning projection differs from HEAD: $id" } }
    require(assessmentHeads.keys.all { it in heads }) { "assessment HEAD has no planning identity" }
    require(assessmentHeads.keys == assessments.values.map { it.planning }.toSet()) { "planning assessments and HEAD indexes disagree" }
    val checkedAssessments = mutableSetOf<PlanningAssessmentId>()
    assessments.forEach { (id, value) ->
        require(id == value.id) { "planning assessment identity mismatch" }
        require(value.parent == null || assessments[value.parent]?.planning == value.planning) { "missing or wrong planning assessment parent" }
        val reviewed = revisions[value.reviewedHead]?.record ?: error("assessed planning revision absent")
        require(reviewed.id == value.planning) { "assessment addresses a different planning identity" }
        require(value.observations.map { it.task }.toSet() == reviewed.tasks.toSet()) { "assessment observations disagree with reviewed membership" }
        require(value.criterionEvidence.isEmpty() || value.criterionEvidence.size == reviewed.acceptance.size) { "criterion evidence disagrees with reviewed scope" }
        value.observations.forEach { observation ->
            val task = taskHistory.revisions[observation.revision]?.record ?: error("assessed task revision absent")
            require(task.id == observation.task && DraftLifecycle.contract(task) == observation.contract) { "assessment task revision and contract disagree" }
        }
        if (value.outcome == PlanningAssessmentOutcome.ACCEPTED) require(reviewed.protocol == PlanningRecordCodec.AUDITED_PROTOCOL &&
            reviewed.acceptance.isNotEmpty() && value.criterionEvidence.size == reviewed.acceptance.size) { "accepted planning assessment requires evidence for every criterion" }
        val seen = mutableSetOf<PlanningAssessmentId>()
        var cursor: PlanningAssessmentId? = id
        while (cursor != null && cursor !in checkedAssessments) {
            require(seen.add(cursor)) { "planning assessment parent cycle" }
            val previous = assessments[cursor] ?: error("missing planning assessment ancestor")
            require(previous.planning == value.planning) { "assessment parent has a different planning identity" }
            cursor = previous.parent
        }
        checkedAssessments += seen
    }
    assessmentHeads.forEach { (id, head) -> require(assessments[head]?.planning == id) { "planning assessment HEAD mismatch" } }
}

fun LedgerSnapshot.planningObservations(record: PlanningRecord): List<PlanningTaskObservation> {
    require(history != null) { "planning assessments require tracked task history" }
    val observed = CurrencyEvaluation.observations(this)
    return record.tasks.sorted().map { id ->
        val value = observed.getValue(id)
        PlanningTaskObservation(id, requireNotNull(value.observedContract), requireNotNull(value.observedRevision), requireNotNull(value.observedInputs))
    }
}

fun LedgerSnapshot.planningAssessmentStatuses(history: PlanningHistory): Map<PlanningAssessmentId, PlanningAssessmentStatus> {
    val records = universe.planningRecords.associateBy { it.id }
    val observed = CurrencyEvaluation.observations(this)
    val currency = currency()
    val missing = (universe.tasks.flatMap { it.requiredExtensions } + universe.planningRecords.flatMap { it.requiredExtensions }).isNotEmpty()
    return history.assessments.mapValues { (_, assessment) ->
        val reasons = mutableListOf<String>()
        if (history.assessmentHeads[assessment.planning] != assessment.id) reasons += "superseded by a later explicit assessment"
        if (history.heads[assessment.planning] != assessment.reviewedHead) reasons += "planning scope revision changed"
        val record = records.getValue(assessment.planning)
        if (assessment.observations.map { it.task }.toSet() != record.tasks.toSet() || assessment.observations.any {
                val now = observed[it.task]
                now == null || now.observedContract != it.contract || now.observedInputs != it.inputs
            }) reasons += "observed member contracts or inputs changed"
        if (reasons.isNotEmpty()) PlanningAssessmentStatus(assessment.id, PlanningAssessmentCurrency.HISTORICAL, reasons)
        else {
            if (missing) reasons += "required semantic provider unavailable for planning assessment"
            if (record.tasks.any { currency.getValue(it).state != Currency.CURRENT }) reasons += "member task currency is not current"
            PlanningAssessmentStatus(assessment.id, if (reasons.isEmpty()) PlanningAssessmentCurrency.CURRENT else PlanningAssessmentCurrency.UNRESOLVED, reasons)
        }
    }
}
