package io.brule.tasking.core

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class AuditedPlanningTest {
    private val a = DraftRecord(TaskId.parseOrThrow("TASK.a"), "A", "open", "Persist A", emptyList(), listOf("Durable"), listOf("Restart"),
        emptyList(), obj(), "tasking/core-draft-2", listOf("check"))
    private val b = a.copy(id = TaskId.parseOrThrow("TASK.b"), title = "B")
    private val opaque = obj("test.opaque/v1" to obj("whole" to DecimalValue(BigDecimal("1E+30")),
        "huge" to IntegerValue(BigInteger("123456789012345678901234567890123456789")), "unknown" to ArrayValue(listOf(NullValue, BooleanValue(false)))))
    private val r = DraftRoadmap(RoadmapId.parseOrThrow("ROADMAP.r"), "R", "A durable line of advance", listOf(a.id, b.id), extensions = opaque)
    private val e = DraftEpic(EpicId.parseOrThrow("EPIC.e"), "E", "A feature across lines", listOf(b.id))
    private val audit = PlanningAudit("reviewer", OccurredAt.parseOrThrow("2026-09-07T12:00:00Z"), "Explicitly reviewed scope", mapOf("review" to "Observed bounded change"))
    private fun seeded() = LedgerTransitions.evolve(LedgerSnapshot("test", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial())),
        Transition.AddRecords(listOf(a, b), listOf(r), listOf(e)))
    private fun tracked() = LedgerTransitions.evolve(seeded(), Transition.TrackPlanning)
    private fun LedgerSnapshot.amend(record: PlanningRecord) = LedgerTransitions.evolve(this,
        Transition.AmendPlanning(PlanningAmendment(planningHistory!!.heads.getValue(record.id), record, audit)))
    private fun scoped() = tracked().amend(r.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, acceptance = listOf("Capability works", "Operator can recover")))
    private fun LedgerSnapshot.assessment(id: PlanningId = r.id, outcome: PlanningAssessmentOutcome = PlanningAssessmentOutcome.ACCEPTED): PlanningAssessment {
        val record = universe.planning(id)
        val history = requireNotNull(planningHistory)
        return PlanningAssessment(history.assessmentHeads[id], id, history.heads.getValue(id), planningObservations(record), outcome, audit,
            if (outcome == PlanningAssessmentOutcome.ACCEPTED) record.acceptance.map { "Explicit criterion observation: $it" } else emptyList())
    }
    private fun LedgerSnapshot.status(id: PlanningAssessmentId) = planningAssessmentStatuses(requireNotNull(planningHistory)).getValue(id).currency

    @Test fun `baseline adoption retains old dialect records task history and orthogonal indexes`() {
        val before = seeded()
        val after = LedgerTransitions.evolve(before, Transition.TrackPlanning)
        assertEquals(before.universe, after.universe)
        assertEquals(before.history, after.history)
        assertEquals(before.receipts, after.receipts)
        assertEquals(before.frontier(), after.frontier())
        val history = requireNotNull(after.planningHistory)
        assertEquals(before.revision, history.origin)
        assertEquals(setOf(r.id, e.id), history.heads.keys)
        assertTrue(history.revisions.values.all { it.parent == null && it.change == PlanningChange.Baseline(before.revision) })
        assertTrue(history.revisions.values.all { it.record.protocol == PlanningRecordCodec.PROTOCOL })
        assertEquals(listOf(e.id), after.universe.epicsFor(r.id))
        assertFails { LedgerTransitions.evolve(after, Transition.TrackPlanning) }
        assertFails { LedgerTransitions.evolve(before.copy(history = null), Transition.TrackPlanning) }
    }

    @Test fun `amendment archival and restoration never rewrite tasks receipts or prerequisite edges`() {
        val before = scoped()
        val amended = before.amend((before.universe.planning(r.id) as DraftRoadmap).copy(title = "New display", tasks = listOf(b.id, a.id)))
        val archive = PlanningDispositionChange(r.id, amended.planningHistory!!.heads.getValue(r.id), PlanningDisposition.ARCHIVED, audit)
        val archived = LedgerTransitions.evolve(amended, Transition.SetPlanningDisposition(archive))
        val restored = LedgerTransitions.evolve(archived, Transition.SetPlanningDisposition(archive.copy(
            reviewedHead = archived.planningHistory!!.heads.getValue(r.id), disposition = PlanningDisposition.ACTIVE)))
        for (after in listOf(amended, archived, restored)) {
            assertEquals(before.universe.tasks, after.universe.tasks)
            assertEquals(before.history, after.history)
            assertEquals(before.receipts, after.receipts)
            assertEquals(before.frontier(), after.frontier())
            assertEquals(opaque, after.universe.planning(r.id).extensions)
        }
        assertEquals(listOf(b.id, a.id), archived.universe.planning(r.id).tasks)
        assertEquals(PlanningDisposition.ARCHIVED, archived.universe.planning(r.id).disposition)
        assertEquals(PlanningDisposition.ACTIVE, restored.universe.planning(r.id).disposition)
        assertTrue(before.planningHistory!!.revisions.all { (id, record) -> restored.planningHistory!!.revisions[id] == record })
    }

    @Test fun `amendments and disposition changes require the inspected head and cannot hide scope changes`() {
        val snapshot = scoped()
        val current = snapshot.universe.planning(r.id) as DraftRoadmap
        val head = snapshot.planningHistory!!.heads.getValue(r.id)
        assertFails { snapshot.amend(current) }
        assertFails { snapshot.amend(current.copy(disposition = PlanningDisposition.ARCHIVED)) }
        assertFails { snapshot.amend(current.copy(tasks = listOf(TaskId.parseOrThrow("TASK.missing")))) }
        assertFails { LedgerTransitions.evolve(snapshot, Transition.AmendPlanning(PlanningAmendment(
            PlanningRevisionId.parseOrThrow("sha256:" + "0".repeat(64)), current.copy(title = "Changed"), audit))) }
        assertFails { LedgerTransitions.evolve(snapshot, Transition.SetPlanningDisposition(PlanningDispositionChange(r.id, head, PlanningDisposition.ACTIVE, audit))) }
        assertFails { PlanningAudit("", audit.occurredAt, audit.reason, audit.evidence) }
        assertFails { PlanningAudit(audit.actor, audit.occurredAt, " ", audit.evidence) }
        assertFails { PlanningAudit(audit.actor, audit.occurredAt, audit.reason, emptyMap()) }
    }

    @Test fun `acceptance is an explicit scoped assertion and never inferred from member lifecycle`() {
        val snapshot = scoped()
        assertTrue(snapshot.planningHistory!!.assessments.isEmpty())
        var closed = snapshot
        for (task in listOf(a, b)) closed = LedgerTransitions.evolve(closed, Transition.CloseTask(ClosureEvidence(
            Receipt(task.id, DraftLifecycle.contract(task), mapOf("check" to "Observed")), "actor", audit.occurredAt)))
        assertTrue(closed.universe.tasks.all { it.state == "closed" })
        assertTrue(closed.planningHistory!!.assessments.isEmpty())
        val assertion = snapshot.assessment()
        val assessed = LedgerTransitions.evolve(snapshot, Transition.AssessPlanning(assertion))
        assertEquals(PlanningAssessmentCurrency.CURRENT, assessed.status(assertion.id))
        assertEquals(snapshot.universe.tasks, assessed.universe.tasks) // Still open; the scope assertion is orthogonal.
        assertEquals(snapshot.history, assessed.history)
        assertEquals(snapshot.receipts, assessed.receipts)
        assertFails { LedgerTransitions.evolve(snapshot, Transition.AssessPlanning(assertion.copy(criterionEvidence = listOf("Only one criterion")))) }
        assertFails { LedgerTransitions.evolve(tracked(), Transition.AssessPlanning(tracked().assessment())) }
    }

    @Test fun `changed scope and semantic inputs retain historical assessments while cosmetic task edits do not invalidate proof`() {
        val scoped = scoped()
        val assertion = scoped.assessment()
        val assessed = LedgerTransitions.evolve(scoped, Transition.AssessPlanning(assertion))
        val cosmetic = LedgerTransitions.evolve(assessed, Transition.ReviseTask(a.copy(title = "Presentation only")))
        assertEquals(PlanningAssessmentCurrency.CURRENT, cosmetic.status(assertion.id))
        val material = LedgerTransitions.evolve(cosmetic, Transition.ReviseTask(a.copy(acceptance = listOf("Different semantic result"))))
        assertEquals(PlanningAssessmentCurrency.HISTORICAL, material.status(assertion.id))
        val changedScope = assessed.amend((assessed.universe.planning(r.id) as DraftRoadmap).copy(acceptance = listOf("A different scope criterion")))
        assertEquals(PlanningAssessmentCurrency.HISTORICAL, changedScope.status(assertion.id))
        assertEquals(assertion, changedScope.planningHistory!!.assessments.getValue(assertion.id))
        val archived = LedgerTransitions.evolve(assessed, Transition.SetPlanningDisposition(PlanningDispositionChange(r.id,
            assessed.planningHistory!!.heads.getValue(r.id), PlanningDisposition.ARCHIVED, audit)))
        assertEquals(PlanningAssessmentCurrency.HISTORICAL, archived.status(assertion.id))
    }

    @Test fun `assessment supersession is explicit and binds the exact inspected member set`() {
        val scoped = scoped()
        val first = scoped.assessment()
        val assessed = LedgerTransitions.evolve(scoped, Transition.AssessPlanning(first))
        assertFails { LedgerTransitions.evolve(assessed, Transition.AssessPlanning(first)) }
        assertFails { LedgerTransitions.evolve(scoped, Transition.AssessPlanning(first.copy(observations = first.observations.drop(1)))) }
        assertFails { LedgerTransitions.evolve(scoped, Transition.AssessPlanning(first.copy(observations = first.observations.map {
            it.copy(inputs = InputDigest.parseOrThrow("sha256:" + "0".repeat(64)))
        }))) }
        val negative = assessed.assessment(outcome = PlanningAssessmentOutcome.NOT_ACCEPTED)
        val superseded = LedgerTransitions.evolve(assessed, Transition.AssessPlanning(negative))
        assertEquals(PlanningAssessmentCurrency.HISTORICAL, superseded.status(first.id))
        assertEquals(PlanningAssessmentCurrency.CURRENT, superseded.status(negative.id))
        assertEquals(PlanningAssessmentOutcome.NOT_ACCEPTED, superseded.planningHistory!!.assessments.getValue(negative.id).outcome)
    }

    @Test fun `required capabilities cannot be bypassed by archival or acceptance assertions`() {
        val snapshot = scoped()
        val record = snapshot.universe.planning(r.id) as DraftRoadmap
        val blocked = snapshot.amend(record.copy(requiredExtensions = listOf("test.required/v1")))
        assertFails { blocked.frontier() }
        assertFails { LedgerTransitions.evolve(blocked, Transition.AssessPlanning(blocked.assessment())) }
        val unresolved = blocked.assessment(outcome = PlanningAssessmentOutcome.UNRESOLVED)
        val recorded = LedgerTransitions.evolve(blocked, Transition.AssessPlanning(unresolved))
        assertEquals(PlanningAssessmentCurrency.UNRESOLVED, recorded.status(unresolved.id))
        val archived = LedgerTransitions.evolve(recorded, Transition.SetPlanningDisposition(PlanningDispositionChange(r.id,
            recorded.planningHistory!!.heads.getValue(r.id), PlanningDisposition.ARCHIVED, audit)))
        assertFails { archived.frontier() }
    }

    @Test fun `planning codecs round trip audit history and exact extension values with distinct nominal identities`() {
        val snapshot = scoped()
        val assertion = snapshot.assessment()
        val after = LedgerTransitions.evolve(snapshot, Transition.AssessPlanning(assertion))
        val history = requireNotNull(after.planningHistory)
        val revisions = history.revisions.mapValues { (_, value) -> PlanningHistoryCodec.decodeRevision(YamlValues.parse(Json.encode(PlanningHistoryCodec.revision(value))).value as ObjectValue) }
        val assessments = history.assessments.mapValues { (_, value) -> PlanningHistoryCodec.decodeAssessment(YamlValues.parse(Json.encode(PlanningHistoryCodec.assessment(value))).value as ObjectValue) }
        assertEquals(history, PlanningHistoryCodec.decodeHeads(PlanningHistoryCodec.heads(history), revisions, assessments))
        assertEquals(audit, PlanningHistoryCodec.decodeAudit(PlanningHistoryCodec.audit(audit)))
        assertFails { PlanningHistoryCodec.decodeAudit(ObjectValue(PlanningHistoryCodec.audit(audit).fields + ("accepted_at" to StringValue(audit.occurredAt.value)))) }
        assertNull(PlanningId.parse(a.id.value))
        assertEquals(r.id, PlanningId.parse(r.id.value))
        assertEquals(e.id, PlanningId.parse(e.id.value))
        assertNull(PlanningRevisionId.parse("banana"))
        assertNull(PlanningAssessmentId.parse("sha256:bad"))
    }
}
