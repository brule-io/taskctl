package io.brule.tasking.conformance

import io.brule.tasking.core.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class HistoricalConformanceTest {
    private fun specimens(): List<Pair<ByteArray, SourceProvenance>> {
        val text = javaClass.getResourceAsStream("/daemon/sources.json")!!.bufferedReader().use { it.readText() }
        val index = YamlValues.parse(text).value as? ObjectValue ?: error("fixture manifest must be an object")
        return index.requiredArray("records").map { value ->
            val source = value as? ObjectValue ?: error("source entry must be an object")
            val bytes = javaClass.getResourceAsStream("/daemon/${source.requiredString("file")}")!!.readAllBytes()
            fun field(key: String) = source.requiredString(key)
            bytes to SourceProvenance(field("repository"), field("revision"), field("source_path"),
                field("source_sha256"), field("adapter"), field("adapter_version"))
        }
    }

    @Test fun `DAEMON complete filename identity retains duplicate numeric prefixes`() {
        val records = specimens().map { (bytes, source) -> HistoricalImport.preview(bytes, source) }
        val siblings = records.filter { it.id.startsWith("TASK.process.006.") }
        assertEquals(2, siblings.size)
        assertEquals(2, siblings.map { it.id }.distinct().size)
        assertEquals(2, siblings.map { it.contractDigest }.distinct().size)
        val manifest = HistoricalImport.manifest(records)
        assertEquals(manifest, HistoricalImport.manifest(records.reversed()))
        val output = Path.of("build/proof/daemon-import-preview.json")
        Files.createDirectories(output.parent)
        Files.writeString(output, Json.encode(manifest) + "\n")
    }

    @Test fun `unchecked historical acceptance does not erase closure or create native evidence`() {
        val (bytes, source) = specimens().last()
        assertContains(bytes.toString(Charsets.UTF_8), "- [ ]")
        val record = HistoricalImport.preview(bytes, source)
        assertEquals("closed", record.state)
        assertEquals("historical-narrative-unverified", record.evidenceClass)
        assertNull(record.nativeProtocol)
        assertContains(record.narrative, "CLOSED SUMMARY")
        assertEquals(source, record.source)
        assertFails { HistoricalImport.preview(bytes + "changed".toByteArray(), source) }
        assertFails { HistoricalImport.preview(bytes, source.copy(revision = "development")) }
        assertFails { HistoricalImport.preview(bytes, source.copy(adapter = "brule.task/v1")) }
    }

    @Test fun `conversion provenance changes manifest identity without altering semantic contract`() {
        val (bytes, source) = specimens().first()
        val old = HistoricalImport.preview(bytes, source)
        val later = HistoricalImport.preview(bytes, source.copy(revision = "a".repeat(40)))
        assertEquals(old.contractDigest, later.contractDigest)
        assertNotEquals(HistoricalImport.manifest(listOf(old)), HistoricalImport.manifest(listOf(later)))
        assertFails { HistoricalImport.manifest(listOf(old, old)) }
    }
}
