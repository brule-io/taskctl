package io.brule.tasking.conformance

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class RelatedLedgerConformanceTest {
    @TempDir lateinit var directory: Path
    private val time = OccurredAt.parseOrThrow("2026-09-08T03:00:00Z")
    private fun id(name: String) = RelatedLedgerFixture.id(name)
    private fun image(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString().replace('\\', '/') to Pair(Files.readString(it), Files.getLastModifiedTime(it))
    } }
    private fun create(name: String): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { if (!Files.exists(distribution.resolve(it))) Files.writeString(distribution.resolve(it), "fixture launcher\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val root = directory.resolve(name)
        Bootstrap.apply(Bootstrap.plan(root, "specimen.related.$name", "test", distribution.parent, lock, imported = RelatedLedgerFixture.admission(name)))
        Files.writeString(root.resolve("product.txt"), "Unrelated product content\n")
        return FileTaskLedger(root)
    }
    private fun review(ledger: FileTaskLedger, name: String): Reconciliation {
        val snapshot = ledger.snapshot(); val task = ledger.task(id(name))!!
        val observations = CurrencyEvaluation.observations(snapshot)
        return Reconciliation(task.id, snapshot.history!!.heads.getValue(task.id), ReviewOutcome.REVALIDATED,
            task.requires.sorted().map { observations.getValue(it) }, "related-ledger reviewer", time,
            "Reviewed current local contract and exact recorded inputs; no external freshness claim.", mapOf("observation" to "Explicit fictional observation"))
    }
    private fun reconcile(ledger: FileTaskLedger, name: String) = ledger.apply(ledger.snapshot().revision, Transition.ReconcileTask(review(ledger, name)))
    private fun reviewed(name: String) = create(name).also { ledger -> listOf("W1", "W2", "W3").forEach { reconcile(ledger, it) } }
    private fun observe(source: LedgerSnapshot): List<String> {
        val observed = CurrencyEvaluation.observations(source).getValue(id("W2"))
        return listOf("Observed repository: ${source.repositoryId}", "Observed task: ${observed.upstream.value}",
            "Observed contract: ${observed.observedContract!!.value}", "Observed transitive inputs: ${observed.observedInputs!!.value}")
    }
    private fun refresh(target: FileTaskLedger, source: LedgerSnapshot) {
        val observed = CurrencyEvaluation.observations(source).getValue(id("W2"))
        val task = target.task(id("W1"))!!
        val witness = obj("source_revision" to StringValue(RelatedLedgerFixture.REVISION), "ledger_revision" to StringValue(source.revision.value),
            "task_revision" to StringValue(observed.observedRevision!!.value))
        target.apply(target.snapshot().revision, Transition.ReviseTask(task.copy(requirements = observe(source),
            extensions = ObjectValue(task.extensions.fields + ("specimen.observation/v1" to witness)))))
    }
    private fun close(ledger: FileTaskLedger): ClosureEvidence {
        val task = ledger.task(id("W3"))!!
        val proof = ClosureEvidence(Receipt(task.id, DraftLifecycle.contract(task), mapOf("observation" to "Reviewed the exact local recorded observation.")), "related-ledger reviewer", time)
        ledger.apply(ledger.snapshot().revision, Transition.CloseTask(proof))
        return proof
    }

    @Test fun `related imports preserve separate ledger identities despite equal short task names and contracts`() {
        val alpha = create("alpha"); val beta = create("beta")
        val a = alpha.snapshot(); val b = beta.snapshot()
        assertNotEquals(a.repositoryId, b.repositoryId)
        assertEquals(a.universe.tasks.map { it.id }, b.universe.tasks.map { it.id })
        assertEquals(a.universe.tasks.map(DraftLifecycle::contract), b.universe.tasks.map(DraftLifecycle::contract))
        assertNotEquals(a.imports.single().manifest.id, b.imports.single().manifest.id)
        for (snapshot in listOf(a, b)) {
            val manifest = snapshot.imports.single().manifest
            assertEquals(RelatedLedgerFixture.REVISION, manifest.revision)
            assertEquals("conformance-related-ledger", manifest.adapter); assertEquals("1.0.0", manifest.adapterVersion)
            assertEquals(manifest, ImportCodec.decodeManifest(ImportCodec.manifest(manifest)))
            assertEquals(NullValue, ImportCodec.manifest(manifest).fields["source_native_protocol"])
            assertTrue(snapshot.receipts.isEmpty()); assertTrue(snapshot.frontier().tasks.isEmpty())
            assertEquals(Currency.AFFECTED, snapshot.currency().getValue(id("W1")).state)
            assertEquals(Currency.UNRESOLVED, snapshot.currency().getValue(id("W3")).state)
            assertNotEquals(manifest.sources.first().contract, DraftLifecycle.contract(snapshot.universe.tasks.first()))
            assertContains(manifest.files.getValue(manifest.sources.first().path), "\"checked\": false")
        }
        assertNotEquals(observe(a), observe(b)) // Repository identity is part of the manual contract.
    }

    @Test fun `external drift is invisible locally until explicit refresh then propagates transitively`() {
        val alpha = reviewed("alpha"); val beta = reviewed("beta")
        val aBefore = alpha.snapshot(); refresh(beta, aBefore)
        listOf("W1", "W2", "W3").forEach { reconcile(beta, it) }
        val receipt = close(beta); val bBefore = beta.snapshot(); val imageBefore = image(beta.root)
        val oldObservation = CurrencyEvaluation.observations(aBefore).getValue(id("W2"))
        val root = alpha.task(id("W1"))!!
        alpha.apply(aBefore.revision, Transition.ReviseTask(root.copy(requirements = listOf("Stronger source result required."))))
        reconcile(alpha, "W1"); reconcile(alpha, "W2")
        val aAfter = alpha.snapshot(); val now = CurrencyEvaluation.observations(aAfter).getValue(id("W2"))
        assertEquals(oldObservation.observedContract, now.observedContract)
        assertNotEquals(oldObservation.observedInputs, now.observedInputs)
        assertEquals(bBefore, FileTaskLedger(beta.root).snapshot())
        assertTrue(beta.snapshot().currency().values.all { it.state == Currency.CURRENT })
        assertEquals(imageBefore, image(beta.root)) // No implicit resolver or remote invalidation.
        val upstreamImage = image(alpha.root)
        refresh(beta, aAfter)
        assertEquals(Currency.AFFECTED, beta.snapshot().currency().getValue(id("W3")).state)
        reconcile(beta, "W1"); reconcile(beta, "W2")
        assertEquals(Currency.AFFECTED, beta.snapshot().currency().getValue(id("W3")).state)
        reconcile(beta, "W3")
        val cold = FileTaskLedger(beta.root).snapshot()
        assertTrue(cold.currency().values.all { it.state == Currency.CURRENT })
        assertEquals(listOf(receipt), cold.receipts)
        assertEquals(bBefore.imports, cold.imports)
        bBefore.history!!.revisions.forEach { (key, value) -> assertEquals(value, cold.history!!.revisions[key]) }
        assertEquals(upstreamImage, image(alpha.root))
        assertEquals(imageBefore.getValue("product.txt"), image(beta.root).getValue("product.txt"))
        assertFalse(Files.exists(alpha.root.resolve(".git"))); assertFalse(Files.exists(beta.root.resolve(".git")))
    }

    @Test fun `cosmetic source revisions retain semantic currency and exact historical observations`() {
        val alpha = reviewed("alpha"); val beta = reviewed("beta")
        refresh(beta, alpha.snapshot()); listOf("W1", "W2", "W3").forEach { reconcile(beta, it) }
        val aBefore = alpha.snapshot(); val bBefore = beta.snapshot()
        val oldEdge = bBefore.dependencies(id("W2")).single()
        val source = alpha.task(id("W2"))!!
        alpha.apply(aBefore.revision, Transition.ReviseTask(source.copy(title = "Cosmetic new source title")))
        val aAfter = alpha.snapshot()
        assertNotEquals(aBefore.history!!.heads[id("W2")], aAfter.history!!.heads[id("W2")])
        assertEquals(observe(aBefore), observe(aAfter))
        refresh(beta, aAfter)
        val after = beta.snapshot()
        assertEquals(DraftLifecycle.contract(bBefore.universe.tasks.first()), DraftLifecycle.contract(after.universe.tasks.first()))
        assertNotEquals(bBefore.history!!.heads[id("W1")], after.history!!.heads[id("W1")])
        assertEquals(oldEdge, after.dependencies(id("W2")).single()) // The originally observed HEAD remains exact.
        assertTrue(after.currency().values.all { it.state == Currency.CURRENT })
        val beforeReads = image(directory)
        FileTaskLedger(alpha.root).snapshot(); FileTaskLedger(beta.root).snapshot(); beta.frontier(); beta.task(id("W2"))
        assertEquals(beforeReads, image(directory))
    }

    @Test fun `a reused task identity from another ledger cannot satisfy the previous observation contract`() {
        val alpha = reviewed("alpha"); val beta = reviewed("beta")
        val a = alpha.snapshot(); val b = beta.snapshot()
        assertEquals(CurrencyEvaluation.observations(a)[id("W2")]!!.observedContract, CurrencyEvaluation.observations(b)[id("W2")]!!.observedContract)
        val local = beta.task(id("W1"))!!
        assertNotEquals(DraftLifecycle.contract(local.copy(requirements = observe(a))), DraftLifecycle.contract(local.copy(requirements = observe(b))))
        assertFails { Dependency(TaskId.parseOrThrow("TASK.missing")) .let { missing ->
            beta.apply(b.revision, Transition.ReviseTask(local.copy(requires = listOf(missing.upstream))))
        } }
        assertEquals(b, beta.snapshot())
    }

    @Test fun `stale refresh and stale actor observations reject without touching either ledger`() {
        val alpha = reviewed("alpha"); val beta = reviewed("beta")
        val old = beta.snapshot(); val staleReview = review(beta, "W2")
        refresh(beta, alpha.snapshot()); reconcile(beta, "W1")
        val before = image(directory)
        assertFailsWith<RevisionConflict> { beta.apply(old.revision, Transition.ReviseTask(beta.task(id("W1"))!!.copy(requirements = listOf("Stale write")))) }
        assertFails { beta.apply(beta.snapshot().revision, Transition.ReconcileTask(staleReview)) }
        assertEquals(before, image(directory))
    }

    @Test fun `dialect labels cannot conceal stateful restrictions or a mismatched source ledger`() {
        val original = RelatedLedgerFixture.files("alpha"); val path = "alpha/W1.json"
        val parsed = YamlValues.parse(original.getValue(path)).value as ObjectValue
        val changes = listOf(parsed.fields + ("requires_host" to StringValue("unmapped")),
            parsed.fields + ("state" to StringValue("claimed")), parsed.fields + ("ledger" to StringValue("beta")),
            parsed.fields + ("dialect" to StringValue("tasking/core-draft-2")))
        changes.forEach { fields -> assertFails { RelatedLedgerFixture.manifest("alpha", original + (path to Json.encode(ObjectValue(fields)))) } }
        assertFails { RelatedLedgerFixture.manifest("alpha", original - path) }
    }
}
