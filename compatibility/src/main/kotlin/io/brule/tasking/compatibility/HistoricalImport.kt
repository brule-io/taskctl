package io.brule.tasking.compatibility

import io.brule.tasking.core.*

/** Preview adapters for the selected canonical specimens. No migration writer,
 * inferred native schema, retrospective attestation, or remote resolution. */
object HistoricalImport {
    fun preview(bytes: ByteArray, source: SourceProvenance): HistoricalRecord {
        require(source.revision.matches(Regex("[0-9a-f]{40}"))) { "exact source revision required" }
        require(source.repository.isNotBlank() && source.adapterVersion == "0.1.0-dev.1")
        require(Canonical.sha256(bytes) == source.sha256) { "source digest mismatch" }
        require(source.path.startsWith(".agents/tasks/closed/") && source.path.endsWith(".md"))
        val id = source.path.substringAfterLast('/').removeSuffix(".md")
        require(id.startsWith("TASK."))
        val text = bytes.toString(Charsets.UTF_8).replace("\r\n", "\n")
        val contract: String
        val narrative: String
        when (source.adapter) {
            "daemon-workspace-prose/2026" -> {
                val boundary = text.indexOf("\n## Closure\n")
                require(boundary >= 0 && text.contains("\n## Acceptance\n")) { "unexpected workspace dialect" }
                contract = text.substring(0, boundary)
                narrative = text.substring(boundary)
            }
            "daemon-net-triage/2026" -> {
                val closure = text.indexOf("\nCLOSED SUMMARY — ")
                val original = text.indexOf("\n# TASK", text.indexOf("\n# TASK") + 1)
                require(text.startsWith("---\n") && closure >= 0 && original > closure && text.contains("\ndepends_on: []\n")) { "this bounded preview requires the sampled net triage shape with no dependencies" }
                contract = text.substring(original)
                narrative = text.substring(closure, original)
            }
            else -> error("unrecognized historical adapter: ${source.adapter}")
        }
        val digest = Canonical.digest("tasking/historical-contract-preview/1", obj(
            "adapter" to StringValue(source.adapter), "adapter_version" to StringValue(source.adapterVersion), "id" to StringValue(id),
            "contract" to SemanticMarkdown.value(contract, acceptance = true),
        ))
        return HistoricalRecord(id, "closed", digest, "historical-narrative-unverified", narrative, source)
    }
    fun manifest(records: List<HistoricalRecord>): ObjectValue {
        require(records.map { it.id }.distinct().size == records.size) { "duplicate full identity" }
        val entries = ArrayValue(records.sortedBy { it.id }.map { it.manifestEntry() })
        return obj("schema" to StringValue("tasking/import-preview/1"), "records" to entries,
            "manifest_id" to StringValue(Canonical.digest("tasking/import-preview/1", entries)))
    }
}
