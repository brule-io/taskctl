package io.brule.tasking.core

import kotlin.test.*

class PlanningUniverseTest {
    private val api = TaskId("TASK.api")
    private val ui = TaskId("TASK.ui")
    private val receipt = TaskId("TASK.receipt")
    private val delivery = RoadmapId("ROADMAP.delivery")
    private val experience = RoadmapId("ROADMAP.experience")
    private val checkout = EpicId("EPIC.checkout")
    private val observability = EpicId("EPIC.observability")

    private fun task(id: TaskId, requires: List<TaskId> = emptyList(), state: String = "open") = DraftRecord(
        id.value, id.value, state, "Implement the bounded ${id.value} transition.", requires.map { it.value },
        listOf("Persist the result."), listOf("The resulting state survives restart."), emptyList(), obj(),
    )
    private fun universe() = DraftUniverse(
        listOf(task(api), task(ui, listOf(api)), task(receipt)),
        listOf(
            DraftRoadmap(delivery, "Delivery", "Advance fulfillment and visibility.", listOf(api, receipt)),
            DraftRoadmap(experience, "Experience", "Advance the operator interface.", listOf(ui, receipt)),
        ),
        listOf(
            DraftEpic(checkout, "Checkout", "Durable user checkout.", listOf(api, ui)),
            DraftEpic(observability, "Observability", "Evidence for operators.", listOf(receipt, api)),
        ),
    )

    @Test fun `bootstrap supports zero roadmaps zero epics and empty named lines of advance`() {
        assertTrue(DraftUniverse(emptyList()).frontier().isEmpty())
        assertEquals(listOf(api), DraftUniverse(listOf(task(api))).frontier())
        val bootstrap = DraftUniverse(listOf(task(api)), listOf(DraftRoadmap(delivery, "Delivery", "Future work.")),
            listOf(DraftEpic(checkout, "Checkout", "Capability to develop.")))
        assertEquals(listOf(api), bootstrap.frontier())
        assertTrue(bootstrap.frontier(roadmap = delivery).isEmpty())
        assertTrue(bootstrap.epicsFor(api).isEmpty())
    }

    @Test fun `roadmap membership epic association and task prerequisites are orthogonal indexes`() {
        val universe = universe()
        assertTrue(universe.indexProblems().isEmpty())
        assertEquals(listOf(delivery, experience), universe.roadmapsFor(checkout))
        assertEquals(listOf(checkout, observability), universe.epicsFor(delivery))
        assertEquals(listOf(delivery, experience), universe.roadmapsFor(receipt))
        assertEquals(listOf(checkout, observability), universe.epicsFor(api))
        assertEquals(listOf(api, receipt), universe.frontier())
        assertEquals(listOf(receipt), universe.frontier(roadmap = experience))
        assertTrue(universe.frontier(roadmap = experience, epic = checkout).isEmpty())
        assertEquals(mapOf(ui to listOf(api)), universe.prerequisitesOutside(experience))
    }

    @Test fun `reordering and overlapping roadmap rows never add DAG edges or duplicate execution`() {
        val a = universe()
        val b = a.copy(roadmaps = a.roadmaps.map { it.copy(tasks = it.tasks.reversed()) })
        assertEquals(a.frontier(), b.frontier())
        assertEquals(listOf(receipt, api), b.frontier(roadmap = delivery))
        assertEquals(2, a.frontier().size)
        assertNotEquals(a.snapshotDigest(), b.snapshotDigest())
        assertEquals(a.snapshotDigest(), a.copy(tasks = a.tasks.reversed(), roadmaps = a.roadmaps.reversed(),
            epics = a.epics.reversed().map { it.copy(tasks = it.tasks.reversed()) }).snapshotDigest())
    }

    @Test fun `filtering a roadmap cannot conceal unmet prerequisites elsewhere or a global cycle`() {
        val a = universe()
        val cycleElsewhere = listOf(task(TaskId("TASK.x"), listOf(TaskId("TASK.y"))), task(TaskId("TASK.y"), listOf(TaskId("TASK.x"))))
        assertFails { a.copy(tasks = a.tasks + cycleElsewhere).frontier(roadmap = experience) }
        val completed = a.copy(tasks = a.tasks.map { if (it.id == api.value) it.copy(state = "closed") else it })
        assertEquals(listOf(ui, receipt), completed.frontier(roadmap = experience))
        val roadmapAsDependency = a.copy(tasks = a.tasks.map { if (it.id == ui.value) it.copy(requires = listOf(delivery.value)) else it })
        assertFails { roadmapAsDependency.frontier() }
    }

