package io.brule.tasking.core

import kotlin.test.*

class EffectiveProfileTest {
    private val feature = ExtensionId.parseOrThrow("test.graph/v1")
    private val pin = ProviderPin(ProviderId.parseOrThrow("test.graph-provider/v1"), ProviderVersion.parseOrThrow("1.0.0"), ProviderDigest.parseOrThrow("sha256:" + "a".repeat(64)))
    private val profile = EffectiveProfile(ProfileId.parseOrThrow("test.workspace/v1"), mapOf(feature to pin))
    private val audit = ProfileAudit("reviewer", OccurredAt.parseOrThrow("2026-09-08T00:00:00Z"), "Reviewed exact behavior", mapOf("review" to "bounded fictional evidence"))
    private val evidence = mapOf("unit" to "passed", "provider" to "verified")
    private val provider = object : PinnedSemanticProvider {
        override val feature = this@EffectiveProfileTest.feature
        override val pin = this@EffectiveProfileTest.pin
        override fun evaluate(record: ProviderTask): Contribution {
            val data = record.payload as? ObjectValue ?: return Contribution()
            require(data.fields.keys.all { it in setOf("after", "proof") }) { "unknown test provider field" }
            val after = (data.fields["after"] as? StringValue)?.value?.let { listOf(TaskId.parseOrThrow(it)) }.orEmpty()
            return Contribution(after, evidenceRequirements = if (data.fields["proof"] == BooleanValue(true)) listOf("provider") else emptyList())
        }
        override fun verify(record: ProviderTask, evidence: Map<String, String>): List<String> =
            if (evaluate(record).evidenceRequirements.isNotEmpty() && evidence["provider"] != "verified") listOf("provider proof rejected") else emptyList()
    }
    private val registry = ProviderRegistry(listOf(provider))
    private fun task(name: String, requires: List<TaskId> = emptyList(), after: String? = null, proof: Boolean = false): DraftRecord = DraftRecord(
        TaskId.parseOrThrow("TASK.$name"), "Task $name", "open", "Bounded result", requires, listOf("Durable result"), listOf("Verified result"),
        requiredExtensions = emptyList(), extensions = if (after == null && !proof) obj() else ObjectValue(mapOf(feature.value to ObjectValue(buildMap {
            after?.let { put("after", StringValue(it)) }; if (proof) put("proof", BooleanValue(true))
        }))), protocol = "tasking/core-draft-2", verification = listOf("unit"))
    private fun empty(providers: ProviderRegistry = registry): LedgerSnapshot = LedgerSnapshot("test", Revision.initial(), DraftUniverse(emptyList()),
        history = TaskHistory(Revision.initial()), providers = providers)
    private fun select(snapshot: LedgerSnapshot, next: EffectiveProfile = profile): LedgerSnapshot = LedgerTransitions.evolve(snapshot,
        Transition.SetProfile(ProfileChange(snapshot.profileHistory?.head, next, audit)))
    private fun seeded(): LedgerSnapshot {
        val a = task("a", proof = true); val b = task("b", after = "TASK.a"); val c = task("c", requires = listOf(b.id))
        return LedgerTransitions.evolve(select(empty()), Transition.AddRecords(listOf(c, b, a)))
    }
    private fun review(snapshot: LedgerSnapshot, id: TaskId, outcome: ReviewOutcome = ReviewOutcome.REVALIDATED): Reconciliation = Reconciliation(
        id, snapshot.history!!.heads.getValue(id), outcome, snapshot.effectiveEvaluation().dependencies.getValue(id).sorted().map { CurrencyEvaluation.observations(snapshot).getValue(it) },
        "reviewer", audit.occurredAt, "Reviewed current semantics", evidence, profile = snapshot.profileHistory?.profile?.digest)
    private fun reconcile(snapshot: LedgerSnapshot, id: TaskId): LedgerSnapshot = LedgerTransitions.evolve(snapshot, Transition.ReconcileTask(review(snapshot, id)))
    private fun close(snapshot: LedgerSnapshot, id: TaskId, supplied: Map<String, String> = evidence): LedgerSnapshot {
        val record = snapshot.universe.tasks.single { it.id == id }
        return LedgerTransitions.evolve(snapshot, Transition.CloseTask(ClosureEvidence(Receipt(id, snapshot.effectiveContract(record), supplied), "reviewer", audit.occurredAt)))
    }

