package io.brule.tasking.repository

import io.brule.tasking.core.*
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class PlanningLedgerTest {
    @TempDir lateinit var directory: Path
    private val task = DraftRecord(TaskId.parseOrThrow("TASK.a"), "A", "open", "Persist result", emptyList(), listOf("Durable"), listOf("Restart"),
        emptyList(), obj(), "tasking/core-draft-2", listOf("test"))
    private val opaque = obj("test.opaque/v1" to obj("whole" to DecimalValue(BigDecimal("1.00E+30")), "fraction" to DecimalValue(BigDecimal("0.000000000000000000000123"))))
    private val roadmap = DraftRoadmap(RoadmapId.parseOrThrow("ROADMAP.r"), "R", "Durable line", listOf(task.id), extensions = opaque)
    private val epic = DraftEpic(EpicId.parseOrThrow("EPIC.e"), "E", "Feature", listOf(task.id))
    private val audit = PlanningAudit("reviewer", OccurredAt.parseOrThrow("2026-09-07T00:00:00Z"), "Reviewed scope", mapOf("review" to "Bounded evidence"))
    private fun create(name: String = "project"): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap")
        Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "test launcher\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val root = directory.resolve(name)
        Bootstrap.apply(Bootstrap.plan(root, "planning.test", "test", distribution.parent, lock,
            Transition.AddRecords(listOf(task), listOf(roadmap), listOf(epic))))
        Files.writeString(root.resolve("source.txt"), "Unrelated source\n")
        return FileTaskLedger(root)
    }
    private fun image(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString().replace('\\', '/') to Pair(Files.readString(it), Files.getLastModifiedTime(it))
    } }
    private fun taskImage(root: Path) = image(root).filterKeys {
        it.startsWith(".agents/tasks/") || it.startsWith(".agents/history/") || it.startsWith(".agents/receipts/") || it == "source.txt"
    }
    private fun paths(plan: ObjectValue): List<String> = plan.requiredArray("writes").map { (it as ObjectValue).requiredString("path") }
    private fun tracked(): FileTaskLedger = create().also { it.apply(it.snapshot().revision, Transition.TrackPlanning) }
    private fun FileTaskLedger.amend(record: PlanningRecord): TransitionResult {
        val snapshot = snapshot()
        return apply(snapshot.revision, Transition.AmendPlanning(PlanningAmendment(snapshot.planningHistory!!.heads.getValue(record.id), record, audit)))
    }

    @Test fun `explicit baseline adoption fences older writers and preserves every legacy record and task file`() {
        val ledger = create()
        val before = ledger.snapshot()
        val files = image(ledger.root)
        val plan = ledger.plan(before.revision, Transition.TrackPlanning)
        assertEquals(files, image(ledger.root))
        assertTrue(paths(plan).all { it == ".agents/config.toml" || it.startsWith(".agents/planning-history/") })
        assertEquals(4, paths(plan).size) // Config, HEADs, two independent baseline roots.
        val unrelated = taskImage(ledger.root)
        ledger.apply(before.revision, Transition.TrackPlanning)
        val after = FileTaskLedger(ledger.root).snapshot()
        assertEquals(before.universe, after.universe)
        assertEquals(before.history, after.history)
        assertEquals(unrelated, taskImage(ledger.root))
        assertEquals(before.revision, after.planningHistory!!.origin)
        assertEquals(opaque, after.universe.roadmaps.single().extensions)
        assertContains(Files.readString(ledger.root.resolve(".agents/config.toml")), "taskctl.native/alpha4")
        val stable = image(ledger.root)
        assertEquals(after, FileTaskLedger(ledger.root).snapshot())
        assertEquals(stable, image(ledger.root))
        assertFailsWith<RevisionConflict> { ledger.apply(before.revision, Transition.TrackPlanning) }
        assertEquals(stable, image(ledger.root))
        val legacy = roadmap.copy(id = RoadmapId.parseOrThrow("ROADMAP.legacy"))
        assertFails { ledger.apply(after.revision, Transition.AddRecords(roadmaps = listOf(legacy))) }
    }

    @Test fun `planning mutations and current assessments survive restart without touching task history`() {
        val ledger = tracked()
        val before = taskImage(ledger.root)
        ledger.amend(roadmap.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, acceptance = listOf("Feature works")))
        var snapshot = FileTaskLedger(ledger.root).snapshot()
        val history = requireNotNull(snapshot.planningHistory)
        val assertion = PlanningAssessment(null, roadmap.id, history.heads.getValue(roadmap.id), snapshot.planningObservations(snapshot.universe.planning(roadmap.id)),
            PlanningAssessmentOutcome.ACCEPTED, audit, listOf("Observed feature result"))
        val oldRevision = snapshot.revision
        val plan = ledger.plan(oldRevision, Transition.AssessPlanning(assertion))
        assertTrue(paths(plan).all { it.startsWith(".agents/planning-history/") })
        ledger.apply(oldRevision, Transition.AssessPlanning(assertion))
        snapshot = FileTaskLedger(ledger.root).snapshot()
        val assessedHistory = requireNotNull(snapshot.planningHistory)
        assertEquals(PlanningAssessmentCurrency.CURRENT, snapshot.planningAssessmentStatuses(assessedHistory).getValue(assertion.id).currency)
        assertEquals(before, taskImage(ledger.root))
        val image = image(ledger.root)
        assertFailsWith<RevisionConflict> { ledger.apply(oldRevision, Transition.AssessPlanning(assertion)) }
        assertEquals(image, image(ledger.root))
        val archive = PlanningDispositionChange(roadmap.id, assessedHistory.heads.getValue(roadmap.id), PlanningDisposition.ARCHIVED, audit)
        val archivePlan = ledger.plan(snapshot.revision, Transition.SetPlanningDisposition(archive))
        assertTrue(paths(archivePlan).all { it.startsWith(".agents/roadmaps/") || it.startsWith(".agents/planning-history/") })
        ledger.apply(snapshot.revision, Transition.SetPlanningDisposition(archive))
        val archived = FileTaskLedger(ledger.root).snapshot()
        val archivedHistory = requireNotNull(archived.planningHistory)
        assertEquals(PlanningDisposition.ARCHIVED, archived.universe.planning(roadmap.id).disposition)
        assertEquals(PlanningAssessmentCurrency.HISTORICAL, archived.planningAssessmentStatuses(archivedHistory).getValue(assertion.id).currency)
        assertEquals(assertion, archivedHistory.assessments.getValue(assertion.id))
        assertTrue(assessedHistory.revisions.all { (id, value) -> archivedHistory.revisions[id] == value })
        assertEquals(before, taskImage(ledger.root))
        assertEquals(opaque, archived.universe.planning(roadmap.id).extensions)
        assertEquals(listOf(task.id), ledger.frontier().tasks)
        assertEquals(listOf(task.id), ledger.frontier(FrontierQuery(roadmap = roadmap.id)).tasks)
    }

    @Test fun `old planning heads unknown fields and tampered immutable objects fail before writes`() {
        val ledger = tracked()
        val original = ledger.snapshot()
        val stale = PlanningAmendment(original.planningHistory!!.heads.getValue(roadmap.id),
            roadmap.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, title = "Stale request"), audit)
        ledger.amend(roadmap.copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, title = "Current title"))
        val stable = image(ledger.root)
        assertFails { ledger.apply(ledger.snapshot().revision, Transition.AmendPlanning(stale)) }
        assertEquals(stable, image(ledger.root))
        val current = ledger.snapshot()
        val id = current.planningHistory!!.heads.getValue(roadmap.id)
        val path = ledger.root.resolve(".agents/planning-history/revisions/${id.value.removePrefix("sha256:")}.json")
        val text = Files.readString(path)
        Files.writeString(path, text.replace("Current title", "Tampered title"))
        assertFails { FileTaskLedger(ledger.root).snapshot() }
        Files.writeString(path, text)
        val headPath = ledger.root.resolve(".agents/planning-history/heads.json")
        val heads = NativeFiles.objectValue(Files.readString(headPath))
        Files.writeString(headPath, Json.encode(ObjectValue(heads.fields + ("implicit_acceptance" to BooleanValue(true)))))
        assertFails { FileTaskLedger(ledger.root).snapshot() }
    }

    private fun interruptedTrack(ledger: FileTaskLedger): Map<String, String> {
        val before = ledger.snapshot()
        val next = LedgerTransitions.evolve(before, Transition.TrackPlanning)
        val history = requireNotNull(next.planningHistory)
        val config = Files.readString(ledger.root.resolve(".agents/config.toml"))
        val writes = linkedMapOf(".agents/config.toml" to config.replace("taskctl.native/alpha2", "taskctl.native/alpha4"),
            ".agents/planning-history/heads.json" to (Json.encode(PlanningHistoryCodec.heads(history)) + "\n"))
        history.revisions.forEach { (id, value) -> writes[".agents/planning-history/revisions/${id.value.removePrefix("sha256:")}.json"] = Json.encode(PlanningHistoryCodec.revision(value)) + "\n" }
        val operation = obj("contract" to StringValue("taskctl.transaction/alpha1"),
            "before" to ObjectValue(writes.keys.associateWith { if (it == ".agents/config.toml") StringValue(config) else NullValue }), "after" to stringMap(writes))
        NativeFiles.atomicWrite(ledger.root, ".agents/runtime/transaction.json", Json.encode(operation))
        return writes
    }

    @Test fun `planning recovery completes partial writes and rejects external edits before any new write`() {
        val ledger = create()
        val before = taskImage(ledger.root)
        val writes = interruptedTrack(ledger)
        NativeFiles.atomicWrite(ledger.root, ".agents/config.toml", writes.getValue(".agents/config.toml"))
        assertFails { ledger.snapshot() }
        ledger.recover()
        assertNotNull(FileTaskLedger(ledger.root).snapshot().planningHistory)
        assertEquals(before, taskImage(ledger.root))
        val conflicted = create("conflict")
        interruptedTrack(conflicted)
        val config = conflicted.root.resolve(".agents/config.toml")
        Files.writeString(config, Files.readString(config) + "# Deliberate external edit\n")
        val conflictImage = image(conflicted.root)
        assertFails { conflicted.recover() }
        assertEquals(conflictImage, image(conflicted.root).filterKeys { it != ".agents/runtime/lock" })
        assertFalse(Files.exists(conflicted.root.resolve(".agents/planning-history")))
    }
}
