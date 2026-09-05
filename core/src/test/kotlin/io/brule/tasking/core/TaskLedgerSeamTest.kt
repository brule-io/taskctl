package io.brule.tasking.core

import kotlin.test.*

class TaskLedgerSeamTest {
    private class MemoryLedger : TaskLedger {
        private var current = LedgerSnapshot("test", Revision("0"), DraftUniverse(emptyList()))
        override fun snapshot() = current
        override fun apply(expectedRevision: Revision, transition: Transition): TransitionResult {
            if (expectedRevision != current.revision) throw RevisionConflict("stale")
            val universe = LedgerTransitions.reduce(current, transition)
            current = current.copy(revision = Revision(universe.snapshotDigest()), universe = universe)
            return TransitionResult(current.revision, emptyList())
        }
    }
    @Test fun `graph and lifecycle operate through a non-filesystem ledger`() {
        val ledger: TaskLedger = MemoryLedger()
        val a = DraftRecord("A", "A", "open", "A", emptyList(), listOf("Persist"), listOf("Durable"), emptyList(), obj())
        val b = a.copy(id = "B", requires = listOf("A"))
        val initial = ledger.snapshot().revision
        val added = ledger.apply(initial, Transition.AddRecords(listOf(a, b), listOf(DraftRoadmap(RoadmapId("R"), "R", "R", listOf(TaskId("B"))))))
        assertEquals(a, ledger.task(TaskId("A")))
        assertEquals(listOf(TaskId("A")), ledger.frontier().tasks)
        assertTrue(ledger.frontier(FrontierQuery(roadmap = RoadmapId("R"))).tasks.isEmpty())
        assertFailsWith<RevisionConflict> { ledger.apply(initial, Transition.AddRecords()) }
        ledger.apply(added.revision, Transition.CloseTask(ClosureEvidence(Receipt("A", DraftLifecycle.contract(a), mapOf("test" to "passed")), "actor", "2026-09-05T00:00:00Z")))
        assertEquals(listOf(TaskId("B")), ledger.frontier(FrontierQuery(roadmap = RoadmapId("R"))).tasks)
    }
    @Test fun `observed dependency contracts can fence readiness and closure independently of identity`() {
        val a = DraftRecord("A", "A", "closed", "A", emptyList(), listOf("Persist"), listOf("Durable"), emptyList(), obj())
        val b = a.copy(id = "B", state = "open", requires = listOf("A"))
        val snapshot = LedgerSnapshot("test", Revision("snapshot-1"), DraftUniverse(listOf(a, b)),
            dependencyBindings = mapOf(TaskId("B") to listOf(Dependency(TaskId("A"), ContractDigest(DraftLifecycle.contract(a))))))
        assertTrue(snapshot.dependencyProblems().isEmpty())
        val changed = snapshot.copy(universe = DraftUniverse(listOf(a.copy(acceptance = listOf("A different outcome")), b)))
        assertTrue(changed.dependencyProblems().single().contains("observed upstream contract changed"))
        val ledger = object : TaskLedger {
            override fun snapshot() = changed
            override fun apply(expectedRevision: Revision, transition: Transition): TransitionResult = error("read-only test adapter")
        }
        assertFails { ledger.frontier() }
        assertFails { LedgerTransitions.reduce(changed, Transition.CloseTask(ClosureEvidence(Receipt("B", DraftLifecycle.contract(b), mapOf("test" to "passed")), "actor", "2026-09-05T00:00:00Z"))) }
        val unbound = changed.copy(dependencyBindings = emptyMap())
        assertEquals(listOf(Dependency(TaskId("A"))), unbound.dependencies(TaskId("B")))
        assertTrue(unbound.dependencyProblems().isEmpty())
    }
}