    @Test fun `profile adoption preserves historical bytes and requires explicit profile review`() {
        val a = task("a")
        val old = LedgerTransitions.evolve(empty(), Transition.AddRecords(listOf(a)))
        val head = old.history!!.head(a.id)
        val bytes = Json.encode(HistoryCodec.revision(head))
        val changed = select(old)
        assertEquals(old.history, changed.history)
        assertEquals(bytes, Json.encode(HistoryCodec.revision(changed.history!!.head(a.id))))
        assertNotEquals(head.contract, changed.effectiveContract(a))
        assertEquals(Currency.AFFECTED, changed.currency().getValue(a.id).state)
        assertFails { LedgerTransitions.evolve(changed, Transition.ReconcileTask(review(changed, a.id).copy(profile = null))) }
        val current = reconcile(changed, a.id)
        assertEquals(Currency.CURRENT, current.currency().getValue(a.id).state)
        assertEquals("taskctl.task-revision/3", current.history!!.head(a.id).protocol)
        assertEquals(head.id, current.history.head(a.id).parent)
        assertEquals(head, current.history.revisions.getValue(head.id))
        assertEquals("taskctl.reconciliation/3", HistoryCodec.review(current.history.head(a.id).review!!).requiredString("protocol"))
    }

    @Test fun `complete contributed DAG controls seed order readiness and evidence`() {
        val start = seeded(); val a = TaskId.parseOrThrow("TASK.a"); val b = TaskId.parseOrThrow("TASK.b")
        assertEquals(listOf(a), start.frontier().tasks)
        assertEquals(listOf(a), start.history!!.head(b).dependencies.map { it.upstream })
        assertTrue(start.universe.tasks.single { it.id == b }.requires.isEmpty())
        assertFails { close(start, a, mapOf("unit" to "passed")) }
        assertFails { close(start, a, evidence + ("provider" to "wrong")) }
        val closed = close(start, a)
        assertEquals(listOf(b), closed.frontier().tasks)
        assertEquals(start.universe.roadmaps, closed.universe.roadmaps)
    }

    @Test fun `provider input drift propagates transitively and survives intermediate review`() {
        var current = seeded()
        val a = TaskId.parseOrThrow("TASK.a"); val b = TaskId.parseOrThrow("TASK.b"); val c = TaskId.parseOrThrow("TASK.c")
        current = close(close(current, a), b)
        val oldReceipts = current.receipts
        val revised = current.universe.tasks.single { it.id == a }.copy(requirements = listOf("Changed durable result"))
        current = LedgerTransitions.evolve(current, Transition.ReviseTask(revised))
        assertEquals(Currency.AFFECTED, current.currency().getValue(c).state)
        current = reconcile(current, a)
        current = reconcile(current, b)
        assertEquals(Currency.AFFECTED, current.currency().getValue(c).state)
        current = reconcile(current, c)
        assertTrue(current.currency().values.all { it.state == Currency.CURRENT })
        assertEquals(oldReceipts, current.receipts)
    }

    @Test fun `contributed edge removal is drift and does not create historical recursion cycles`() {
        val start = seeded(); val b = TaskId.parseOrThrow("TASK.b")
        val changed = LedgerTransitions.evolve(start, Transition.ReviseTask(start.universe.tasks.single { it.id == b }.copy(extensions = obj())))
        assertEquals(Currency.AFFECTED, changed.currency().getValue(b).state)
        assertEquals(listOf(TaskId.parseOrThrow("TASK.a")), changed.history!!.head(b).prerequisites)
        val reviewed = reconcile(changed, b)
        assertTrue(reviewed.history!!.head(b).prerequisites.isEmpty())
        assertEquals(Currency.CURRENT, reviewed.currency().getValue(b).state)
        val a = TaskId.parseOrThrow("TASK.a")
        val reverse = LedgerTransitions.evolve(reviewed, Transition.ReviseTask(reviewed.universe.tasks.single { it.id == a }.copy(
            extensions = obj(feature.value to obj("after" to StringValue(b.value))))))
        // Historical B -> A and current A -> B must never become one cyclic graph.
        assertTrue(reverse.effectiveEvaluation().problems.isEmpty())
        assertEquals(Currency.UNRESOLVED, reverse.currency().getValue(a).state)
        val reviewedReverse = reconcile(reverse, a)
        assertEquals(Currency.CURRENT, reviewedReverse.currency().getValue(a).state)
        assertEquals(Currency.AFFECTED, reviewedReverse.currency().getValue(TaskId.parseOrThrow("TASK.c")).state)
    }

