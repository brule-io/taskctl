package io.brule.tasking.repository

import io.brule.tasking.core.*
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ProfileLedgerTest {
    @TempDir lateinit var directory: Path
    private val feature = ExtensionId.parseOrThrow("test.edge/v1")
    private val pin = ProviderPin(ProviderId.parseOrThrow("test.edge-provider/v1"), ProviderVersion.parseOrThrow("1.0.0"), ProviderDigest.parseOrThrow("sha256:" + "c".repeat(64)))
    private val profile = EffectiveProfile(ProfileId.parseOrThrow("test.workspace/v1"), mapOf(feature to pin))
    private val audit = ProfileAudit("reviewer", OccurredAt.parseOrThrow("2026-09-08T00:00:00Z"), "Reviewed provider", mapOf("review" to "Synthetic conformance implementation"))
    private val a = DraftRecord(TaskId.parseOrThrow("TASK.a"), "A", "open", "Durable", emptyList(), listOf("Persist"), listOf("Restart"), emptyList(), obj(), "tasking/core-draft-2", listOf("unit"))
    private val payload = obj(feature.value to obj("after" to StringValue(a.id.value), "opaque" to DecimalValue(BigDecimal("1.00E+30"))))
    private val b = a.copy(id = TaskId.parseOrThrow("TASK.b"), extensions = payload)
    private val provider = object : PinnedSemanticProvider {
        override val feature = this@ProfileLedgerTest.feature
        override val pin = this@ProfileLedgerTest.pin
        override fun evaluate(record: ProviderTask): Contribution {
            val data = record.payload as? ObjectValue ?: return Contribution()
            return Contribution(listOf(TaskId.parseOrThrow(data.requiredString("after"))), evidenceRequirements = listOf("provider"))
        }
        override fun verify(record: ProviderTask, evidence: Map<String, String>): List<String> =
            if (record.payload != NullValue && evidence["provider"] != "verified") listOf("provider rejected evidence") else emptyList()
    }
    private val registry = ProviderRegistry(listOf(provider))
    private fun create(name: String = "project"): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap")
        Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "test launcher\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val root = directory.resolve(name)
        Bootstrap.apply(Bootstrap.plan(root, "profile.test", "test", distribution.parent, lock, Transition.AddRecords(listOf(a, b))))
        Files.writeString(root.resolve("source.txt"), "Unrelated source\n")
        return FileTaskLedger(root, registry)
    }
    private fun image(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString().replace('\\', '/') to Pair(Files.readString(it), Files.getLastModifiedTime(it))
    } }
    private fun taskImage(root: Path) = image(root).filterKeys { it.startsWith(".agents/tasks/") || it.startsWith(".agents/history/") || it.startsWith(".agents/receipts/") || it == "source.txt" }
    private fun change(ledger: FileTaskLedger): Transition.SetProfile = Transition.SetProfile(ProfileChange(ledger.snapshot().profileHistory?.head, profile, audit))
    private fun reconcile(ledger: FileTaskLedger, id: TaskId, supplied: Map<String, String> = mapOf("unit" to "passed", "provider" to "verified")) {
        val snapshot = ledger.snapshot()
        val observed = CurrencyEvaluation.observations(snapshot)
        val review = Reconciliation(id, snapshot.history!!.heads.getValue(id), ReviewOutcome.REVALIDATED,
            snapshot.effectiveEvaluation().dependencies.getValue(id).map { observed.getValue(it) }, "reviewer", audit.occurredAt, "Reviewed inputs", supplied,
            profile = snapshot.profileHistory!!.profile.digest)
        ledger.apply(snapshot.revision, Transition.ReconcileTask(review))
    }

    @Test fun `profile adoption preserves old task bytes and cold load uses the exact persisted profile`() {
        val ledger = create(); val before = ledger.snapshot(); val original = taskImage(ledger.root); val transition = change(ledger)
        val plan = ledger.plan(before.revision, transition)
        val paths = plan.requiredArray("writes").map { (it as ObjectValue).requiredString("path") }
        assertEquals(4, paths.size)
        assertTrue(paths.all { it in setOf(".agents/config.toml", ".agents/policy.toml") || it.startsWith(".agents/profile-history/") })
        assertEquals(original, taskImage(ledger.root))
        ledger.apply(before.revision, transition)
        val cold = FileTaskLedger(ledger.root, ProviderRegistry(listOf(provider))).snapshot()
        assertEquals(profile, cold.profileHistory!!.profile)
        assertEquals(before.revision, cold.profileHistory!!.origin)
        assertEquals(before.history, cold.history)
        assertEquals(before.universe, cold.universe)
        assertEquals(original, taskImage(ledger.root))
        assertContains(Files.readString(ledger.root.resolve(".agents/config.toml")), "taskctl.native/alpha5")
        assertEquals(Currency.AFFECTED, cold.currency().getValue(a.id).state)
        assertEquals(Currency.UNRESOLVED, cold.currency().getValue(b.id).state) // Newly contributed edge has no old observation.
        val stable = image(ledger.root)
        FileTaskLedger(ledger.root).snapshot().also {
            assertEquals(cold.history, it.history)
            assertTrue(it.currency().values.all { value -> value.state == Currency.UNRESOLVED })
            assertFails { it.frontier() }
        }
        assertEquals(stable, image(ledger.root))
        assertFailsWith<RevisionConflict> { ledger.apply(before.revision, transition) }
        assertEquals(stable, image(ledger.root))
    }

    @Test fun `cold revisions retain contributed observations evidence and exact opaque data`() {
        val ledger = create(); ledger.apply(ledger.snapshot().revision, change(ledger))
        reconcile(ledger, a.id)
        assertFails { reconcile(ledger, b.id, mapOf("unit" to "passed", "provider" to "wrong")) }
        reconcile(ledger, b.id)
        var snapshot = FileTaskLedger(ledger.root, registry).snapshot()
        val head = snapshot.history!!.head(b.id)
        assertEquals("taskctl.task-revision/3", head.protocol)
        assertEquals(listOf(a.id), head.dependencies.map { it.upstream })
        assertEquals(snapshot.history!!.head(a.id).id, head.dependencies.single().observedRevision)
        assertEquals(snapshot.effectiveContract(a), head.dependencies.single().observedContract)
        assertEquals(payload, head.record.extensions)
        val receipt = ClosureEvidence(Receipt(a.id, snapshot.effectiveContract(a), mapOf("unit" to "passed")), "reviewer", audit.occurredAt)
        ledger.apply(snapshot.revision, Transition.CloseTask(receipt))
        snapshot = FileTaskLedger(ledger.root, registry).snapshot()
        assertEquals(listOf(b.id), snapshot.frontier().tasks)
        assertEquals(listOf(receipt), snapshot.receipts)
        val noCode = FileTaskLedger(ledger.root).snapshot()
        assertEquals(snapshot.history, noCode.history)
        assertEquals(snapshot.receipts, noCode.receipts)
        assertTrue(noCode.currency().values.all { it.state == Currency.UNRESOLVED })
    }

    @Test fun `profile and planning histories coexist and missing pins cannot approve scope acceptance`() {
        val ledger = create(); ledger.apply(ledger.snapshot().revision, Transition.TrackPlanning)
        val old = ledger.snapshot().planningHistory
        ledger.apply(ledger.snapshot().revision, change(ledger))
        assertEquals(old, ledger.snapshot().planningHistory)
        val next = DraftEpic(EpicId.parseOrThrow("EPIC.result"), "Result", "Durable feature", listOf(a.id), protocol = PlanningRecordCodec.AUDITED_PROTOCOL, acceptance = listOf("Feature reviewed"))
        reconcile(ledger, a.id)
        ledger.apply(ledger.snapshot().revision, Transition.AddRecords(epics = listOf(next)))
        val snapshot = ledger.snapshot()
        val assertion = PlanningAssessment(null, next.id, snapshot.planningHistory!!.heads.getValue(next.id), snapshot.planningObservations(next), PlanningAssessmentOutcome.ACCEPTED,
            PlanningAudit(audit.actor, audit.occurredAt, audit.reason, audit.evidence), listOf("Observed result"))
        assertFails { FileTaskLedger(ledger.root).apply(snapshot.revision, Transition.AssessPlanning(assertion)) }
        ledger.apply(snapshot.revision, Transition.AssessPlanning(assertion))
        val cold = FileTaskLedger(ledger.root, registry).snapshot()
        assertEquals(PlanningAssessmentCurrency.CURRENT, cold.planningAssessmentStatuses(cold.planningHistory!!).getValue(assertion.id).currency)
        val missing = FileTaskLedger(ledger.root).snapshot()
        assertEquals(PlanningAssessmentCurrency.UNRESOLVED, missing.planningAssessmentStatuses(missing.planningHistory!!).getValue(assertion.id).currency)
    }

    @Test fun `profile history rejects tampered objects and mismatched policy without mutations`() {
        val ledger = create(); ledger.apply(ledger.snapshot().revision, change(ledger))
        val original = image(ledger.root)
        val policy = ledger.root.resolve(".agents/policy.toml")
        Files.writeString(policy, Files.readString(policy).replace(profile.digest.value, "minimal/alpha1"))
        assertFails { FileTaskLedger(ledger.root).snapshot() }
        Files.writeString(policy, original.getValue(".agents/policy.toml").first)
        val path = Files.list(ledger.root.resolve(".agents/profile-history/revisions")).use { it.findFirst().orElseThrow() }
        Files.writeString(path, Files.readString(path).replace("Reviewed provider", "Unrecorded audit"))
        val corrupt = image(ledger.root)
        assertFails { FileTaskLedger(ledger.root).snapshot() }
        assertEquals(corrupt, image(ledger.root))
    }

    private fun interruptedProfile(ledger: FileTaskLedger): Map<String, String> {
        val before = ledger.snapshot(); val after = LedgerTransitions.evolve(before, change(ledger)); val history = after.profileHistory!!
        val digest = history.profile.digest.value
        val writes = linkedMapOf(
            ".agents/config.toml" to Files.readString(ledger.root.resolve(".agents/config.toml")).replace("taskctl.native/alpha2", "taskctl.native/alpha5").replace("minimal/alpha1", digest),
            ".agents/policy.toml" to Files.readString(ledger.root.resolve(".agents/policy.toml")).replace("minimal/alpha1", digest),
            ".agents/profile-history/heads.json" to (Json.encode(ProfileCodec.heads(history)) + "\n"))
        history.revisions.forEach { (id, value) -> writes[".agents/profile-history/revisions/${id.value.removePrefix("sha256:")}.json"] = Json.encode(ProfileCodec.revision(value)) + "\n" }
        val operation = obj("contract" to StringValue("taskctl.transaction/alpha1"), "before" to ObjectValue(writes.keys.associateWith {
            val path = ledger.root.resolve(it); if (Files.exists(path)) StringValue(Files.readString(path)) else NullValue
        }), "after" to stringMap(writes))
        NativeFiles.atomicWrite(ledger.root, ".agents/runtime/transaction.json", Json.encode(operation))
        return writes
    }

    @Test fun `profile recovery completes partial policy writes and refuses external conflicts before writes`() {
        val ledger = create(); val original = taskImage(ledger.root); val writes = interruptedProfile(ledger)
        NativeFiles.atomicWrite(ledger.root, ".agents/policy.toml", writes.getValue(".agents/policy.toml"))
        assertFails { ledger.snapshot() }
        ledger.recover()
        assertEquals(profile, FileTaskLedger(ledger.root).snapshot().profileHistory!!.profile)
        assertEquals(original, taskImage(ledger.root))
        val conflict = create("conflict"); interruptedProfile(conflict)
        val path = conflict.root.resolve(".agents/policy.toml"); Files.writeString(path, Files.readString(path) + "# external edit\n")
        val stable = image(conflict.root)
        assertFails { conflict.recover() }
        assertEquals(stable, image(conflict.root).filterKeys { it != ".agents/runtime/lock" })
        assertFalse(Files.exists(conflict.root.resolve(".agents/profile-history")))
    }
}
