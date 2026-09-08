package io.brule.tasking.cli

import io.brule.tasking.core.*
import java.math.BigDecimal
import kotlin.test.*

class PlanningReadModelsTest {
    private val task = DraftRecord(TaskId.parseOrThrow("TASK.a"), "A", "open", "Persist", emptyList(), listOf("Durable"), listOf("Restart"), emptyList(), obj(), "tasking/core-draft-2", listOf("check"))
    private val roadmap = DraftRoadmap(RoadmapId.parseOrThrow("ROADMAP.r"), "R", "Line", listOf(task.id),
        extensions = obj("test.precise/v1" to DecimalValue(BigDecimal("1E+30"))))
    private val audit = PlanningAudit("actor", OccurredAt.parseOrThrow("2026-09-07T00:00:00Z"), "Reviewed", mapOf("review" to "Observed"))
    private fun legacy() = LedgerTransitions.evolve(LedgerSnapshot("test", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial())),
        Transition.AddRecords(listOf(task), listOf(roadmap)))

    @Test fun `expanded snapshot exposes every planning identity and historical assessment without changing legacy views`() {
        val legacy = legacy()
        assertEquals("taskctl.snapshot/alpha1", LedgerReadModels.snapshot(legacy).requiredString("contract"))
        assertFalse("planning_history" in LedgerReadModels.snapshot(legacy).fields)
        assertEquals(BooleanValue(false), PlanningReadModels.history(legacy, roadmap.id).fields["tracked"])
        var snapshot = LedgerTransitions.evolve(legacy, Transition.TrackPlanning)
        val updated = roadmap.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, acceptance = listOf("Feature works"))
        snapshot = LedgerTransitions.evolve(snapshot, Transition.AmendPlanning(PlanningAmendment(snapshot.planningHistory!!.heads.getValue(roadmap.id), updated, audit)))
        val assertion = PlanningAssessment(null, roadmap.id, snapshot.planningHistory!!.heads.getValue(roadmap.id), snapshot.planningObservations(updated),
            PlanningAssessmentOutcome.ACCEPTED, audit, listOf("Observed feature"))
        snapshot = LedgerTransitions.evolve(snapshot, Transition.AssessPlanning(assertion))
        val read = LedgerReadModels.snapshot(snapshot)
        assertEquals("taskctl.snapshot/alpha2", read.requiredString("contract"))
        assertEquals("taskctl.native/alpha4", read.requiredString("protocol"))
        assertEquals(snapshot.revision.value, read.requiredString("revision"))
        val planning = read.fields.getValue("planning_history") as ObjectValue
        val history = requireNotNull(snapshot.planningHistory)
        assertEquals(PlanningHistoryCodec.heads(history), planning.fields["heads"])
        assertEquals(history.revisions.keys.map { it.value }.toSet(), (planning.fields.getValue("revisions") as ObjectValue).fields.keys)
        assertEquals(PlanningHistoryCodec.assessment(assertion), (planning.fields.getValue("assessments") as ObjectValue).fields[assertion.id.value])
        val encoded = YamlValues.parse(Json.encode(read)).value as ObjectValue
        assertEquals(read, encoded)
        assertEquals(PlanningReadModels.complete(snapshot), planning)
        val plan = PlanningReadModels.assessmentPlan(snapshot, roadmap.id)
        assertEquals(StringValue(assertion.id.value), plan.fields["parent"])
        assertEquals(ArrayValue(snapshot.planningObservations(updated).map(PlanningHistoryCodec::observation)), plan.fields["observations"])
        assertEquals(legacy.universe.tasks, snapshot.universe.tasks)
    }

    @Test fun `doctor flags stale latest assessments while context remains bounded and does not infer task completion`() {
        var snapshot = LedgerTransitions.evolve(legacy(), Transition.TrackPlanning)
        val updated = roadmap.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, acceptance = listOf("Feature works"))
        snapshot = LedgerTransitions.evolve(snapshot, Transition.AmendPlanning(PlanningAmendment(snapshot.planningHistory!!.heads.getValue(roadmap.id), updated, audit)))
        val assertion = PlanningAssessment(null, roadmap.id, snapshot.planningHistory!!.heads.getValue(roadmap.id), snapshot.planningObservations(updated),
            PlanningAssessmentOutcome.ACCEPTED, audit, listOf("Observed feature"))
        snapshot = LedgerTransitions.evolve(snapshot, Transition.AssessPlanning(assertion))
        var doctor = LedgerReadModels.doctor(snapshot)
        assertEquals("taskctl.doctor/alpha2", doctor.requiredString("contract"))
        assertEquals("ok", doctor.requiredString("health"))
        assertEquals(integer(1), (doctor.fields.getValue("planning") as ObjectValue).fields["current_accepted"])
        snapshot = LedgerTransitions.evolve(snapshot, Transition.SetPlanningDisposition(PlanningDispositionChange(roadmap.id,
            snapshot.planningHistory!!.heads.getValue(roadmap.id), PlanningDisposition.ARCHIVED, audit)))
        doctor = LedgerReadModels.doctor(snapshot)
        assertEquals("attention", doctor.requiredString("health"))
        assertEquals(integer(0), (doctor.fields.getValue("planning") as ObjectValue).fields["current_accepted"])
        assertTrue(doctor.requiredArray("diagnostics").any { (it as ObjectValue).requiredString("code") == "PLANNING_ASSESSMENT_HISTORICAL" })
        val context = LedgerReadModels.context(snapshot)
        assertTrue(Json.encode(context).toByteArray().size <= LedgerReadModels.CONTEXT_MAX_BYTES)
        assertEquals(listOf(task.id.value), context.requiredArray("work").map { (it as ObjectValue).requiredString("id") })
        assertEquals(listOf(task.id), snapshot.frontier().tasks)
    }
}
