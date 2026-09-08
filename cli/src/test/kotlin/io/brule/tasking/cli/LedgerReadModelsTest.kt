package io.brule.tasking.cli

import io.brule.tasking.core.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class LedgerReadModelsTest {
    private fun task(id: String = "TASK.a") = DraftRecord(TaskId.parseOrThrow(id), "Durable result", "open", "Persist the result.",
        emptyList(), listOf("Durable"), listOf("Restarts"), emptyList(), obj(), "tasking/core-draft-2", listOf("check"))
    private fun empty() = LedgerSnapshot("read-model-test", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial()))
    private fun ObjectValue.objectAt(key: String) = assertIs<ObjectValue>(fields.getValue(key))
    private fun ObjectValue.number(key: String) = assertIs<IntegerValue>(fields.getValue(key)).value.intValueExact()
    private fun ObjectValue.flag(key: String) = assertIs<BooleanValue>(fields.getValue(key)).value
    private fun List<Value>.objects() = map { assertIs<ObjectValue>(it) }
    private fun context(snapshot: LedgerSnapshot) = LedgerReadModels.context(snapshot).also {
        assertTrue(Json.encode(it).toByteArray(Charsets.UTF_8).size <= LedgerReadModels.CONTEXT_MAX_BYTES)
        assertTrue(it.requiredArray("work").size <= LedgerReadModels.CONTEXT_MAX_ITEMS)
    }

    @Test fun `empty diagnostics briefing and snapshot have distinct explicit contracts`() {
        val value = empty()
        val doctor = LedgerReadModels.doctor(value)
        val briefing = context(value)
        val full = LedgerReadModels.snapshot(value)
        assertEquals("taskctl.doctor/alpha1", doctor.requiredString("contract"))
        assertEquals("taskctl.context/alpha1", briefing.requiredString("contract"))
        assertEquals("taskctl.snapshot/alpha1", full.requiredString("contract"))
        assertEquals("ok", doctor.requiredString("health")); assertTrue(doctor.flag("valid"))
        assertTrue(doctor.requiredArray("diagnostics").isEmpty()); assertFalse("work" in doctor.fields)
        assertEquals(0, doctor.number("tasks")); assertEquals(0, briefing.number("omitted_items"))
        assertFalse(briefing.flag("truncated")); assertFalse(briefing.flag("complete_ledger"))
        assertTrue(full.objectAt("records").requiredArray("tasks").isEmpty())
        assertIs<ObjectValue>(full.fields["history"])
        for (projection in listOf(doctor, briefing, full)) assertEquals(value.revision.value, projection.requiredString("revision"))
    }

    @Test fun `large records and graph produce bounded honest context and complete precise snapshot`() {
        val opaque = obj("huge" to IntegerValue(BigInteger("900719925474099312345678901234567890")),
            "decimal" to DecimalValue(BigDecimal("0.123456789012345678901234567890")), "secret_note" to StringValue("complete-state-only"))
        val tasks = (1..160).map { task("TASK.large.${it.toString().padStart(3, '0')}").copy(
            title = "🧬".repeat(1_000), intent = "Complete bounded work. ".repeat(500), extensions = obj("test.opaque/v1" to opaque)) }
        val longIdentity = task("TASK." + "long".repeat(600))
        val value = LedgerTransitions.evolve(empty(), Transition.AddRecords(tasks + longIdentity)).copy(repositoryId = "🧬".repeat(5_000))
        val briefing = context(value)
        assertEquals(161, briefing.objectAt("counts").number("tasks"))
        assertEquals(12, briefing.requiredArray("work").size)
        assertEquals(149, briefing.number("omitted_items")); assertEquals(1, briefing.number("omitted_overlong_identities"))
        assertTrue(briefing.flag("truncated")); assertTrue(briefing.objectAt("repository_display").flag("truncated"))
        assertFalse(Json.encode(briefing).contains("complete-state-only"))
        assertContains(briefing.requiredArray("follow_up"), StringValue("snapshot --format json"))
        for (item in briefing.requiredArray("work").objects()) {
            assertNotNull(TaskId.parse(item.requiredString("id")))
            assertTrue(item.flag("text_truncated"))
            val title = item.requiredString("title")
            assertEquals(160, title.codePointCount(0, title.length)); assertFalse(title.last().isHighSurrogate())
        }
        val full = LedgerReadModels.snapshot(value)
        assertEquals(value.repositoryId, full.requiredString("repository_id"))
        val decoded = full.objectAt("records").requiredArray("tasks").map { DraftDocument.parse(Json.encode(it)).record }.associateBy { it.id }
        assertEquals((tasks + longIdentity).associateBy { it.id }, decoded)
        assertEquals(opaque, decoded.getValue(tasks.first().id).extensions.fields["test.opaque/v1"])
    }

    @Test fun `escaped text and long valid identities respect the encoded byte budget`() {
        val tasks = (1..20).map { task("TASK." + "a".repeat(500) + it.toString().padStart(2, '0')).copy(
            title = "\u0000".repeat(160), intent = "\u0000".repeat(240)) }
        val value = LedgerTransitions.evolve(empty(), Transition.AddRecords(tasks))
        val briefing = context(value)
        assertTrue(briefing.requiredArray("work").size in 1 until LedgerReadModels.CONTEXT_MAX_ITEMS)
        assertTrue(briefing.number("omitted_items") > 0)
        assertEquals(0, briefing.number("omitted_overlong_identities"))
        assertTrue(briefing.flag("truncated"))
        assertTrue(briefing.requiredArray("work").objects().all { item -> tasks.any { it.id.value == item.requiredString("id") } })
    }

    @Test fun `missing task or planning providers remain diagnostic and never gain readiness`() {
        val plain = task()
        val required = plain.copy(id = TaskId.parseOrThrow("TASK.restricted"), requiredExtensions = listOf("test.host/v1"),
            extensions = obj("test.host/v1" to obj("host" to StringValue("unavailable"))))
        for (value in listOf(
            LedgerTransitions.evolve(empty(), Transition.AddRecords(listOf(plain, required))),
            LedgerTransitions.evolve(empty(), Transition.AddRecords(listOf(plain), roadmaps = listOf(
                DraftRoadmap(RoadmapId.parseOrThrow("ROADMAP.restricted"), "Restricted", "Requires a provider", listOf(plain.id), listOf("test.host/v1"))))),
        )) {
            val doctor = LedgerReadModels.doctor(value)
            assertEquals("blocked", doctor.requiredString("health"))
            assertEquals(listOf(StringValue("test.host/v1")), doctor.requiredArray("required_capabilities_unavailable"))
            assertEquals("REQUIRED_CAPABILITY_UNAVAILABLE", doctor.requiredArray("diagnostics").objects().first().requiredString("code"))
            assertEquals(0, context(value).objectAt("counts").number("ready"))
            assertTrue(LedgerReadModels.snapshot(value).objectAt("derived").requiredArray("frontier").isEmpty())
        }
    }

    @Test fun `legacy identity readiness and tracked currency retain their separate meanings`() {
        val a = task().copy(state = "closed")
        val b = task("TASK.b").copy(requires = listOf(a.id))
        val receipt = ClosureEvidence(Receipt(a.id, DraftLifecycle.contract(a), mapOf("check" to "Historical result")), "actor", LegacyRecordedAt.parseOrThrow("historical time"))
        val legacy = LedgerSnapshot("legacy", Revision.initial(), DraftUniverse(listOf(a, b)), receipts = listOf(receipt))
        assertEquals(Currency.UNRESOLVED, legacy.currency().getValue(b.id).state)
        val item = context(legacy).requiredArray("work").objects().single { it.requiredString("id") == b.id.value }
        assertEquals("ready", item.requiredString("category")); assertEquals("unresolved", item.requiredString("currency"))
        assertEquals(NullValue, LedgerReadModels.snapshot(legacy).fields["history"])
        val tracked = LedgerTransitions.evolve(legacy.copy(universe = DraftUniverse(listOf(task(), b))), Transition.TrackHistory)
        assertEquals("needs_review", context(tracked).requiredArray("work").objects().single { it.requiredString("id") == b.id.value }.requiredString("category"))
        assertEquals("attention", LedgerReadModels.doctor(tracked).requiredString("health"))
    }

    @Test fun `snapshot retains all revisions old receipts planning and explicit dependency bindings`() {
        val a = task()
        val b = task("TASK.b").copy(requires = listOf(a.id))
        val roadmap = DraftRoadmap(RoadmapId.parseOrThrow("ROADMAP.r"), "Lane", "Advance", listOf(b.id, a.id))
        val epic = DraftEpic(EpicId.parseOrThrow("EPIC.e"), "Capability", "Across lanes", listOf(a.id))
        var value = LedgerTransitions.evolve(empty(), Transition.AddRecords(listOf(a, b), listOf(roadmap), listOf(epic)))
        val receipt = ClosureEvidence(Receipt(a.id, DraftLifecycle.contract(a), mapOf("check" to "Observed")), "actor", LegacyRecordedAt.parseOrThrow("historical arbitrary timestamp"))
        value = LedgerTransitions.evolve(value, Transition.CloseTask(receipt))
        value = LedgerTransitions.evolve(value, Transition.ReviseTask(a.copy(state = "closed", acceptance = listOf("A stronger result"))))
        value = value.copy(dependencyBindings = mapOf(b.id to listOf(Dependency(a.id))))
        val full = LedgerReadModels.snapshot(value)
        val history = full.objectAt("history")
        val revisions = history.objectAt("revisions").fields.map { (id, revision) ->
            TaskRevisionId.parseOrThrow(id) to HistoryCodec.decodeRevision(assertIs<ObjectValue>(revision))
        }.toMap()
        assertEquals(value.history, HistoryCodec.decodeHeads(history.objectAt("heads"), revisions))
        assertEquals(listOf(receipt), full.requiredArray("receipts").objects().map(NativeCodec::decodeEvidence))
        assertEquals(roadmap, PlanningRecordCodec.decode(full.objectAt("records").requiredArray("roadmaps").objects().single()))
        assertEquals(epic, PlanningRecordCodec.decode(full.objectAt("records").requiredArray("epics").objects().single()))
        assertEquals(listOf(Dependency(a.id)), full.objectAt("dependency_bindings").requiredArray(b.id.value).objects().map(HistoryCodec::decodeDependency))
        assertEquals(value.currency().values.sortedBy { it.task }.map(HistoryCodec::currency), full.objectAt("derived").requiredArray("currency"))
        val reordered = value.copy(universe = value.universe.copy(tasks = value.universe.tasks.reversed()),
            history = value.history!!.copy(revisions = value.history!!.revisions.entries.reversed().associate { it.toPair() }))
        assertEquals(Json.encode(full), Json.encode(LedgerReadModels.snapshot(reordered)))
        assertEquals(Json.encode(context(value)), Json.encode(context(reordered)))
    }

    @Test fun `complete snapshot preserves import source bytes classification and manifest identity`() {
        val historical = task().copy(state = "closed")
        val source = "fictional historical closure\n  exact decimal spelling: 0.123456789012345678901234567890\n"
        val manifest = ImportManifest("fictional-source", "a".repeat(40), "test-read-model", "1.0.0", mapOf("task.txt" to source),
            listOf(ImportSource(historical.id, "task.txt", "closed", Canonical.sha256(source.toByteArray()), DraftLifecycle.contract(historical))), DraftUniverse(listOf(historical)))
        val admission = ImportAdmission(manifest, ImportReview(manifest.id, "reviewer", LegacyRecordedAt.parseOrThrow("historical time"), "Fictional test review"))
        val value = LedgerTransitions.evolve(empty(), Transition.ImportRecords(admission))
        val encoded = LedgerReadModels.snapshot(value)
        assertEquals("taskctl.native/alpha3", encoded.requiredString("protocol"))
        val decoded = ImportCodec.decodeAdmission(encoded.requiredArray("imports").objects().single())
        assertEquals(admission, decoded); assertEquals(source, decoded.manifest.files.getValue("task.txt"))
        assertEquals(manifest.id, decoded.manifest.id)
        assertEquals(0, encoded.requiredArray("receipts").size)
        assertEquals("needs_review", context(value).requiredArray("work").objects().single().requiredString("category"))
    }

    @Test fun `readiness projections preserve the ledger dependency binding guard`() {
        val a = task()
        val b = task("TASK.b")
        val value = LedgerTransitions.evolve(empty(), Transition.AddRecords(listOf(a, b)))
            .copy(dependencyBindings = mapOf(b.id to listOf(Dependency(a.id))))
        assertTrue(value.dependencyProblems().isNotEmpty())
        assertFails { context(value) }
        assertFails { LedgerReadModels.snapshot(value) }
    }
}
