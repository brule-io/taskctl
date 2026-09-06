package io.brule.tasking.core

import kotlin.test.*

class TaskLedgerSeamTest {
    private class MemoryLedger : TaskLedger {
        private var current = LedgerSnapshot("test", Revision.initial(), DraftUniverse(emptyList()))
        override fun snapshot() = current
        override fun apply(expectedRevision: Revision, transition: Transition): TransitionResult {
            if (expectedRevision != current.revision) throw RevisionConflict("stale")
            val universe = LedgerTransitions.reduce(current, transition)
            current = current.copy(revision = Revision.parseOrThrow(universe.snapshotDigest()), universe = universe)
            return TransitionResult(current.revision, emptyList())
        }
    }
    @Test fun `graph and lifecycle operate through a non-filesystem ledger`() {
        val ledger: TaskLedger = MemoryLedger()
        val a = DraftRecord(TaskId.parseOrThrow("TASK.a"), "TASK.a", "open", "TASK.a", emptyList(), listOf("Persist"), listOf("Durable"), emptyList(), obj())
        val b = a.copy(id = TaskId.parseOrThrow("TASK.b"), requires = listOf(TaskId.parseOrThrow("TASK.a")))
        val initial = ledger.snapshot().revision
        val added = ledger.apply(initial, Transition.AddRecords(listOf(a, b), listOf(DraftRoadmap(RoadmapId.parseOrThrow("ROADMAP.r"), "ROADMAP.r", "ROADMAP.r", listOf(TaskId.parseOrThrow("TASK.b"))))))
        assertEquals(a, ledger.task(TaskId.parseOrThrow("TASK.a")))
        assertEquals(listOf(TaskId.parseOrThrow("TASK.a")), ledger.frontier().tasks)
        assertTrue(ledger.frontier(FrontierQuery(roadmap = RoadmapId.parseOrThrow("ROADMAP.r"))).tasks.isEmpty())
        assertFailsWith<RevisionConflict> { ledger.apply(initial, Transition.AddRecords()) }
        ledger.apply(added.revision, Transition.CloseTask(ClosureEvidence(Receipt(TaskId.parseOrThrow("TASK.a"), DraftLifecycle.contract(a), mapOf("test" to "passed")), "actor", "2026-09-05T00:00:00Z")))
        assertEquals(listOf(TaskId.parseOrThrow("TASK.b")), ledger.frontier(FrontierQuery(roadmap = RoadmapId.parseOrThrow("ROADMAP.r"))).tasks)
    }
    @Test fun `observed dependency contracts can fence readiness and closure independently of identity`() {
        val a = DraftRecord(TaskId.parseOrThrow("TASK.a"), "TASK.a", "closed", "TASK.a", emptyList(), listOf("Persist"), listOf("Durable"), emptyList(), obj())
        val b = a.copy(id = TaskId.parseOrThrow("TASK.b"), state = "open", requires = listOf(TaskId.parseOrThrow("TASK.a")))
        val snapshot = LedgerSnapshot("test", Revision.initial(), DraftUniverse(listOf(a, b)),
            dependencyBindings = mapOf(TaskId.parseOrThrow("TASK.b") to listOf(Dependency(TaskId.parseOrThrow("TASK.a"), DraftLifecycle.contract(a)))))
        assertTrue(snapshot.dependencyProblems().isEmpty())
        val changed = snapshot.copy(universe = DraftUniverse(listOf(a.copy(acceptance = listOf("A different outcome")), b)))
        assertTrue(changed.dependencyProblems().isEmpty())
        assertEquals(Currency.UNRESOLVED, changed.currency().getValue(b.id).state)
        val ledger = object : TaskLedger {
            override fun snapshot() = changed
            override fun apply(expectedRevision: Revision, transition: Transition): TransitionResult = error("read-only test adapter")
        }
        assertEquals(listOf(b.id), ledger.frontier().tasks) // Legacy identity-only execution stays in its original contract.
        val unbound = changed.copy(dependencyBindings = emptyMap())
        assertEquals(listOf(Dependency(TaskId.parseOrThrow("TASK.a"))), unbound.dependencies(TaskId.parseOrThrow("TASK.b")))
        assertTrue(unbound.dependencyProblems().isEmpty())
    }
}
