package io.brule.tasking.conformance

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class AssertionInputBoundaryTest {
    @TempDir lateinit var directory: Path
    private val time = OccurredAt.parseOrThrow("2026-09-08T04:00:00.123456789+00:00")
    private val task = DraftRecord(TaskId.parseOrThrow("TASK.assertion.a"), "A", "open", "Preserve valid assertions", emptyList(),
        listOf("Durable result"), listOf("Observed"), emptyList(), obj(), "tasking/core-draft-2", listOf("test"))
    private val roadmap = DraftRoadmap(RoadmapId.parseOrThrow("ROADMAP.assertion"), "Assertions", "Durable line", listOf(task.id))
    private fun planningAudit(evidence: Map<String, String> = mapOf("test" to "Observed")) = PlanningAudit("reviewer", time, "Reviewed scope", evidence)
    private fun profileAudit(evidence: Map<String, String> = mapOf("test" to "Observed")) = ProfileAudit("reviewer", time, "Reviewed profile", evidence)
    private fun profile() = EffectiveProfile(ProfileId.parseOrThrow("specimen.assertion/v1"), emptyMap())
    private fun create(): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "fixture launcher\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val root = directory.resolve("ledger")
        Bootstrap.apply(Bootstrap.plan(root, "specimen.assertion", "test", distribution.parent, lock, Transition.AddRecords(listOf(task), listOf(roadmap))))
        Files.writeString(root.resolve("product.txt"), "Unrelated bytes\n")
        return FileTaskLedger(root)
    }
    private fun tracked(): FileTaskLedger = create().also { it.apply(it.snapshot().revision, Transition.TrackPlanning) }
    private fun image(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString() to Pair(Files.readString(it), Files.getLastModifiedTime(it))
    } }
    private fun reject(ledger: FileTaskLedger, transition: Transition) {
        val snapshot = ledger.snapshot(); val before = image(ledger.root)
        assertFails { LedgerTransitions.reduce(snapshot, transition) }
        assertFails { LedgerTransitions.evolve(snapshot, transition) }
        assertFails { ledger.plan(snapshot.revision, transition) }
        assertFails { ledger.apply(snapshot.revision, transition) }
        assertEquals(before, image(ledger.root)); assertEquals(snapshot, FileTaskLedger(ledger.root).snapshot())
    }
    private fun criteria(ledger: FileTaskLedger): DraftRoadmap {
        val value = roadmap.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, acceptance = listOf("Observed criterion"))
        ledger.apply(ledger.snapshot().revision, Transition.AmendPlanning(PlanningAmendment(ledger.snapshot().planningHistory!!.heads.getValue(roadmap.id), value, planningAudit())))
        return value
    }

    @Test fun `mutated profile audit evidence cannot become accepted state or a writable plan`() {
        val ledger = create()
        for (replacement in listOf(emptyMap(), mapOf("" to "value"), mapOf("test" to " "))) {
            val borrowed = mutableMapOf("test" to "Initially valid")
            val audit = profileAudit(borrowed); borrowed.clear(); borrowed.putAll(replacement)
            reject(ledger, Transition.SetProfile(ProfileChange(null, profile(), audit)))
        }
    }
    @Test fun `planning amendment and disposition refuse mutated audit evidence before writes`() {
        val ledger = tracked(); val snapshot = ledger.snapshot()
        val evidence = mutableMapOf("test" to "Valid"); val audit = planningAudit(evidence); evidence.clear()
        val head = snapshot.planningHistory!!.heads.getValue(roadmap.id)
        reject(ledger, Transition.AmendPlanning(PlanningAmendment(head, roadmap.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, title = "Changed"), audit)))
        reject(ledger, Transition.SetPlanningDisposition(PlanningDispositionChange(roadmap.id, head, PlanningDisposition.ARCHIVED, audit)))
    }
    @Test fun `planning acceptance rejects criterion evidence made blank after construction`() {
        val ledger = tracked(); val record = criteria(ledger); val snapshot = ledger.snapshot()
        val criteria = mutableListOf("Initially valid")
        val assertion = PlanningAssessment(null, record.id, snapshot.planningHistory!!.heads.getValue(record.id), snapshot.planningObservations(record),
            PlanningAssessmentOutcome.ACCEPTED, planningAudit(), criteria)
        criteria[0] = " "
        reject(ledger, Transition.AssessPlanning(assertion))
    }
    @Test fun `planning assessment refuses an emptied audit even when member observations remain exact`() {
        val ledger = tracked(); val record = criteria(ledger); val snapshot = ledger.snapshot()
        val borrowed = mutableMapOf("test" to "Valid"); val audit = planningAudit(borrowed)
        val assertion = PlanningAssessment(null, record.id, snapshot.planningHistory!!.heads.getValue(record.id), snapshot.planningObservations(record),
            PlanningAssessmentOutcome.ACCEPTED, audit, listOf("Observed result"))
        borrowed.clear()
        reject(ledger, Transition.AssessPlanning(assertion))
    }
    @Test fun `receipt and review guards reject removed required evidence and changed observations`() {
        val ledger = create(); val snapshot = ledger.snapshot()
        val receiptEvidence = mutableMapOf("test" to "Valid")
        val receipt = ClosureEvidence(Receipt(task.id, DraftLifecycle.contract(task), receiptEvidence), "reviewer", time)
        receiptEvidence.clear(); reject(ledger, Transition.CloseTask(receipt))
        val reviewEvidence = mutableMapOf("test" to "Valid")
        val observations = mutableListOf<Dependency>()
        val review = Reconciliation(task.id, snapshot.history!!.heads.getValue(task.id), ReviewOutcome.REVALIDATED, observations,
            "reviewer", time, "Reviewed current work", reviewEvidence)
        reviewEvidence.clear(); reject(ledger, Transition.ReconcileTask(review))
        reviewEvidence["test"] = "Valid"
        observations += Dependency(task.id); observations += Dependency(task.id)
        reject(ledger, Transition.ReconcileTask(review))
    }
    @Test fun `valid historical receipts and typed planning profile assertions remain reconstructable after caller edits`() {
        val ledger = tracked(); val record = criteria(ledger)
        val oldTime = LegacyRecordedAt("legacy narrative time without a calendar")
        val receipt = ClosureEvidence(Receipt(task.id, DraftLifecycle.contract(task), mapOf("test" to "Observed")), "reviewer", oldTime)
        ledger.apply(ledger.snapshot().revision, Transition.CloseTask(receipt))
        val auditMap = mutableMapOf("test" to "Observed"); val audit = planningAudit(auditMap)
        val snapshot = ledger.snapshot(); val criterionEvidence = mutableListOf("Observed criterion")
        val assessment = PlanningAssessment(null, record.id, snapshot.planningHistory!!.heads.getValue(record.id), snapshot.planningObservations(record),
            PlanningAssessmentOutcome.ACCEPTED, audit, criterionEvidence)
        val assessmentBytes = Json.encode(PlanningHistoryCodec.assessment(assessment))
        ledger.apply(snapshot.revision, Transition.AssessPlanning(assessment))
        val profileEvidence = mutableMapOf("test" to "Observed"); val profileAudit = profileAudit(profileEvidence)
        val profileAuditBytes = Json.encode(ProfileCodec.audit(profileAudit))
        ledger.apply(ledger.snapshot().revision, Transition.SetProfile(ProfileChange(null, profile(), profileAudit)))
        auditMap.clear(); criterionEvidence.clear(); profileEvidence.clear()
        val before = image(ledger.root); val cold = FileTaskLedger(ledger.root).snapshot()
        assertEquals(listOf(receipt), cold.receipts); assertEquals(oldTime, cold.receipts.single().time)
        assertEquals(assessmentBytes, Json.encode(PlanningHistoryCodec.assessment(cold.planningHistory!!.assessments.values.single())))
        assertEquals(profileAuditBytes, Json.encode(ProfileCodec.audit(cold.profileHistory!!.revisions.values.single().audit)))
        assertEquals(before, image(ledger.root))
    }
    @Test fun `provider verification receives read only evidence and cannot mutate receipts or reviews`() {
        val evidence = mutableMapOf("test" to "Valid")
        val provider = object : SemanticProvider {
            override val identity = "specimen.mutating/v1"
            override val pin = "test-pin"
            override fun evaluate(record: DraftRecord) = Contribution()
            override fun verify(record: DraftRecord, evidence: Map<String, String>): List<String> {
                (evidence as? MutableMap<*, *>)?.clear()
                return emptyList()
            }
        }
        val legacyProfile = Profile(mapOf(provider.identity to provider.pin))
        val receipt = Receipt(task.id, DraftLifecycle.contract(task, legacyProfile), evidence)
        assertTrue(DraftLifecycle.closureProblems(listOf(task), task.id, receipt, legacyProfile, listOf(provider)).any { "verification failed" in it })
        assertEquals(mapOf("test" to "Valid"), evidence)

        val feature = ExtensionId.parseOrThrow("specimen.mutating/v1")
        val pin = ProviderPin(ProviderId.parseOrThrow("specimen.mutating-provider/v1"), ProviderVersion.parseOrThrow("1.0.0"), ProviderDigest.parseOrThrow("sha256:" + "a".repeat(64)))
        val typed = object : PinnedSemanticProvider {
            override val feature = feature
            override val pin = pin
            override fun evaluate(record: ProviderTask) = Contribution()
            override fun verify(record: ProviderTask, evidence: Map<String, String>): List<String> {
                (evidence as? MutableMap<*, *>)?.clear()
                return emptyList()
            }
        }
        val initial = create(); val ledger = FileTaskLedger(initial.root, ProviderRegistry(listOf(typed)))
        ledger.apply(ledger.snapshot().revision, Transition.SetProfile(ProfileChange(null,
            EffectiveProfile(ProfileId.parseOrThrow("specimen.mutating/v1"), mapOf(feature to pin)), profileAudit())))
        val snapshot = ledger.snapshot()
        val review = Reconciliation(task.id, snapshot.history!!.heads.getValue(task.id), ReviewOutcome.REVALIDATED, emptyList(),
            "reviewer", time, "Reviewed current profile", evidence, profile = snapshot.profileHistory!!.profile.digest)
        reject(ledger, Transition.ReconcileTask(review))
        assertEquals(mapOf("test" to "Valid"), evidence)
    }
}
