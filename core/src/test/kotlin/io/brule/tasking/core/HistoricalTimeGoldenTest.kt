package io.brule.tasking.core

import kotlin.test.*

/** Synthetic old-format values pinned against 4233a65 before timestamp changes. */
class HistoricalTimeGoldenTest {
    private fun source(name: String): String = requireNotNull(javaClass.getResourceAsStream("/evidence-time/$name.json"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    private fun value(name: String): ObjectValue = YamlValues.parse(source(name)).value as ObjectValue
    private fun unchanged(name: String, encoded: ObjectValue) {
        assertEquals(source(name), Json.encode(encoded) + "\n")
        assertEquals(value("expected").requiredString(name), Canonical.sha256((Json.encode(encoded) + "\n").toByteArray(Charsets.UTF_8)))
    }
    @Test fun `legacy receipt bytes and storage digest survive arbitrary recorded text`() {
        unchanged("receipt", NativeCodec.evidence(NativeCodec.decodeEvidence(value("receipt"))))
    }
    @Test fun `legacy reconciliation and import review preserve exact historical text`() {
        unchanged("review", HistoryCodec.review(HistoryCodec.decodeReview(value("review"))))
        unchanged("import_review", ImportCodec.review(ImportCodec.decodeReview(value("import_review"))))
    }
    @Test fun `legacy native and imported task revision identities retain their original hash domains`() {
        for (name in listOf("revision", "imported_revision")) {
            val revision = HistoryCodec.decodeRevision(value(name))
            unchanged(name, HistoryCodec.revision(revision))
            assertEquals(value("expected").requiredString("${name}_id"), revision.id.value)
        }
    }
}
