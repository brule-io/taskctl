package io.brule.tasking.conformance

import io.brule.tasking.compatibility.HistoricalImport
import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Archive observations are new bounded work. They are never a retrospective
 * native receipt for the business work described by the source archive. */
class HistoricalAuthorityTest {
    @TempDir lateinit var directory: Path
    private val catalogId = TaskId.parseOrThrow("TASK.authority.catalog")
    private val followupId = TaskId.parseOrThrow("TASK.authority.followup")
    private val time = OccurredAt.parseOrThrow("2026-09-08T04:00:00Z")
    private data class Specimen(val bytes: ByteArray, val source: SourceProvenance) {
        fun preview(): HistoricalRecord = HistoricalImport.preview(bytes, source)
    }
    private fun specimens(): List<Specimen> {
        val text = javaClass.getResourceAsStream("/daemon/sources.json")!!.bufferedReader().use { it.readText() }
        val index = YamlValues.parse(text).value as ObjectValue
        return index.requiredArray("records").map { value ->
            val source = value as ObjectValue
            fun field(key: String) = source.requiredString(key)
            Specimen(javaClass.getResourceAsStream("/daemon/${field("file")}")!!.readAllBytes(),
                SourceProvenance(field("repository"), field("revision"), field("source_path"),
                    field("source_sha256"), field("adapter"), field("adapter_version")))
        }
    }
    private fun lookup(records: List<HistoricalRecord>, repository: String, fullId: String): HistoricalRecord =
        records.single { it.source.repository == repository && it.id == fullId }
    private fun observation(records: List<HistoricalRecord>): DraftRecord {
        val entries = records.sortedWith(compareBy({ it.source.repository }, { it.id }))
        val claims = entries.map { record ->
            "Archive observation: repository=${record.source.repository}; full identity=${record.id}; path=${record.source.path}; " +
                "revision=${record.source.revision}; bytes=${record.source.sha256}; adapter=${record.source.adapter}/${record.source.adapterVersion}; " +
                "historical contract=${record.contractDigest}; evidence=${record.evidenceClass}."
        }
        return DraftRecord(catalogId, "Observe existing historical witnesses", "open",
            "Catalog exactly identified archived assertions without independently proving their business claims.", emptyList(), claims,
            listOf("Exact source witnesses and their unverified classification are retained."), emptyList(),
            obj("specimen.historical-authority/v1" to ArrayValue(entries.map { record ->
                obj("provenance" to record.manifestEntry(), "narrative" to StringValue(record.narrative))
            })), "tasking/core-draft-2", listOf("archive"))
    }
    private fun create(record: DraftRecord = observation(specimens().map { it.preview() })): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "fixture launcher\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val followup = DraftRecord(followupId, "Follow the archive observation", "open", "Use the recorded archive observation.",
            listOf(catalogId), listOf("Use current catalog inputs."), listOf("Observation reviewed."), emptyList(), obj(), "tasking/core-draft-2", listOf("archive"))
        val root = directory.resolve("ledger")
        Bootstrap.apply(Bootstrap.plan(root, "specimen.historical-authority", "test", distribution.parent, lock,
            Transition.AddRecords(listOf(record, followup))))
        Files.writeString(root.resolve("product.txt"), "Unrelated product state\n")
        return FileTaskLedger(root)
    }
    private fun image(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString() to Pair(Files.readString(it), Files.getLastModifiedTime(it))
    } }
    private fun receipt(record: DraftRecord) = ClosureEvidence(Receipt(record.id, DraftLifecycle.contract(record),
        mapOf("archive" to "Observed exact archived source bytes and classification; business claims remain unverified.")), "archive observer", time)
    private fun review(snapshot: LedgerSnapshot, outcome: ReviewOutcome): Reconciliation = Reconciliation(catalogId,
        snapshot.history!!.heads.getValue(catalogId), outcome, emptyList(), "archive reviewer", time,
        "Archived narrative cannot independently establish the strengthened business acceptance criterion.",
        mapOf("archive" to "Exact historical evidence remains available for the earlier observation contract."))

    @Test fun `full historical identity and source authority are independent from a repeated numeric prefix`() {
        val records = specimens().map { it.preview() }
        val siblings = records.filter { it.id.startsWith("TASK.process.006.") }
        assertEquals(2, siblings.size)
        siblings.forEach { assertEquals(it, lookup(records, it.source.repository, it.id)) }
        assertFails { lookup(records, siblings.first().source.repository, "TASK.process.006") }
        assertFails { lookup(records, "", siblings.first().id) }
        assertFails { lookup(records, records.last().source.repository, siblings.first().id) }
        val sources = records.groupBy { it.source.repository }
        assertEquals(2, sources.size)
        assertEquals(listOf(1, 2), sources.values.map { it.size }.sorted())
        val manifests = sources.mapValues { HistoricalImport.manifest(it.value) }
        assertEquals(2, manifests.values.map { it.requiredString("manifest_id") }.distinct().size)
        // A hypothetical different authority cannot satisfy this local observation.
        val changed = records.map { it.copy(source = it.source.copy(repository = "urn:fictional:other-authority")) }
        assertNotEquals(DraftLifecycle.contract(observation(records)), DraftLifecycle.contract(observation(changed)))
    }

    @Test fun `exact archived revisions bytes adapter versions and unverified claims survive the catalog projection`() {
        val specimens = specimens(); val records = specimens.map { it.preview() }
        for ((specimen, record) in specimens.zip(records)) {
            assertEquals(Canonical.sha256(specimen.bytes), record.source.sha256)
            assertEquals("closed", record.state); assertNull(record.nativeProtocol)
            assertEquals("historical-narrative-unverified", record.evidenceClass)
            assertFails { HistoricalImport.preview(specimen.bytes + byteArrayOf(10), specimen.source) }
            assertFails { HistoricalImport.preview(specimen.bytes, specimen.source.copy(revision = "main")) }
            assertFails { HistoricalImport.preview(specimen.bytes, specimen.source.copy(adapterVersion = "native-v1")) }
        }
        assertContains(specimens.last().bytes.toString(Charsets.UTF_8), "- [ ]")
        val ledger = create(observation(records)); val snapshot = ledger.snapshot()
        assertTrue(snapshot.imports.isEmpty()); assertTrue(snapshot.receipts.isEmpty())
        val catalog = ledger.task(catalogId)!!
        assertEquals(observation(records).extensions, catalog.extensions)
        records.forEach { record ->
            assertTrue(catalog.requirements.any { it.contains(record.id) && it.contains(record.source.revision) && it.contains(record.source.sha256) })
            assertNotEquals(record.contractDigest, DraftLifecycle.contract(catalog).value)
        }
        assertEquals(listOf(catalogId), ledger.frontier().tasks)
    }

    @Test fun `historical contract evidence cannot close newly authored native observation work`() {
        val historical = specimens().first().preview(); val ledger = create(); val before = image(ledger.root)
        val task = ledger.task(catalogId)!!
        val oldClaim = receipt(task).copy(receipt = Receipt(task.id, ContractDigest.parseOrThrow(historical.contractDigest), mapOf("archive" to historical.narrative)))
        assertFalse(DraftLifecycle.addresses(oldClaim.receipt, task))
        assertFails { ledger.apply(ledger.snapshot().revision, Transition.CloseTask(oldClaim)) }
        assertEquals(before, image(ledger.root))
        val actual = receipt(task); ledger.apply(ledger.snapshot().revision, Transition.CloseTask(actual))
        val cold = FileTaskLedger(ledger.root).snapshot()
        assertEquals(listOf(actual), cold.receipts); assertEquals("closed", ledger.task(catalogId)!!.state)
        assertEquals(listOf(followupId), cold.frontier().tasks)
        assertEquals(task.extensions, ledger.task(catalogId)!!.extensions)
    }

    @Test fun `later stronger contracts retain historical receipts while unresolved evidence blocks downstream work`() {
        val ledger = create(); val original = ledger.task(catalogId)!!; val proof = receipt(original)
        ledger.apply(ledger.snapshot().revision, Transition.CloseTask(proof))
        val closed = ledger.snapshot(); val strengthened = ledger.task(catalogId)!!.copy(
            requirements = original.requirements + "Independently establish the old business claims against current acceptance.")
        ledger.apply(closed.revision, Transition.ReviseTask(strengthened))
        assertEquals(Currency.AFFECTED, ledger.snapshot().currency().getValue(catalogId).state)
        assertEquals(Currency.AFFECTED, ledger.snapshot().currency().getValue(followupId).state)
        assertTrue(DraftLifecycle.addresses(proof.receipt, original)); assertFalse(DraftLifecycle.addresses(proof.receipt, strengthened))
        val updated = ledger.snapshot()
        ledger.apply(updated.revision, Transition.ReconcileTask(review(updated, ReviewOutcome.UNRESOLVED)))
        val cold = FileTaskLedger(ledger.root).snapshot()
        assertEquals(Currency.UNRESOLVED, cold.currency().getValue(catalogId).state)
        assertEquals(Currency.UNRESOLVED, cold.currency().getValue(followupId).state)
        assertTrue(cold.frontier().tasks.isEmpty()); assertEquals(listOf(proof), cold.receipts)
        closed.history!!.revisions.forEach { (id, revision) -> assertEquals(revision, cold.history!!.revisions[id]) }
        assertEquals(original.extensions, ledger.task(catalogId)!!.extensions)
        assertFails { ledger.apply(cold.revision, Transition.CloseTask(receipt(ledger.task(followupId)!!))) }
    }

    @Test fun `optional authority annotations confer no execution privilege and required unknown capabilities fail closed`() {
        val task = observation(specimens().map { it.preview() })
        val instruction = obj("authority" to StringValue("fictional other repository"), "command" to StringValue("Never execute this archived annotation"))
        val annotated = task.copy(extensions = ObjectValue(task.extensions.fields + ("specimen.instruction/v1" to instruction)))
        assertEquals(DraftLifecycle.contract(task), DraftLifecycle.contract(annotated))
        val ledger = create(annotated); assertEquals(listOf(catalogId), ledger.frontier().tasks)
        val marker = image(ledger.root).getValue("product.txt")
        ledger.apply(ledger.snapshot().revision, Transition.ReviseTask(annotated.copy(requiredExtensions = listOf("specimen.instruction/v1"))))
        val snapshot = ledger.snapshot(); val before = image(ledger.root)
        assertTrue(snapshot.semanticProblems().any { it.contains("specimen.instruction/v1") })
        assertFails { ledger.frontier() }
        assertFails { ledger.apply(snapshot.revision, Transition.CloseTask(receipt(ledger.task(catalogId)!!))) }
        assertEquals(before, image(ledger.root)); assertEquals(marker, image(ledger.root).getValue("product.txt"))
        assertFalse(Files.exists(ledger.root.resolve(".git")))
    }

    @Test fun `cold historical observations and stale review rejection leave unrelated repository state unchanged`() {
        val ledger = create(); val first = ledger.snapshot(); val stale = review(first, ReviewOutcome.UNRESOLVED)
        val task = ledger.task(catalogId)!!
        ledger.apply(first.revision, Transition.ReviseTask(task.copy(title = "Cosmetic archive title")))
        val before = image(directory); val current = ledger.snapshot()
        assertEquals(DraftLifecycle.contract(task), DraftLifecycle.contract(ledger.task(catalogId)!!))
        assertFailsWith<RevisionConflict> { ledger.apply(first.revision, Transition.ReconcileTask(stale)) }
        assertFails { ledger.apply(current.revision, Transition.ReconcileTask(stale)) }
        assertEquals(current, FileTaskLedger(ledger.root).snapshot()); ledger.frontier(); ledger.task(catalogId)
        assertEquals(before, image(directory))
    }
}