    @Test fun `missing mismatched and failed providers cannot invent current semantic inputs`() {
        val start = seeded(); val a = TaskId.parseOrThrow("TASK.a")
        val badPin = pin.copy(digest = ProviderDigest.parseOrThrow("sha256:" + "b".repeat(64)))
        val wrong = object : PinnedSemanticProvider by provider { override val pin = badPin }
        val failed = object : PinnedSemanticProvider by provider { override fun evaluate(record: ProviderTask): Contribution = error("provider failed") }
        for (runtime in listOf(ProviderRegistry(), ProviderRegistry(listOf(wrong)), ProviderRegistry(listOf(failed)))) {
            val snapshot = start.copy(providers = runtime)
            snapshot.history!!.validate(snapshot.universe, snapshot.receipts)
            snapshot.validateProfiles()
            assertTrue(snapshot.currency().values.all { it.state == Currency.UNRESOLVED })
            assertTrue(CurrencyEvaluation.observations(snapshot).values.all { it.observedInputs == null })
            assertFails { snapshot.frontier() }
            assertFails { close(snapshot, a) }
            assertFails { reconcile(snapshot, a) }
        }
        assertFails { ProviderRegistry(listOf(provider, provider)) }
        assertTrue(select(empty(ProviderRegistry())).semanticProblems().isNotEmpty())
    }

    @Test fun `pin drift remains affected after matching replacement appears until explicit review`() {
        val start = seeded(); val a = TaskId.parseOrThrow("TASK.a")
        val nextPin = pin.copy(version = ProviderVersion.parseOrThrow("2.0.0"), digest = ProviderDigest.parseOrThrow("sha256:" + "b".repeat(64)))
        val next = profile.copy(bindings = mapOf(feature to nextPin))
        val changed = select(start, next)
        assertEquals(Currency.UNRESOLVED, changed.currency().getValue(a).state)
        val replacement = object : PinnedSemanticProvider by provider { override val pin = nextPin }
        val installed = changed.copy(providers = ProviderRegistry(listOf(replacement)))
        assertEquals(Currency.AFFECTED, installed.currency().getValue(a).state)
        assertFails { LedgerTransitions.evolve(installed, Transition.ReconcileTask(review(start, a))) }
        val reviewed = reconcile(installed, a)
        assertEquals(Currency.CURRENT, reviewed.currency().getValue(a).state)
        assertEquals(Currency.AFFECTED, reviewed.currency().getValue(TaskId.parseOrThrow("TASK.c")).state)
        assertEquals(2, reviewed.profileHistory!!.revisions.size)
    }

    @Test fun `provider cycles and graph defects cannot bypass core validation`() {
        val cycle = object : PinnedSemanticProvider by provider { override fun evaluate(record: ProviderTask) = Contribution(listOf(record.id)) }
        val missing = object : PinnedSemanticProvider by provider { override fun evaluate(record: ProviderTask) = Contribution(listOf(TaskId.parseOrThrow("TASK.absent"))) }
        for (runtime in listOf(cycle, missing)) {
            assertFails { LedgerTransitions.evolve(select(empty(ProviderRegistry(listOf(runtime)))), Transition.AddRecords(listOf(task("a")))) }
        }
        val before = seeded()
        val badHead = ProfileRevisionId.parseOrThrow("sha256:" + "0".repeat(64))
        assertFails { LedgerTransitions.evolve(before, Transition.SetProfile(ProfileChange(badHead, profile, audit))) }
        assertFails { LedgerTransitions.evolve(before, Transition.SetProfile(ProfileChange(before.profileHistory!!.head, profile, audit))) }
        assertFails { LedgerTransitions.evolve(before, Transition.ReviseTask(before.universe.tasks.first().copy(state = "closed"))) }
    }

