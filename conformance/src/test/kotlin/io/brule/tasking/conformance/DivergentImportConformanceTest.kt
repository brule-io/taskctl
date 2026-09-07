package io.brule.tasking.conformance

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class DivergentImportConformanceTest {
    @TempDir lateinit var directory: Path
    private fun resource(name: String): String = requireNotNull(javaClass.getResourceAsStream("/divergent-import/$name"))
        .use { it.readBytes().toString(Charsets.UTF_8) }
    private fun index(): ObjectValue = YamlValues.parse(resource("sources.json")).value as ObjectValue
    private fun source(): Path {
        val root = directory.resolve("object work source")
        val index = index()
        assertEquals("independently-authored-fictional-conformance-specimen", index.requiredString("classification"))
        (index.fields.getValue("files") as ObjectValue).fields.forEach { (path, digest) ->
            val text = resource("source/$path")
            assertEquals((digest as StringValue).value, Canonical.sha256(text.toByteArray(Charsets.UTF_8)))
            Files.createDirectories(root.resolve(path).parent)
            Files.writeString(root.resolve(path), text)
        }
        return root
    }
    private fun inspect(root: Path): ImportManifest = ObjectWorkAdapter.inspect(root,
        index().requiredString("repository"), index().requiredString("revision"))
    private fun id(value: String): TaskId = ObjectWorkAdapter.taskId(value)
    private fun inventory(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString() to Pair(Canonical.sha256(Files.readAllBytes(it)), Files.getLastModifiedTime(it))
    } }
    private fun admission(manifest: ImportManifest) = ImportAdmission(manifest, ImportReview(manifest.id, "specimen reviewer",
        "2026-09-07T00:00:00Z", "Reviewed the fictional bounded mapping; no historical test execution is claimed."))
    private fun bootstrap(manifest: ImportManifest): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap")
        Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "test launcher\n") }
        val target = directory.resolve("disposable target")
        Files.createDirectories(target)
        Files.writeString(target.resolve("AGENTS.md"), "Existing authority\n")
        Files.writeString(target.resolve("product.txt"), "Untouched product\n")
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val before = inventory(target)
        val plan = Bootstrap.plan(target, "specimen", "test", distribution.parent, lock, adopt = true, imported = admission(manifest))
        assertEquals(before, inventory(target))
        assertTrue(plan.files.keys.all { it.startsWith(".agents/") || it.startsWith(".taskctl/") || it in setOf("taskctl", "taskctl.ps1", "taskctl.bat") })
        Bootstrap.apply(plan)
        before.forEach { (path, value) -> assertEquals(value, inventory(target).getValue(path)) }
        assertFalse(Files.exists(target.resolve(".git")))
        return FileTaskLedger(target)
    }
    private fun reconcile(ledger: FileTaskLedger, task: TaskId) {
        val snapshot = ledger.snapshot()
        val record = snapshot.universe.tasks.single { it.id == task }
        val observations = CurrencyEvaluation.observations(snapshot)
        val review = Reconciliation(task, snapshot.history!!.heads.getValue(task), ReviewOutcome.REVALIDATED,
            record.requires.sorted().map { observations.getValue(it) }, "specimen reviewer", "2026-09-07T00:00:00Z",
            "Explicitly reviewed current fictional contract and inputs; retained historical narrative unchanged.", mapOf("specimen" to "Bounded conformance assertion."))
        ledger.apply(snapshot.revision, Transition.ReconcileTask(review))
    }
    private fun transform(root: Path, name: String = "WORK-2", edit: (ObjectValue) -> ObjectValue) {
        val path = root.resolve("work/$name.json")
        val current = YamlValues.parse(Files.readString(path)).value as ObjectValue
        Files.writeString(path, Json.encode(edit(current)) + "\n")
    }

    @Test fun `nested object dialect preserves complete identities exact values and source provenance`() {
        val root = source(); val before = inventory(root); val manifest = inspect(root)
        assertEquals(before, inventory(root))
        assertEquals("conformance-object-work", manifest.adapter)
        assertEquals("1.0.0", manifest.adapterVersion)
        assertEquals("7426b442a1fc217894af2c6f9f77ce8bc05413af", manifest.revision)
        assertEquals(setOf(id("WORK-1"), id("WORK-01"), id("WORK-2")), manifest.universe.tasks.map { it.id }.toSet())
        assertTrue(manifest.universe.roadmaps.isEmpty()); assertEquals(2, manifest.universe.epics.size)
        assertEquals(listOf(id("WORK-2")), manifest.universe.frontier())
        val record = manifest.universe.tasks.single { it.id == id("WORK-1") }
        val legacy = record.extensions.fields.getValue(ObjectWorkAdapter.EXTENSION) as ObjectValue
        val labels = legacy.fields.getValue("labels") as ObjectValue
        assertEquals(IntegerValue(BigInteger("900719925474099312345678901234567890")), labels.fields["exact_integer"])
        assertEquals(DecimalValue(BigDecimal("0.123456789012345678901234567890")), labels.fields["exact_decimal"])
        assertEquals(manifest, ImportCodec.decodeManifest(YamlValues.parse(Json.encode(ImportCodec.manifest(manifest))).value as ObjectValue))
        assertNotEquals(manifest.sources.single { it.id == record.id }.contract, DraftLifecycle.contract(record))
        assertEquals(NullValue, ImportCodec.manifest(manifest).fields["source_native_protocol"])
    }

    @Test fun `ordinary file lifecycle requires current inputs and preserves historical evidence across restart`() {
        val root = source(); val sourceBefore = inventory(root); val manifest = inspect(root); val ledger = bootstrap(manifest)
        val imported = ledger.snapshot()
        assertTrue(imported.receipts.isEmpty()); assertTrue(ledger.frontier().tasks.isEmpty())
        assertEquals(Currency.AFFECTED, imported.currency().getValue(id("WORK-1")).state)
        assertEquals(Currency.UNRESOLVED, imported.currency().getValue(id("WORK-2")).state)
        val beforeRejected = inventory(ledger.root)
        assertFails { reconcile(ledger, id("WORK-2")) }
        assertEquals(beforeRejected, inventory(ledger.root))
        listOf("WORK-1", "WORK-01", "WORK-2").forEach { reconcile(ledger, id(it)) }
        assertEquals(listOf(id("WORK-2")), ledger.frontier().tasks)
        val current = ledger.snapshot(); val task = ledger.task(id("WORK-2"))!!
        val receipt = ClosureEvidence(Receipt(task.id, DraftLifecycle.contract(task), mapOf("specimen" to "Current fictional check verified.")),
            "specimen reviewer", "2026-09-07T00:00:00Z")
        val writesBefore = inventory(ledger.root)
        assertTrue(ledger.plan(current.revision, Transition.CloseTask(receipt)).requiredArray("writes").isNotEmpty())
        assertEquals(writesBefore, inventory(ledger.root))
        assertFailsWith<RevisionConflict> { ledger.apply(imported.revision, Transition.CloseTask(receipt)) }
        assertEquals(writesBefore, inventory(ledger.root))
        ledger.apply(current.revision, Transition.CloseTask(receipt))
        val cold = FileTaskLedger(ledger.root); val closed = cold.snapshot()
        assertEquals(listOf(receipt), closed.receipts); assertTrue(cold.frontier().tasks.isEmpty())
        assertEquals(manifest, closed.imports.single().manifest)
        assertTrue(imported.history!!.revisions.all { (key, value) -> closed.history!!.revisions[key] == value })
        val beforeReads = inventory(ledger.root)
        assertEquals(closed, cold.snapshot()); cold.task(task.id); cold.frontier()
        assertEquals(beforeReads, inventory(ledger.root)); assertEquals(sourceBefore, inventory(root))
        cold.apply(closed.revision, Transition.ReviseTask(task.copy(state = "closed", acceptance = listOf("Stronger result required."))))
        assertEquals(Currency.AFFECTED, cold.snapshot().currency().getValue(task.id).state)
        assertFalse(DraftLifecycle.addresses(receipt.receipt, cold.task(task.id)!!))
        assertEquals(listOf(receipt), cold.snapshot().receipts)
        assertEquals("closed", cold.task(id("WORK-1"))!!.state) // Historical unchecked flag never erases closure.
    }

    @Test fun `upstream changes remain transitively affected after intermediate reconciliation`() {
        val ledger = bootstrap(inspect(source()))
        listOf("WORK-1", "WORK-01", "WORK-2").forEach { reconcile(ledger, id(it)) }
        val root = ledger.task(id("WORK-1"))!!
        ledger.apply(ledger.snapshot().revision, Transition.ReviseTask(root.copy(requirements = listOf("Keep bytes and audit their origin."))))
        assertTrue(ledger.frontier().tasks.isEmpty())
        reconcile(ledger, id("WORK-1")); reconcile(ledger, id("WORK-01"))
        assertEquals(Currency.AFFECTED, ledger.snapshot().currency().getValue(id("WORK-2")).state)
        assertTrue(ledger.frontier().tasks.isEmpty())
        reconcile(ledger, id("WORK-2"))
        assertEquals(listOf(id("WORK-2")), ledger.frontier().tasks)
    }

    @Test fun `schema label cannot admit another structure or silently discard execution restrictions`() {
        val root = source()
        val original = Files.readString(root.resolve("work/WORK-2.json"))
        for (state in listOf("claimed", "blocked")) {
            transform(root) { record -> ObjectValue(record.fields + ("lifecycle" to obj("state" to StringValue(state), "narrative" to NullValue))) }
            val error = assertFails { inspect(root) }
            assertContains(error.message.orEmpty(), "execution-preserving mapping")
            Files.writeString(root.resolve("work/WORK-2.json"), original)
        }
        for (key in listOf("claim", "requires_host", "requires_operator", "unknown_semantics")) {
            transform(root) { ObjectValue(it.fields + (key to StringValue("must not disappear"))) }
            assertFails { inspect(root) }
            Files.writeString(root.resolve("work/WORK-2.json"), original)
        }
        Files.writeString(root.resolve("work/WORK-2.json"), "schema: specimen.work/1\nref: WORK-2\ndepends: []\n")
        assertFails { inspect(root) }
    }

    @Test fun `unknown contract fields wrong types missing parents and cycles are rejected`() {
        val root = source(); val path = root.resolve("work/WORK-2.json"); val original = Files.readString(path)
        transform(root) { record -> val contract = record.fields.getValue("contract") as ObjectValue
            ObjectValue(record.fields + ("contract" to ObjectValue(contract.fields + ("predicate" to StringValue("false"))))) }
        assertFails { inspect(root) }; Files.writeString(path, original)
        transform(root) { ObjectValue(it.fields + ("labels" to ArrayValue(emptyList()))) }
        assertFails { inspect(root) }; Files.writeString(path, original)
        transform(root) { ObjectValue(it.fields + ("feature" to StringValue("FEATURE-MISSING"))) }
        assertFails { inspect(root) }; Files.writeString(path, original)
        transform(root, "WORK-1") { record -> val contract = record.fields.getValue("contract") as ObjectValue
            ObjectValue(record.fields + ("contract" to ObjectValue(contract.fields + ("prerequisites" to strings(listOf("WORK-2")))))) }
        assertFails { inspect(root) }
    }

    @Test fun `source and adapter identity changes require a new review even for unchanged target contracts`() {
        val root = source(); val first = inspect(root)
        val revised = first.copy(adapterVersion = "1.0.1")
        assertNotEquals(first.id, revised.id)
        assertFails { ImportAdmission(revised, admission(first).review) }
        Files.writeString(root.resolve("README.md"), Files.readString(root.resolve("README.md")) + "Additional historical context.\n")
        val second = inspect(root)
        assertNotEquals(first.id, second.id)
        assertEquals(first.universe, second.universe)
        assertFails { ImportAdmission(second, admission(first).review) }
        assertFails { first.copy(files = first.files + ("work/WORK-1.json" to "forged")) }
    }

    @Test fun `canonical imported preimage is available for packaged process parity`() {
        val manifest = inspect(source())
        val ledger = bootstrap(manifest)
        val files = Files.walk(ledger.root.resolve(".agents")).use { paths ->
            paths.filter { Files.isRegularFile(it) }.sorted().toList().associate { path ->
                ledger.root.relativize(path).toString().replace('\\', '/') to Files.readString(path)
            }
        }
        val projection = obj("protocol" to StringValue("taskctl.conformance-preimage/1"),
            "adapter" to StringValue(manifest.adapter), "adapter_version" to StringValue(manifest.adapterVersion),
            "source_revision" to StringValue(manifest.revision), "manifest_id" to StringValue(manifest.id.value), "files" to stringMap(files))
        assertEquals(projection, YamlValues.parse(Json.encode(projection)).value)
        // The JVM build emits the canonical preimage for the separate executable
        // parity driver. Native tests repeat the same semantic assertions.
        System.getProperty("tasking.divergent.output")?.let { name ->
            val path = Path.of(name)
            Files.createDirectories(path.parent)
            Files.writeString(path, Json.encode(projection) + "\n")
        }
    }
}
