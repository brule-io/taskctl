package io.brule.tasking.conformance

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ComplexPlanningConformanceTest {
    @TempDir lateinit var directory: Path
    private val delivery = RoadmapId.parseOrThrow("ROADMAP.specimen.delivery")
    private val reliability = RoadmapId.parseOrThrow("ROADMAP.specimen.reliability")
    private val resilience = EpicId.parseOrThrow("EPIC.specimen.resilience")
    private val audit = PlanningAudit("conformance reviewer", OccurredAt.parseOrThrow("2026-09-08T00:00:00Z"), "Reviewed exact scope", mapOf("review" to "Fictional bounded observation"))
    private fun id(value: String) = ComplexPlanningFixture.taskId(value)
    private fun create(): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "fixture launcher\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val root = directory.resolve("target")
        Bootstrap.apply(Bootstrap.plan(root, "conformance.complex", "test", distribution.parent, lock, imported = ComplexPlanningFixture.admission()))
        Files.writeString(root.resolve("source.txt"), "Unrelated consumer source\n")
        return FileTaskLedger(root)
    }
    private fun image(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString().replace('\\', '/') to Pair(Files.readString(it), Files.getLastModifiedTime(it))
    } }
    private fun taskImage(root: Path) = image(root).filterKeys { it.startsWith(".agents/tasks/") || it.startsWith(".agents/history/") || it.startsWith(".agents/receipts/") || it.startsWith(".agents/imports/") || it == "source.txt" }
    private fun reconcile(ledger: FileTaskLedger, task: TaskId) {
        val snapshot = ledger.snapshot(); val record = snapshot.universe.tasks.single { it.id == task }; val observations = CurrencyEvaluation.observations(snapshot)
        val review = Reconciliation(task, snapshot.history!!.heads.getValue(task), ReviewOutcome.REVALIDATED, record.requires.sorted().map { observations.getValue(it) },
            "conformance reviewer", audit.occurredAt, "Reviewed current mapping and inputs; old assertion remains historical", mapOf("mapping" to "Explicit bounded fixture evidence"))
        ledger.apply(snapshot.revision, Transition.ReconcileTask(review))
    }
    private fun reviewed(): FileTaskLedger = create().also { ledger ->
        ledger.apply(ledger.snapshot().revision, Transition.TrackPlanning)
        listOf("W-1", "W-01", "W-2", "W-3", "W-4", "W-5").forEach { reconcile(ledger, id(it)) }
    }
    private fun amend(ledger: FileTaskLedger, record: PlanningRecord) {
        val snapshot = ledger.snapshot()
        ledger.apply(snapshot.revision, Transition.AmendPlanning(PlanningAmendment(snapshot.planningHistory!!.heads.getValue(record.id), record, audit)))
    }
    private fun assess(ledger: FileTaskLedger, scope: PlanningId): PlanningAssessment {
        val snapshot = ledger.snapshot(); val record = snapshot.universe.planning(scope); val history = snapshot.planningHistory!!
        val assertion = PlanningAssessment(history.assessmentHeads[scope], scope, history.heads.getValue(scope), snapshot.planningObservations(record),
            PlanningAssessmentOutcome.ACCEPTED, audit, record.acceptance.map { "Observed criterion: $it" })
        ledger.apply(snapshot.revision, Transition.AssessPlanning(assertion))
        return assertion
    }

    @Test fun `exact source adapter preserves overlapping indexes and unverified historical claims`() {
        val manifest = ComplexPlanningFixture.inspect()
        assertEquals(ComplexPlanningFixture.REVISION, manifest.revision)
        assertEquals(ComplexPlanningFixture.ADAPTER, manifest.adapter); assertEquals("1.0.0", manifest.adapterVersion)
        assertEquals("sha256:7c6844313efb6601b1839356790abaabcd44e40df1369c17b62725ac55f9acee", manifest.id.value)
        assertEquals(manifest, ImportCodec.decodeManifest(ImportCodec.manifest(manifest)))
        assertEquals(13, manifest.files.size); assertEquals(12, manifest.sources.size)
        assertEquals(6, manifest.universe.tasks.size); assertEquals(4, manifest.universe.tasks.count { it.state == "closed" })
        assertEquals(3, manifest.universe.roadmaps.size); assertEquals(3, manifest.universe.epics.size)
        assertNotEquals(id("W-1"), id("W-01"))
        assertEquals(listOf(delivery, reliability), manifest.universe.roadmapsFor(id("W-2")))
        assertEquals(3, manifest.universe.epicsFor(id("W-2")).size)
        assertEquals(3, manifest.universe.epicsFor(delivery).size)
        assertTrue(manifest.universe.roadmaps.single { it.id.value.endsWith("future") }.tasks.isEmpty())
        val historical = manifest.sources.single { it.id == id("W-4") }
        assertContains(manifest.files.getValue(historical.path), "\"done\": false")
        assertNotEquals(historical.contract, DraftLifecycle.contract(manifest.universe.tasks.single { it.id == historical.id }))
        val ledger = create(); val before = image(ledger.root); val snapshot = FileTaskLedger(ledger.root).snapshot()
        assertEquals(before, image(ledger.root))
        assertTrue(snapshot.receipts.isEmpty()); assertTrue(snapshot.frontier().tasks.isEmpty())
        assertTrue(snapshot.universe.planningRecords.all { it.protocol == PlanningRecordCodec.PROTOCOL && it.disposition == PlanningDisposition.ACTIVE && it.acceptance.isEmpty() })
        assertNull(snapshot.planningHistory)
        assertEquals(manifest.id, snapshot.imports.single().manifest.id)
        assertEquals(NullValue, ImportCodec.manifest(manifest).fields["source_native_protocol"])
    }

    @Test fun `native planning baseline and cross-index frontier retain independent task causality`() {
        val ledger = create(); val original = ledger.snapshot(); val unchanged = taskImage(ledger.root)
        ledger.apply(original.revision, Transition.TrackPlanning)
        val tracked = ledger.snapshot()
        assertEquals(original.history, tracked.history); assertEquals(original.imports, tracked.imports)
        assertEquals(unchanged, taskImage(ledger.root))
        assertEquals(original.revision, tracked.planningHistory!!.origin)
        assertTrue(tracked.planningHistory!!.revisions.values.all { it.change == PlanningChange.Baseline(original.revision) })
        assertTrue(tracked.planningHistory!!.assessments.isEmpty())
        assertFails { reconcile(ledger, id("W-5")) }
        listOf("W-1", "W-01", "W-2", "W-3", "W-4", "W-5").forEach { reconcile(ledger, id(it)) }
        assertEquals(listOf(id("W-3"), id("W-5")), ledger.frontier().tasks)
        assertEquals(listOf(id("W-5")), ledger.frontier(FrontierQuery(reliability, resilience)).tasks)
        assertEquals(listOf(id("W-5")), ledger.frontier(FrontierQuery(delivery, resilience)).tasks)
        assertTrue(ledger.snapshot().receipts.isEmpty()) // Revalidation never fabricates historical native receipts.
        assertEquals(original.universe.tasks.map { it.id to it.requires }.toMap(), ledger.snapshot().universe.tasks.map { it.id to it.requires }.toMap())
    }

    @Test fun `scope amendments archival and explicit assessments preserve all task and import state`() {
        val ledger = reviewed(); val before = taskImage(ledger.root); val initial = ledger.snapshot()
        val scope = initial.universe.epics.single { it.id == resilience }.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL,
            acceptance = listOf("The complete recovery scope was explicitly reviewed."))
        amend(ledger, scope)
        val accepted = assess(ledger, scope.id)
        assertTrue(ledger.snapshot().universe.tasks.any { it.state == "open" })
        val snapshot = ledger.snapshot(); val roadmap = snapshot.universe.roadmaps.single { it.id == reliability }
        val archived = Transition.SetPlanningDisposition(PlanningDispositionChange(roadmap.id, snapshot.planningHistory!!.heads.getValue(roadmap.id), PlanningDisposition.ARCHIVED, audit))
        ledger.apply(snapshot.revision, archived)
        assertEquals(listOf(id("W-5")), ledger.frontier(FrontierQuery(reliability)).tasks)
        assertEquals(PlanningAssessmentCurrency.CURRENT, ledger.snapshot().planningAssessmentStatuses(ledger.snapshot().planningHistory!!).getValue(accepted.id).currency)
        amend(ledger, scope.copy(tasks = scope.tasks.filter { it != id("W-4") }))
        val cold = FileTaskLedger(ledger.root).snapshot()
        assertEquals(PlanningAssessmentCurrency.HISTORICAL, cold.planningAssessmentStatuses(cold.planningHistory!!).getValue(accepted.id).currency)
        assertEquals(accepted, cold.planningHistory!!.assessments.getValue(accepted.id))
        assertEquals(before, taskImage(ledger.root))
        assertEquals(initial.universe.tasks, cold.universe.tasks)
        assertEquals(initial.imports, cold.imports)
        assertEquals(initial.history, cold.history)
        assertFailsWith<RevisionConflict> { ledger.apply(snapshot.revision, archived) }
    }

    @Test fun `member contract changes stale scope evidence and retain historical closures through explicit review`() {
        val ledger = reviewed(); val imported = ledger.snapshot().imports.single()
        val scope = ledger.snapshot().universe.epics.single { it.id == resilience }.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, acceptance = listOf("Recovery reviewed"))
        amend(ledger, scope); val accepted = assess(ledger, scope.id)
        val snapshot = ledger.snapshot(); val closed = snapshot.universe.tasks.single { it.id == id("W-2") }
        val oldHead = snapshot.history!!.head(closed.id)
        ledger.apply(snapshot.revision, Transition.ReviseTask(closed.copy(requirements = listOf("Revised durable fulfilment contract"))))
        var current = ledger.snapshot()
        assertEquals("closed", current.universe.tasks.single { it.id == closed.id }.state)
        assertEquals(PlanningAssessmentCurrency.HISTORICAL, current.planningAssessmentStatuses(current.planningHistory!!).getValue(accepted.id).currency)
        assertEquals(Currency.AFFECTED, current.currency().getValue(id("W-5")).state)
        assertFails { assess(ledger, scope.id) }
        reconcile(ledger, id("W-2")); reconcile(ledger, id("W-3")); reconcile(ledger, id("W-5"))
        val reviewed = assess(ledger, scope.id)
        current = FileTaskLedger(ledger.root).snapshot()
        assertEquals(accepted.id, reviewed.parent)
        assertEquals(PlanningAssessmentCurrency.CURRENT, current.planningAssessmentStatuses(current.planningHistory!!).getValue(reviewed.id).currency)
        assertEquals(oldHead, current.history!!.revisions.getValue(oldHead.id))
        assertEquals(imported, current.imports.single()); assertTrue(current.receipts.isEmpty())
        current.history!!.validate(current.universe, current.receipts, current.imports)
    }

    @Test fun `closing every member still does not accept or archive any planning scope`() {
        val ledger = reviewed()
        for (task in ledger.snapshot().universe.tasks.filter { it.state == "open" }) {
            val snapshot = ledger.snapshot()
            val receipt = ClosureEvidence(Receipt(task.id, DraftLifecycle.contract(task), mapOf("mapping" to "Current fictional result reviewed")), "conformance reviewer", audit.occurredAt)
            ledger.apply(snapshot.revision, Transition.CloseTask(receipt))
        }
        val final = FileTaskLedger(ledger.root).snapshot()
        assertTrue(final.universe.tasks.all { it.state == "closed" })
        assertTrue(final.frontier().tasks.isEmpty())
        assertEquals(2, final.receipts.size)
        assertTrue(final.planningHistory!!.assessments.isEmpty())
        assertTrue(final.universe.planningRecords.all { it.disposition == PlanningDisposition.ACTIVE && it.acceptance.isEmpty() })
    }

    @Test fun `an upstream task outside the scope invalidates its assessment through transitive inputs`() {
        val ledger = reviewed()
        val scope = ledger.snapshot().universe.epics.single { it.id == resilience }.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, acceptance = listOf("Inputs reviewed"))
        assertFalse(id("W-1") in scope.tasks)
        amend(ledger, scope); val accepted = assess(ledger, scope.id)
        val snapshot = ledger.snapshot(); val root = snapshot.universe.tasks.single { it.id == id("W-1") }
        ledger.apply(snapshot.revision, Transition.ReviseTask(root.copy(requirements = listOf("Stronger external prerequisite"))))
        reconcile(ledger, root.id); reconcile(ledger, id("W-2"))
        val current = ledger.snapshot()
        assertEquals(Currency.CURRENT, current.currency().getValue(id("W-2")).state)
        assertEquals(Currency.AFFECTED, current.currency().getValue(id("W-5")).state)
        assertEquals(PlanningAssessmentCurrency.HISTORICAL, current.planningAssessmentStatuses(current.planningHistory!!).getValue(accepted.id).currency)
        assertFails { assess(ledger, scope.id) }
        assertEquals(accepted, current.planningHistory!!.assessments.getValue(accepted.id))
    }

    @Test fun `canonical complex import preimage is available to packaged executable parity`() {
        val ledger = create(); val manifest = ledger.snapshot().imports.single().manifest
        val files = image(ledger.root).filterKeys { it.startsWith(".agents/") }.mapValues { it.value.first }
        val projection = obj("protocol" to StringValue("taskctl.conformance-preimage/1"),
            "adapter" to StringValue(manifest.adapter), "adapter_version" to StringValue(manifest.adapterVersion),
            "source_revision" to StringValue(manifest.revision), "manifest_id" to StringValue(manifest.id.value), "files" to stringMap(files))
        assertEquals(projection, YamlValues.parse(Json.encode(projection)).value)
        System.getProperty("tasking.complex.output")?.let { name ->
            val path = Path.of(name); Files.createDirectories(path.parent)
            Files.writeString(path, Json.encode(projection) + "\n")
        }
    }

    @Test fun `the adapter rejects stateful policy and graph defects instead of lowering them to annotations`() {
        val original = ComplexPlanningFixture.files()
        fun changed(suffix: String, change: (ObjectValue) -> ObjectValue): Map<String, String> {
            val path = original.keys.single { it.endsWith(suffix) }
            val value = YamlValues.parse(original.getValue(path)).value as ObjectValue
            return original + (path to Json.encode(change(value)))
        }
        for (field in listOf("host", "operator", "release_gate", "claim")) assertFails {
            ComplexPlanningFixture.inspect(changed("/work/W-3.json") { ObjectValue(it.fields + (field to StringValue("required"))) })
        }
        assertFails { ComplexPlanningFixture.inspect(changed("/work/W-3.json") { ObjectValue(it.fields + ("status" to StringValue("claimed"))) }) }
        assertFails { ComplexPlanningFixture.inspect(changed("/work/W-1.json") { ObjectValue(it.fields + ("after" to strings(listOf("W-3")))) }) }
        assertFails { ComplexPlanningFixture.inspect(changed("/lanes/delivery.json") { ObjectValue(it.fields + ("members" to strings(listOf("W-999")))) }) }
        assertFails { ComplexPlanningFixture.inspect(changed("/work/W-3.json") { ObjectValue(it.fields + ("dialect" to StringValue("tasking/core-draft-2"))) }) }
        assertEquals(ComplexPlanningFixture.files(), original)
    }
}
