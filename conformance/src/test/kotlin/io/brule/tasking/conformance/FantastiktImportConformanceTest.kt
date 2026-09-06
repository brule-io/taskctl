package io.brule.tasking.conformance

import io.brule.tasking.core.*
import io.brule.tasking.compatibility.FantastiktImport
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class FantastiktImportConformanceTest {
    @TempDir lateinit var directory: Path
    private fun text(name: String) = requireNotNull(javaClass.getResourceAsStream("/fantastikt-import/$name")).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun source(): Path {
        val root = directory.resolve("ancestral source")
        val index = YamlValues.parse(text("sources.json")).value as ObjectValue
        assertEquals("b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4", index.requiredString("revision"))
        (index.fields.getValue("files") as ObjectValue).fields.forEach { (name, digest) ->
            val content = text(name)
            assertEquals((digest as StringValue).value, Canonical.sha256(content.toByteArray(Charsets.UTF_8)))
            val path = root.resolve(name); Files.createDirectories(path.parent); Files.writeString(path, content)
        }
        return root
    }
    private fun inspect(root: Path) = FantastiktImport.inspect(root, "fantastikt", "b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4")
    private fun admission(manifest: ImportManifest) = ImportAdmission(manifest, ImportReview(manifest.id, "test reviewer", "2026-09-06T00:00:00Z", "Reviewed conversion and retained historical classification."))
    private fun empty() = LedgerSnapshot("target", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial()))
    private fun inventory(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString() to Pair(Canonical.sha256(Files.readAllBytes(it)), Files.getLastModifiedTime(it))
    } }
    private fun review(snapshot: LedgerSnapshot, task: DraftRecord): Reconciliation {
        val observations = CurrencyEvaluation.observations(snapshot)
        return Reconciliation(task.id, snapshot.history!!.heads.getValue(task.id), ReviewOutcome.REVALIDATED,
            task.requires.sorted().map { observations.getValue(it) }, "test reviewer", "2026-09-06T00:00:00Z",
            "Reviewed projection and current inputs; original closure remains historical.", mapOf("mapping" to "Explicit test assertion about contract/input correspondence."))
    }
    @Test fun `real source preserves forty identities historical bytes and orthogonal planning`() {
        val source = source(); val before = inventory(source)
        val manifest = inspect(source)
        assertEquals(before, inventory(source))
        assertEquals(manifest, ImportCodec.decodeManifest(ImportCodec.manifest(manifest)))
        assertEquals(40, manifest.universe.tasks.size)
        assertEquals(29, manifest.universe.tasks.count { it.state == "closed" })
        assertEquals(1, manifest.universe.roadmaps.size); assertEquals(1, manifest.universe.epics.size)
        assertEquals(listOf(TaskId.parseOrThrow("TASK.draft-mvp.038")), manifest.universe.frontier())
        assertEquals(40, manifest.universe.roadmaps.single().tasks.size)
        assertEquals(manifest.universe.roadmaps.single().tasks, manifest.universe.epics.single().tasks)
        val task = manifest.universe.tasks.single { it.id.value == "TASK.draft-mvp.029" }
        assertEquals(8, task.requires.size)
        assertTrue(task.requirements.single().contains("Keep Task 008's `ball etl doctor` registration visibly unimplemented"))
        val origin = manifest.sources.single { it.id == task.id }
        assertEquals("closed", origin.state)
        assertNotEquals(origin.contract, DraftLifecycle.contract(task)) // Different, explicitly named projections.
        assertEquals(Files.readString(source.resolve(origin.path)), manifest.files.getValue(origin.path))
        assertTrue(manifest.files.getValue(origin.path).contains("187 tests, 0 failures, 0 errors, 0 skipped"))
        assertEquals(NullValue, ImportCodec.manifest(manifest).fields["source_native_protocol"])
    }
    @Test fun `import preserves closure without fabricating receipts and review is a separate transition`() {
        val manifest = inspect(source())
        var snapshot = LedgerTransitions.evolve(empty(), Transition.ImportRecords(admission(manifest)))
        val original = snapshot.history!!
        snapshot.history!!.validate(snapshot.universe, snapshot.receipts, snapshot.imports)
        assertTrue(snapshot.receipts.isEmpty())
        val root = snapshot.universe.tasks.single { it.id.value == "TASK.draft-mvp.001" }
        assertEquals(Currency.AFFECTED, snapshot.currency().getValue(root.id).state)
        val downstream = snapshot.universe.tasks.single { it.id.value == "TASK.draft-mvp.029" }
        assertEquals(Currency.UNRESOLVED, snapshot.currency().getValue(downstream.id).state)
        assertFails { LedgerTransitions.evolve(snapshot, Transition.ReconcileTask(review(snapshot, downstream))) }
        val pending = snapshot.universe.tasks.associateBy { it.id }.toMutableMap()
        while (pending.isNotEmpty()) {
            val ready = pending.values.filter { task -> task.requires.none { it in pending } }.sortedBy { it.id }
            assertTrue(ready.isNotEmpty())
            ready.forEach { task ->
                if (snapshot.currency().getValue(task.id).state != Currency.CURRENT) {
                    snapshot = LedgerTransitions.evolve(snapshot, Transition.ReconcileTask(review(snapshot, task)))
                }
                pending.remove(task.id)
            }
        }
        assertTrue(snapshot.currency().values.all { it.state == Currency.CURRENT })
        assertEquals(manifest.universe, snapshot.universe)
        assertTrue(snapshot.receipts.isEmpty())
        assertTrue(original.revisions.all { (id, value) -> snapshot.history!!.revisions[id] == value })
        assertEquals(manifest, snapshot.imports.single().manifest)
        snapshot = LedgerTransitions.evolve(snapshot, Transition.ReviseTask(downstream.copy(acceptance = listOf("A newly strengthened result."))))
        assertEquals("closed", snapshot.universe.tasks.single { it.id == downstream.id }.state)
        assertEquals(Currency.AFFECTED, snapshot.currency().getValue(downstream.id).state)
        snapshot.history!!.validate(snapshot.universe, snapshot.receipts, snapshot.imports)
    }
    @Test fun `drift forged classification and mismatched reviews are rejected`() {
        val source = source(); val manifest = inspect(source)
        val changed = manifest.copy(revision = "a".repeat(40))
        assertNotEquals(manifest.id, changed.id)
        assertFails { ImportAdmission(changed, admission(manifest).review) }
        val first = manifest.sources.first()
        assertFails { manifest.copy(files = manifest.files + (first.path to "changed source")) }
        val encoded = ImportCodec.manifest(manifest)
        assertFails { ImportCodec.decodeManifest(ObjectValue(encoded.fields + ("evidence_classification" to StringValue("actor-assertion")))) }
        assertFails { ImportCodec.decodeManifest(ObjectValue(encoded.fields + ("source_native_protocol" to StringValue("tasking/core-draft-2")))) }
        val once = LedgerTransitions.evolve(empty(), Transition.ImportRecords(admission(manifest)))
        assertFails { LedgerTransitions.evolve(once, Transition.ImportRecords(admission(manifest))) }
        assertFails { LedgerTransitions.evolve(empty(), Transition.AddRecords(manifest.universe.tasks)) }
        assertFails { once.history!!.validate(once.universe, emptyList()) }
        Files.writeString(source.resolve(".agents/README.md"), "Changed source metadata.\n")
        assertNotEquals(manifest.id, inspect(source).id)
    }
    @Test fun `realization prerequisites lower to causal edges without planning ownership`() {
        val source = source()
        val aggregate = Files.list(source.resolve(".agents/tasks/open")).use { paths -> paths.filter { it.fileName.toString().startsWith("TASK.draft-mvp.038.") }.findFirst().orElseThrow() }
        val child = Files.readString(aggregate).replace("TASK.draft-mvp.038", "TASK.draft-mvp.041")
            .replace("\n---\n\n#", "\nrealizes: TASK.draft-mvp.038\n---\n\n#")
        Files.writeString(aggregate.parent.resolve("TASK.draft-mvp.041.realization.md"), child)
        val manifest = inspect(source)
        assertTrue(TaskId.parseOrThrow("TASK.draft-mvp.041") in manifest.universe.tasks.single { it.id.value == "TASK.draft-mvp.038" }.requires)
        assertEquals(listOf(TaskId.parseOrThrow("TASK.draft-mvp.041")), manifest.universe.frontier())
        assertEquals(41, manifest.universe.roadmaps.single().tasks.size)
    }
    @Test fun `file bootstrap persists imported history and refuses overwrite without source mutation`() {
        val source = source(); val before = inventory(source); val manifest = inspect(source)
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "canonical test launcher\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val target = directory.resolve("target"); Files.createDirectories(target)
        Files.writeString(target.resolve("source.kt"), "Existing product source\n")
        Files.writeString(target.resolve("AGENTS.md"), "Existing contributor contract\n")
        val plan = Bootstrap.plan(target, "fantastikt", "test", distribution.parent, lock, adopt = true, imported = admission(manifest))
        assertFalse(Files.exists(target.resolve(".agents")))
        Bootstrap.apply(plan)
        assertEquals(before, inventory(source))
        assertEquals("Existing product source\n", Files.readString(target.resolve("source.kt")))
        assertEquals("Existing contributor contract\n", Files.readString(target.resolve("AGENTS.md")))
        assertTrue(Files.readString(target.resolve(".agents/config.toml")).contains("taskctl.native/alpha3"))
        val ledger = FileTaskLedger(target); val loaded = ledger.snapshot()
        assertEquals(manifest, loaded.imports.single().manifest)
        assertTrue(loaded.receipts.isEmpty()); assertTrue(ledger.frontier().tasks.isEmpty())
        val currentFiles = inventory(target)
        assertEquals(loaded, FileTaskLedger(target).snapshot()); assertEquals(currentFiles, inventory(target))
        assertFails { Bootstrap.apply(plan) }
        val root = loaded.universe.tasks.single { it.id.value == "TASK.draft-mvp.001" }
        ledger.apply(loaded.revision, Transition.ReconcileTask(review(loaded, root)))
        assertEquals(Currency.CURRENT, FileTaskLedger(target).snapshot().currency().getValue(root.id).state)
        assertTrue(Files.readString(target.resolve(".agents/config.toml")).contains("taskctl.native/alpha3"))
    }
}