    @Test fun `planning records reject dangling duplicate and wrong-kind references`() {
        val a = universe()
        assertTrue(a.copy(roadmaps = a.roadmaps + a.roadmaps.first()).indexProblems().any { "duplicate roadmap" in it })
        assertTrue(a.copy(epics = a.epics + a.epics.first()).indexProblems().any { "duplicate epic" in it })
        assertTrue(a.copy(roadmaps = listOf(a.roadmaps.first().copy(tasks = listOf(TaskId(checkout.value))))).indexProblems().any { "missing task" in it })
        assertFails { DraftRoadmap(delivery, "Delivery", "Intent", listOf(api, api)) }
        assertFails { DraftEpic(checkout, "Checkout", "Scope", listOf(api, api)) }
        assertFails { a.frontier(roadmap = RoadmapId("unknown")) }
        assertFails { a.frontier(epic = EpicId("unknown")) }
    }

    @Test fun `regrouping preserves task evidence but changes the durable universe snapshot`() {
        val a = universe()
        val proven = a.tasks.first()
        val evidence = Receipt(proven.id, DraftLifecycle.contract(proven), mapOf("test" to "durability verified"))
        val regrouped = a.copy(roadmaps = listOf(a.roadmaps.first().copy(tasks = listOf(receipt))),
            epics = listOf(a.epics.first().copy(scope = "A revised capability scope.", tasks = listOf(ui))))
        assertTrue(regrouped.closureProblems(api, evidence).isEmpty())
        assertTrue(DraftLifecycle.addresses(evidence, regrouped.tasks.first()))
        assertNotEquals(a.snapshotDigest(), regrouped.snapshotDigest())
        assertFalse(DraftLifecycle.addresses(evidence, proven.copy(acceptance = listOf("Also survive a regional outage."))))
        val allTasksClosed = a.copy(tasks = a.tasks.map { it.copy(state = "closed") })
        assertTrue(allTasksClosed.frontier().isEmpty())
        assertEquals(a.epics, allTasksClosed.epics) // No automatic epic completion or scope assertion.
    }

    @Test fun `capability prerequisites participate globally before roadmap projection`() {
        val provider = object : SemanticProvider {
            override val identity = "test.readiness/v1"
            override val pin = "fixture-provider-1"
            override fun evaluate(record: DraftRecord) = if (record.id == receipt.value) Contribution(prerequisites = listOf(api.value)) else Contribution()
            override fun verify(record: DraftRecord, evidence: Map<String, String>) = emptyList<String>()
        }
        val profile = Profile(mapOf(provider.identity to provider.pin))
        assertTrue(universe().frontier(roadmap = experience, profile = profile, providers = listOf(provider)).isEmpty())
        assertFails { universe().frontier(roadmap = experience, profile = profile) }
    }

    @Test fun `planning codecs enforce the reserved namespace and preserve opaque extension source`() {
        val source = """
            protocol: tasking/planning-draft-1
            kind: roadmap
            id: ROADMAP.delivery
            title: 'Delivery 🧬'
            intent: Fulfill orders.
            tasks: [TASK.api, TASK.receipt]
            extensions:
              project.notes/v1:
                # Original opaque presentation and exact decimal.
                weight: 12345678901234567890.123456789
        """.trimIndent() + "\n"
        val document = PlanningDocument.parse(source)
        assertEquals(source, document.render())
        assertEquals(source.substringAfter("extensions:"), document.withTitle("New title").render().substringAfter("extensions:"))
        assertIs<DraftRoadmap>(document.record)
        assertEquals(document.record, PlanningRecordCodec.decode(PlanningRecordCodec.encode(document.record)))
        val epic = DraftEpic(checkout, "Checkout", "Durable checkout.", listOf(api, ui))
        assertEquals(epic, PlanningRecordCodec.decode(PlanningRecordCodec.encode(epic)))
        assertFails { PlanningDocument.parse(source + "epic: EPIC.checkout\n") }
        assertFails { PlanningDocument.parse(source.replace("kind: roadmap", "kind: task")) }
        assertFails { PlanningDocument.parse(source.replace("tasking/planning-draft-1", "tasking/v1")) }
        val required = assertIs<DraftRoadmap>(PlanningDocument.parse(source + "required_extensions: [project.release/v1]\n").record)
        val u = DraftUniverse(listOf(task(api), task(receipt)), listOf(required))
        assertEquals(listOf(delivery), u.roadmapsFor(api)) // Inspection needs no provider.
        assertFails { u.frontier() } // Unknown executable semantics cannot become a silent gate.
    }
}