    @Test fun `unknown optional values stay inactive and installed code never auto activates`() {
        var called = 0
        val counting = object : PinnedSemanticProvider by provider { override fun evaluate(record: ProviderTask): Contribution { called++; return provider.evaluate(record) } }
        val a = task("a").copy(extensions = obj("unknown.data/v1" to obj("decimal" to DecimalValue(java.math.BigDecimal("1E+30")))))
        val legacy = LedgerTransitions.evolve(empty(ProviderRegistry(listOf(counting))), Transition.AddRecords(listOf(a)))
        legacy.frontier(); legacy.currency()
        assertEquals(0, called)
        val emptyProfile = EffectiveProfile(ProfileId.parseOrThrow("test.empty/v1"), emptyMap())
        val selected = select(legacy, emptyProfile)
        assertEquals(selected.effectiveContract(a), selected.effectiveContract(a.copy(extensions = obj())))
        assertEquals(a.extensions, selected.universe.tasks.single().extensions)
        var probes = 0
        val probe = EnvironmentProbe { probes++; mapOf("host" to "explicit only") }
        selected.currency(); selected.semanticProblems()
        assertEquals(0, probes)
        assertEquals("explicit only", probe.probe()["host"])
        assertEquals(1, probes)
    }

    @Test fun `strict profile and revision codecs reject untyped identities and spoofed audit time`() {
        assertNull(ExtensionId.parse("banana")); assertNull(ProviderId.parse("bad")); assertNull(ProfileId.parse("invalid"))
        assertNull(ProviderVersion.parse("")); assertNull(ProviderVersion.parse("with space")); assertNull(ProviderDigest.parse("latest"))
        assertNull(ProfileDigest.parse("bad")); assertNull(ProfileRevisionId.parse("bad"))
        val change = ProfileChange(null, profile, audit)
        assertEquals(change, ProfileCodec.decodeChange(ProfileCodec.change(change)))
        assertFails { ProfileCodec.decodeProfile(ObjectValue(ProfileCodec.profile(profile).fields + ("unknown" to NullValue))) }
        assertFails { ProfileCodec.decodeAudit(ObjectValue(ProfileCodec.audit(audit).fields + ("accepted_at" to StringValue(audit.occurredAt.value)))) }
        assertFails { ProfileCodec.decodePin(obj("provider" to StringValue(pin.provider.value), "version" to StringValue("latest"))) }
        val start = seeded()
        for (revision in start.history!!.revisions.values) assertEquals(revision, HistoryCodec.decodeRevision(HistoryCodec.revision(revision)))
        val reviewed = review(start, TaskId.parseOrThrow("TASK.a"))
        assertEquals(reviewed, HistoryCodec.decodeReview(HistoryCodec.review(reviewed)))
        assertFails { reviewed.copy(time = LegacyRecordedAt.parseOrThrow("yesterday")) }
        assertFails { HistoryCodec.decodeReview(ObjectValue(HistoryCodec.review(reviewed).fields - "profile")) }
    }

    @Test fun `provider inputs exclude lifecycle cosmetic text and inactive data and normalize semantic prose`() {
        val inputs = mutableListOf<Triple<ObjectValue, Value, ContractDigest>>()
        val inspecting = object : PinnedSemanticProvider by provider {
            override fun evaluate(record: ProviderTask): Contribution {
                inputs += Triple(record.core, record.payload, record.contract)
                return provider.evaluate(record)
            }
        }
        val selected = select(empty(ProviderRegistry(listOf(inspecting))))
        val first = task("a").copy(requirements = listOf("Durable\nresult"))
        val second = first.copy(title = "Cosmetic title", state = "closed", requirements = listOf("Durable result"),
            extensions = obj("unknown.annotation/v1" to StringValue("not activated")))
        selected.copy(universe = DraftUniverse(listOf(first))).effectiveEvaluation()
        selected.copy(universe = DraftUniverse(listOf(second))).effectiveEvaluation()
        assertEquals(2, inputs.size)
        assertEquals(inputs[0], inputs[1])
        assertFalse("title" in inputs[0].first.fields || "state" in inputs[0].first.fields)
        assertEquals(NullValue, inputs[0].second)
    }
}
