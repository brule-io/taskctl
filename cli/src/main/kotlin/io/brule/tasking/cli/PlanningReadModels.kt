package io.brule.tasking.cli

import io.brule.tasking.core.*

/** Planning inspection never grants member-task execution or infers group acceptance. */
internal object PlanningReadModels {
    fun complete(snapshot: LedgerSnapshot): ObjectValue {
        val history = requireNotNull(snapshot.planningHistory)
        val statuses = snapshot.planningAssessmentStatuses(history)
        return obj("heads" to PlanningHistoryCodec.heads(history),
            "revisions" to ObjectValue(history.revisions.entries.sortedBy { it.key.value }.associate { it.key.value to PlanningHistoryCodec.revision(it.value) }),
            "assessments" to ObjectValue(history.assessments.entries.sortedBy { it.key.value }.associate { it.key.value to PlanningHistoryCodec.assessment(it.value) }),
            "assessment_currency" to ArrayValue(statuses.values.sortedBy { it.assessment.value }.map(PlanningHistoryCodec::status)))
    }

    fun summary(snapshot: LedgerSnapshot, statuses: Map<PlanningAssessmentId, PlanningAssessmentStatus>): ObjectValue {
        val history = requireNotNull(snapshot.planningHistory)
        return obj("tracked" to BooleanValue(true), "revisions" to integer(history.revisions.size), "assessments" to integer(history.assessments.size),
            "archived" to integer(snapshot.universe.planningRecords.count { it.disposition == PlanningDisposition.ARCHIVED }),
            "current_accepted" to integer(history.assessmentHeads.values.count { id ->
                history.assessments.getValue(id).outcome == PlanningAssessmentOutcome.ACCEPTED && statuses.getValue(id).currency == PlanningAssessmentCurrency.CURRENT
            }))
    }

    fun history(snapshot: LedgerSnapshot, id: PlanningId): ObjectValue {
        val record = snapshot.universe.planning(id)
        val history = snapshot.planningHistory
        val statuses = history?.let { snapshot.planningAssessmentStatuses(it) }.orEmpty()
        return obj("contract" to StringValue("taskctl.planning-history-view/1"), "revision" to StringValue(snapshot.revision.value),
            "planning" to StringValue(id.value), "record" to PlanningRecordCodec.encode(record), "tracked" to BooleanValue(history != null),
            "origin_ledger_revision" to optionalString(history?.origin?.value), "head" to optionalString(history?.heads?.get(id)?.value),
            "assessment_head" to optionalString(history?.assessmentHeads?.get(id)?.value),
            "revisions" to ArrayValue(history?.revisions.orEmpty().entries.filter { it.value.record.id == id }.sortedBy { it.key.value }.map {
                obj("revision" to StringValue(it.key.value), "value" to PlanningHistoryCodec.revision(it.value))
            }),
            "assessments" to ArrayValue(history?.assessments.orEmpty().entries.filter { it.value.planning == id }.sortedBy { it.key.value }.map {
                obj("assessment" to StringValue(it.key.value), "value" to PlanningHistoryCodec.assessment(it.value), "status" to PlanningHistoryCodec.status(statuses.getValue(it.key)))
            }))
    }

    fun assessmentPlan(snapshot: LedgerSnapshot, id: PlanningId): ObjectValue {
        val history = requireNotNull(snapshot.planningHistory) { "track planning history before assessment" }
        val record = snapshot.universe.planning(id)
        return obj("contract" to StringValue("taskctl.planning-assessment-plan/1"), "revision" to StringValue(snapshot.revision.value),
            "planning" to StringValue(id.value), "reviewed_head" to StringValue(history.heads.getValue(id).value),
            "parent" to optionalString(history.assessmentHeads[id]?.value), "acceptance" to strings(record.acceptance),
            "observations" to ArrayValue(snapshot.planningObservations(record).map(PlanningHistoryCodec::observation)),
            "instructions" to StringValue("Submit taskctl.planning-assessment/1 with these exact planning and member identities, an outcome, a taskctl.planning-audit/1 actor assertion, and criterion_evidence in acceptance order. Accepted requires a nonempty criterion list and evidence for each criterion. No assessment or member-task closure is inferred or recorded by this plan."))
    }
}
