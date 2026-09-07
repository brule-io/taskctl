package io.brule.tasking.core

import kotlin.test.*

class CurrencyConformanceTest {
    private fun task(name: String, vararg upstream: String) = DraftRecord(TaskId.parseOrThrow("TASK.$name"), name, "open", "Persist $name.",
        upstream.map { TaskId.parseOrThrow("TASK.$it") }, listOf("Durable result."), listOf("Survives restart."), emptyList(), obj(), "tasking/core-draft-2", listOf("test"))
    private val a = task("a")
    private val b = task("b", "a")
    private val c = task("c", "b")
    private class MemoryLedger : TaskLedger {
        private var current = LedgerSnapshot("memory", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial()))
        override fun snapshot() = current
        override fun apply(expectedRevision: Revision, transition: Transition): TransitionResult {
            if (current.revision != expectedRevision) throw RevisionConflict("stale")
            val next = LedgerTransitions.evolve(current, transition)
            val history = requireNotNull(next.history)
            current = next.copy(revision = Revision.parseOrThrow(Canonical.digest("memory-ledger/test", HistoryCodec.heads(history))))
            return TransitionResult(current.revision, emptyList())
        }
    }
    private fun TaskLedger.apply(value: Transition) = apply(snapshot().revision, value)
    private fun TaskLedger.close(id: TaskId) {
        val record = requireNotNull(task(id))
        apply(Transition.CloseTask(ClosureEvidence(Receipt(id, DraftLifecycle.contract(record), mapOf("test" to "test harness assertion")), "tester", LegacyRecordedAt.parseOrThrow("2026-09-06T00:00:00Z"))))
    }
    private fun TaskLedger.review(id: TaskId, outcome: ReviewOutcome = ReviewOutcome.REVALIDATED): Reconciliation {
        val snapshot = snapshot()
        val observations = CurrencyEvaluation.observations(snapshot)
        return Reconciliation(id, requireNotNull(snapshot.history).heads.getValue(id), outcome,
            requireNotNull(task(id)).requires.sorted().map { observations.getValue(it) }, "tester", LegacyRecordedAt.parseOrThrow("2026-09-06T00:00:00Z"), "Reviewed changed inputs.", mapOf("test" to "Revalidation passed."))
    }
    @Test fun `transitive invalidation survives intermediate reconciliation and preserves closed history`() {
        val ledger: TaskLedger = MemoryLedger()
        ledger.apply(Transition.AddRecords(listOf(c, b, a)))
        listOf(a, b, c).forEach { ledger.close(it.id) }
        val before = ledger.snapshot()
        val oldB = DraftLifecycle.contract(b)
        ledger.apply(Transition.ReviseTask(requireNotNull(ledger.task(a.id)).copy(acceptance = listOf("Survives total power loss."))))
        assertEquals(listOf(Currency.AFFECTED, Currency.AFFECTED, Currency.AFFECTED), listOf(a, b, c).map { ledger.snapshot().currency().getValue(it.id).state })
        assertEquals(before.receipts, ledger.snapshot().receipts)
        assertTrue(ledger.snapshot().universe.tasks.all { it.state == "closed" })
        assertTrue(before.history!!.revisions.all { (id, value) -> ledger.snapshot().history!!.revisions[id] == value })
        assertFails { ledger.apply(Transition.ReconcileTask(ledger.review(b.id))) }
        ledger.apply(Transition.ReconcileTask(ledger.review(a.id)))
        ledger.apply(Transition.ReconcileTask(ledger.review(b.id)))
        assertEquals(oldB, DraftLifecycle.contract(requireNotNull(ledger.task(b.id))))
        assertEquals(Currency.AFFECTED, ledger.snapshot().currency().getValue(c.id).state)
        assertTrue(ledger.snapshot().currency().getValue(c.id).causes.any {
            it.path == listOf(c.id, b.id, a.id) && it.observed?.observedContract == DraftLifecycle.contract(a)
        })
        ledger.apply(Transition.ReconcileTask(ledger.review(c.id)))
        assertTrue(ledger.snapshot().currency().values.all { it.state == Currency.CURRENT })
        assertEquals(before.receipts, ledger.snapshot().receipts)
    }
    @Test fun `review requires exact inspected identities evidence and CAS`() {
        val ledger: TaskLedger = MemoryLedger()
        ledger.apply(Transition.AddRecords(listOf(a, b)))
        val review = ledger.review(b.id)
        val revision = ledger.snapshot().revision
        assertFails { ledger.apply(Transition.ReconcileTask(review.copy(evidence = emptyMap()))) }
        assertFails { ledger.apply(Transition.ReconcileTask(review.copy(observations = emptyList()))) }
        ledger.apply(Transition.ReviseTask(a.copy(title = "A clearer title")))
        assertTrue(ledger.snapshot().currency().values.all { it.state == Currency.CURRENT })
        assertFailsWith<RevisionConflict> { ledger.apply(revision, Transition.ReconcileTask(review)) }
        assertFails { ledger.apply(Transition.ReconcileTask(review)) } // Same contract, different inspected HEAD.
        ledger.apply(Transition.ReconcileTask(ledger.review(b.id, ReviewOutcome.UNRESOLVED)))
        assertEquals(Currency.UNRESOLVED, ledger.snapshot().currency().getValue(b.id).state)
        ledger.apply(Transition.ReconcileTask(ledger.review(b.id)))
        assertEquals(Currency.CURRENT, ledger.snapshot().currency().getValue(b.id).state)
    }
    @Test fun `legacy history adoption records origin and leaves unknown inputs unresolved`() {
        val legacy = LedgerSnapshot("legacy", Revision.initial(), DraftUniverse(listOf(a, b)))
        val tracked = LedgerTransitions.evolve(legacy, Transition.TrackHistory)
        assertEquals(legacy.revision, tracked.history!!.origin)
        assertEquals(Currency.CURRENT, tracked.currency().getValue(a.id).state)
        assertEquals(Currency.UNRESOLVED, tracked.currency().getValue(b.id).state)
        assertNull(tracked.dependencies(b.id).single().observedContract)
        assertFails { LedgerTransitions.evolve(tracked, Transition.TrackHistory) }
    }
    @Test fun `new semantic projection ignores presentation and includes all material inputs`() {
        val digest = DraftLifecycle.contract(a)
        // Independent framed-encoding vector: pins the algorithm, AST shape and field projection.
        assertEquals("sha256:2f45546fb2406ec40ecd406b61aff59a9df44a1fcdb59c1b637aefe1d0e89144", digest.value)
        assertEquals(digest, DraftLifecycle.contract(a.copy(title = "Presentation only", state = "closed", extensions = obj("test.annotation/v1" to integer(1)))))
        assertEquals(digest, DraftLifecycle.contract(a.copy(intent = "Persist\na.")))
        listOf(a.copy(intent = "Persist something else."), a.copy(requires = listOf(b.id)), a.copy(requirements = listOf("New requirement")),
            a.copy(acceptance = listOf("New criterion")), a.copy(verification = listOf("integration")), a.copy(requiredExtensions = listOf("test.semantic/v1"))).forEach {
            assertNotEquals(digest, DraftLifecycle.contract(it))
        }
        assertNotEquals(digest, DraftLifecycle.contract(a.copy(protocol = "tasking/core-draft-1", verification = emptyList())))
        assertEquals(a, DraftDocument.parse(Json.encode(NativeCodec.task(a))).record)
        val rev = TaskRevision(null, a, emptyList())
        assertEquals(rev, HistoryCodec.decodeRevision(HistoryCodec.revision(rev)))
        assertEquals(rev.id, HistoryCodec.decodeRevision(HistoryCodec.revision(rev)).id)
    }
    @Test fun `affected open tasks are inspectable but cannot execute or close`() {
        val ledger: TaskLedger = MemoryLedger()
        ledger.apply(Transition.AddRecords(listOf(a, b, c)))
        ledger.close(a.id)
        assertEquals(listOf(b.id), ledger.frontier().tasks)
        ledger.apply(Transition.ReviseTask(requireNotNull(ledger.task(a.id)).copy(requirements = listOf("New requirement."))))
        assertTrue(ledger.frontier().tasks.isEmpty())
        assertFails { ledger.close(b.id) }
        assertEquals(listOf(c.id, b.id, a.id), ledger.snapshot().currency().getValue(c.id).causes.last().path)
    }

    @Test fun `diamond graphs retain bounded deterministic cause witnesses`() {
        val ledger: TaskLedger = MemoryLedger()
        val tasks = mutableListOf(a)
        var upstream = listOf(a.id)
        repeat(24) { depth ->
            val next = listOf(task("left$depth"), task("right$depth")).map { it.copy(requires = upstream) }
            tasks += next
            upstream = next.map { it.id }
        }
        ledger.apply(Transition.AddRecords(tasks))
        ledger.apply(Transition.ReviseTask(a.copy(acceptance = listOf("Changed root criterion."))))
        val currency = ledger.snapshot().currency()
        upstream.forEach { id ->
            val result = currency.getValue(id)
            assertEquals(Currency.AFFECTED, result.state)
            assertTrue(result.causes.size <= tasks.size)
            assertTrue(result.causes.any { it.path.last() == a.id })
        }
        assertEquals(currency, ledger.snapshot().currency())
    }
}
