package io.brule.tasking.conformance

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class WorkspaceCapabilityConformanceTest {
    @TempDir lateinit var directory: Path
    private val fixture = WorkspaceCapabilityFixture
    private var next = 0
    private fun id(value: String) = fixture.id(value)
    private fun create(activate: Boolean = true, records: List<DraftRecord> = fixture.records()): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "fixture launcher\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val root = directory.resolve("project-${next++}")
        Bootstrap.apply(Bootstrap.plan(root, "conformance.workspace", "test", distribution.parent, lock))
        Files.writeString(root.resolve("source.txt"), "Untouched consumer source\n")
        val ledger = FileTaskLedger(root, fixture.registry)
        if (activate) ledger.apply(ledger.snapshot().revision, Transition.SetProfile(ProfileChange(null, fixture.profile, fixture.audit)))
        ledger.apply(ledger.snapshot().revision, Transition.AddRecords(records))
        return ledger
    }
    private fun image(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString().replace('\\', '/') to Pair(Files.readString(it), Files.getLastModifiedTime(it))
    } }
    private fun probe(snapshot: LedgerSnapshot, task: TaskId = id("work")): Map<String, String> =
        EnvironmentProbe { fixture.observation(snapshot.universe.tasks.single { it.id == task }) }.probe()
    private fun close(ledger: FileTaskLedger, task: TaskId, supplied: Map<String, String>? = null) {
        val snapshot = ledger.snapshot(); val record = snapshot.universe.tasks.single { it.id == task }
        val evidence = supplied ?: fixture.evidence(snapshot, task)
        ledger.apply(snapshot.revision, Transition.CloseTask(ClosureEvidence(Receipt(task, snapshot.effectiveContract(record), evidence), "fixture reviewer", fixture.time)))
    }
    private fun reviewedPrerequisites(): FileTaskLedger = create().also { close(it, id("authority")); close(it, id("environment")) }
    private fun reconcile(ledger: FileTaskLedger, task: TaskId, evidence: Map<String, String>? = null) {
        val snapshot = ledger.snapshot(); val observations = CurrencyEvaluation.observations(snapshot)
        val review = Reconciliation(task, snapshot.history!!.heads.getValue(task), ReviewOutcome.REVALIDATED,
            snapshot.effectiveEvaluation().dependencies.getValue(task).sorted().map { observations.getValue(it) }, "fixture reviewer", fixture.time,
            "Review the exact current contract and contributed inputs", evidence ?: fixture.evidence(snapshot, task), profile = snapshot.profileHistory!!.profile.digest)
        ledger.apply(snapshot.revision, Transition.ReconcileTask(review))
    }

    @Test fun `installed code leaves unknown optional payloads inactive and lossless`() {
        val original = fixture.records().single { it.id == id("optional") }
        val optional = original.copy(extensions = ObjectValue(original.extensions.fields + (fixture.workspace.value to obj("not_an_active_schema" to BooleanValue(true)))))
        val ledger = create(false, listOf(optional)); val before = ledger.snapshot(); val bytes = image(ledger.root)
        assertNull(before.profileHistory)
        assertEquals(optional.extensions, FileTaskLedger(ledger.root).snapshot().universe.tasks.single().extensions)
        assertEquals(listOf(optional.id), ledger.frontier().tasks)
        assertEquals(bytes, image(ledger.root))
        val changed = optional.copy(extensions = ObjectValue(optional.extensions.fields + ("unrecognized.host/v4" to obj("operator" to StringValue("claim all authority")))))
        ledger.apply(before.revision, Transition.ReviseTask(changed))
        assertEquals(DraftLifecycle.contract(optional), DraftLifecycle.contract(changed))
        assertEquals(Currency.CURRENT, ledger.snapshot().currency().getValue(optional.id).state)
        assertEquals("Untouched consumer source\n", Files.readString(ledger.root.resolve("source.txt")))
        assertFails { DraftDocument.parse(Json.encode(ObjectValue(NativeCodec.task(optional).fields + ("host" to StringValue("not a core field"))))) }
    }

    @Test fun `persisted workspace and environment pins contribute an orthogonal causal graph`() {
        val ledger = create(); val snapshot = ledger.snapshot()
        assertEquals(fixture.profile, snapshot.profileHistory!!.profile)
        val task = snapshot.universe.tasks.single { it.id == id("work") }
        assertTrue(task.requires.isEmpty())
        val dependencies = listOf(id("authority"), id("environment"))
        assertEquals(dependencies, snapshot.history!!.head(task.id).dependencies.map { it.upstream })
        assertEquals(dependencies, snapshot.effectiveEvaluation().dependencies.getValue(task.id).sorted())
        assertEquals(listOf(id("authority"), id("environment"), id("optional")), ledger.frontier().tasks)
        val cold = FileTaskLedger(ledger.root, fixture.registry).snapshot()
        assertEquals(snapshot, cold); cold.history!!.validate(cold.universe, cold.receipts, cold.imports)
        val before = image(ledger.root)
        assertFails { close(ledger, task.id, fixture.evidence(snapshot, task.id, probe(snapshot))) }
        assertEquals(before, image(ledger.root)) // Provider evidence cannot close open prerequisites.
        close(ledger, id("authority")); close(ledger, id("environment"))
        assertEquals(listOf(id("optional"), id("work")), ledger.frontier().tasks)
    }

    @Test fun `deterministic reads and validation never run probes and observation is not automatic approval`() {
        val ledger = reviewedPrerequisites(); val snapshot = ledger.snapshot(); var probes = 0
        val explicitProbe = EnvironmentProbe { probes++; fixture.observation(snapshot.universe.tasks.single { it.id == id("work") }) }
        val before = image(ledger.root)
        ledger.snapshot().effectiveEvaluation(); ledger.frontier(); ledger.task(id("work")); snapshot.currency()
        assertFails { close(ledger, id("work")) }
        assertEquals(0, probes); assertEquals(before, image(ledger.root))
        val observed = explicitProbe.probe()
        assertEquals(1, probes)
        assertFails { close(ledger, id("work"), fixture.evidence(snapshot, id("work"), observed + ("status" to "unavailable"))) }
        assertFails { close(ledger, id("work"), fixture.evidence(snapshot, id("work"), observed + ("host" to "different-host"))) }
        val evidence = fixture.evidence(snapshot, id("work"), observed)
        assertFails { close(ledger, id("work"), evidence - "test") }
        assertFails { close(ledger, id("work"), evidence - "workspace.review") }
        close(ledger, id("work"), evidence)
        assertEquals(1, probes)
        val cold = FileTaskLedger(ledger.root, fixture.registry).snapshot()
        assertEquals(evidence, cold.receipts.single { it.receipt.taskId == id("work") }.receipt.evidence)
        assertContains(ledger.frontier().tasks, id("downstream"))
        val after = image(ledger.root)
        assertFails { close(ledger, id("work"), evidence) }
        assertEquals(after, image(ledger.root))
    }

    @Test fun `provider evidence cannot override a held policy empty core criteria or out of scope claims`() {
        val ledger = reviewedPrerequisites(); val snapshot = ledger.snapshot()
        val held = id("held")
        assertTrue(snapshot.effectiveEvaluation().contributions.getValue(held).blockers.isNotEmpty())
        assertFails { close(ledger, held, fixture.evidence(snapshot, held, probe(snapshot, held))) }
        assertFails { reconcile(ledger, held, fixture.evidence(snapshot, held, probe(snapshot, held))) }
        val work = snapshot.universe.tasks.single { it.id == id("work") }
        val evidence = fixture.evidence(snapshot, work.id, probe(snapshot))
        val claim = YamlValues.parse(evidence.getValue("workspace.review")).value as ObjectValue
        val outOfScope = Json.encode(ObjectValue(claim.fields + ("mutations" to ArrayValue(listOf(obj("repository" to StringValue("other-component"), "paths" to strings(listOf("**"))))))))
        assertFails { close(ledger, work.id, evidence + ("workspace.review" to outOfScope)) }
        assertFails { ledger.apply(snapshot.revision, Transition.ReviseTask(work.copy(acceptance = emptyList()))) }
        assertEquals(snapshot, ledger.snapshot()) // Invalid core intent cannot cross the typed boundary.
        assertEquals("Untouched consumer source\n", Files.readString(ledger.root.resolve("source.txt")))
    }

    @Test fun `contributed authority drift remains transitive after intermediate review and keeps receipts`() {
        val ledger = reviewedPrerequisites()
        close(ledger, id("work"), fixture.evidence(ledger.snapshot(), id("work"), probe(ledger.snapshot())))
        close(ledger, id("downstream"))
        val before = ledger.snapshot(); val receipts = before.receipts
        val authority = before.universe.tasks.single { it.id == id("authority") }
        ledger.apply(before.revision, Transition.ReviseTask(authority.copy(requirements = listOf("Review a stronger workspace authority contract"))))
        assertEquals(Currency.AFFECTED, ledger.snapshot().currency().getValue(id("downstream")).state)
        reconcile(ledger, authority.id)
        reconcile(ledger, id("work"), fixture.evidence(ledger.snapshot(), id("work"), probe(ledger.snapshot())))
        assertEquals(Currency.AFFECTED, ledger.snapshot().currency().getValue(id("downstream")).state)
        assertEquals(Currency.CURRENT, ledger.snapshot().currency().getValue(id("optional")).state)
        reconcile(ledger, id("downstream"))
        assertEquals(receipts, ledger.snapshot().receipts)
        assertEquals(before.universe.tasks.map { it.id to it.requires }, ledger.snapshot().universe.tasks.map { it.id to it.requires })
        assertTrue(ledger.snapshot().currency().values.all { it.state == Currency.CURRENT })
    }

    @Test fun `an environment contract change requires fresh evidence from every activated capability`() {
        val ledger = reviewedPrerequisites(); val before = ledger.snapshot(); val work = before.universe.tasks.single { it.id == id("work") }
        val old = fixture.evidence(before, work.id, probe(before))
        close(ledger, work.id, old)
        val closed = ledger.snapshot(); val currentRecord = closed.universe.tasks.single { it.id == work.id }
        ledger.apply(closed.revision, Transition.ReviseTask(currentRecord.copy(extensions = ObjectValue(currentRecord.extensions.fields +
            (fixture.environment.value to fixture.environmentPayload(host = "fixture-new-host"))))))
        val changed = ledger.snapshot(); val fresh = fixture.evidence(changed, work.id, probe(changed))
        assertNotEquals(before.effectiveContract(work), changed.effectiveContract(changed.universe.tasks.single { it.id == work.id }))
        assertFails { reconcile(ledger, work.id, old) }
        assertFails { reconcile(ledger, work.id, fresh + ("workspace.review" to old.getValue("workspace.review"))) }
        reconcile(ledger, work.id, fresh)
        assertEquals(Currency.CURRENT, ledger.snapshot().currency().getValue(work.id).state)
        assertEquals(closed.receipts, ledger.snapshot().receipts)
        assertEquals(old, ledger.snapshot().receipts.single { it.receipt.taskId == work.id }.receipt.evidence)
    }

    @Test fun `missing or mismatched code fails closed without erasing stored profile or opaque state`() {
        val ledger = reviewedPrerequisites(); val before = ledger.snapshot(); val bytes = image(ledger.root)
        val wrong = object : PinnedSemanticProvider by fixture.environmentProvider {
            override val pin = fixture.environmentPin.copy(version = ProviderVersion.parseOrThrow("1.0.1"))
        }
        for (registry in listOf(ProviderRegistry(), ProviderRegistry(listOf(fixture.workspaceProvider)), ProviderRegistry(listOf(fixture.workspaceProvider, wrong)))) {
            val unavailable = FileTaskLedger(ledger.root, registry); val snapshot = unavailable.snapshot()
            assertEquals(before.profileHistory, snapshot.profileHistory); assertEquals(before.universe, snapshot.universe)
            assertEquals(before.history, snapshot.history)
            assertTrue(snapshot.currency().values.all { it.state == Currency.UNRESOLVED })
            assertFails { unavailable.frontier() }
            assertFails { reconcile(unavailable, id("work"), fixture.evidence(before, id("work"), probe(before))) }
            assertEquals(bytes, image(ledger.root))
        }
    }

    @Test fun `malformed required payloads and unsupported stateful labels are never downgraded to optional data`() {
        val base = fixture.task("bad", extensions = obj(fixture.workspace.value to fixture.workspacePayload()), required = listOf(fixture.workspace.value))
        val roots = fixture.records().filter { it.id in listOf(id("authority"), id("environment")) }
        fun withPayload(payload: ObjectValue) = base.copy(extensions = obj(fixture.workspace.value to payload))
        val malformed = listOf(base.copy(extensions = obj()),
            withPayload(fixture.workspacePayload(paths = listOf("../outside"))),
            withPayload(fixture.workspacePayload(paths = listOf("C:\\outside"))),
            withPayload(ObjectValue(fixture.workspacePayload().fields + ("claim" to StringValue("approved")))))
        for (record in malformed) {
            val ledger = create(records = roots); val bytes = image(ledger.root)
            assertFails { ledger.apply(ledger.snapshot().revision, Transition.AddRecords(listOf(record))) }
            assertEquals(bytes, image(ledger.root))
        }
        val unsupported = create(false, roots + base.copy(requiredExtensions = listOf("conformance.stateful-claim/v1")))
        assertFails { unsupported.frontier() }
        val before = image(unsupported.root)
        assertFails { close(unsupported, base.id, mapOf("test" to "declared ready", "operator" to "approved", "claim" to "held")) }
        assertEquals(before, image(unsupported.root))
        assertFails { fixture.task("stateful").copy(state = "claimed") }
    }

    @Test fun `programmatic task extension invariants fail before any ledger write`() {
        val ledger = create(false, listOf(fixture.task("baseline")))
        val snapshot = ledger.snapshot(); val before = image(ledger.root)
        val record = fixture.task("invalid-namespace")
        val invalid: List<(DraftRecord) -> DraftRecord> = listOf(
            { it.copy(extensions = obj("banana" to StringValue("not a namespace"))) },
            { it.copy(requiredExtensions = listOf("banana")) },
            { it.copy(requiredExtensions = listOf(fixture.workspace.value, fixture.workspace.value)) })
        for (change in invalid) {
            assertFails { ledger.apply(snapshot.revision, Transition.AddRecords(listOf(change(record)))) }
            assertEquals(before, image(ledger.root))
            assertEquals(snapshot, ledger.snapshot())
        }
    }
}
