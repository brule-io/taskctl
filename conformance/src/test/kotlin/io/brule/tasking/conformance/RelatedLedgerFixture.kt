package io.brule.tasking.conformance

import io.brule.tasking.core.*

/** Explicit, test-local dialect; no remote lookup or consumer policy interpretation. */
internal object RelatedLedgerFixture {
    const val REVISION = "da0bd033a3cce21af5eeebeb78d70bbf069d4f16"
    private fun text(name: String) = requireNotNull(javaClass.getResourceAsStream("/related-ledger/$name")).use { it.readBytes().toString(Charsets.UTF_8) }
    fun id(name: String): TaskId {
        require(Regex("W[1-9][0-9]*").matches(name)) { "invalid specimen task identity" }
        return TaskId.parseOrThrow("TASK.related.$name")
    }
    private fun objectValue(value: Value) = value as? ObjectValue ?: error("specimen object required")
    private fun ObjectValue.texts(key: String) = requiredArray(key).map { (it as? StringValue)?.value ?: error("string list required") }
    fun files(ledger: String): Map<String, String> {
        require(ledger in setOf("alpha", "beta"))
        val index = objectValue(YamlValues.parse(text("sources.json")).value)
        require(index.requiredString("revision") == REVISION && index.requiredString("repository") == "https://github.com/brule-io/taskctl")
        require(index.requiredString("classification") == "independently-authored-fictional-related-ledgers")
        return objectValue(index.fields.getValue("files")).fields.filterKeys { it.startsWith("$ledger/") }.mapValues { (path, expected) ->
            text("source/$path").also { require(Canonical.sha256(it.toByteArray(Charsets.UTF_8)) == (expected as? StringValue)?.value) }
        }
    }
    fun manifest(ledger: String, files: Map<String, String> = files(ledger)): ImportManifest {
        require(ledger in setOf("alpha", "beta"))
        val tasks = mutableListOf<DraftRecord>(); val sources = mutableListOf<ImportSource>()
        require(files.keys == setOf("$ledger/README.md", "$ledger/W1.json", "$ledger/W2.json", "$ledger/W3.json"))
        files.filterKeys { it.endsWith(".json") }.toSortedMap().forEach { (path, text) ->
            val value = objectValue(YamlValues.parse(text).value)
            require(value.fields.keys == setOf("dialect", "ledger", "id", "state", "after", "intent", "requirements", "checks")) { "unknown source semantics require a preserving adapter" }
            require(value.requiredString("dialect") == "specimen.related/1" && value.requiredString("ledger") == ledger)
            val id = id(value.requiredString("id"))
            require(path == "$ledger/${value.requiredString("id")}.json")
            val state = when (value.requiredString("state")) { "done" -> "closed"; "open" -> "open"; else -> error("unsupported stateful source") }
            val checks = value.requiredArray("checks").map(::objectValue).map {
                require(it.fields.keys == setOf("checked", "text") && it.fields["checked"] is BooleanValue)
                it.requiredString("text")
            }
            tasks += DraftRecord(id, "Legacy ${value.requiredString("id")}", state, value.requiredString("intent"), value.texts("after").map(::id),
                value.texts("requirements"), checks, emptyList(), obj("specimen.related/v1" to obj("ledger" to StringValue(ledger), "source" to StringValue(path))),
                "tasking/core-draft-2", listOf("observation"))
            sources += ImportSource(id, path, state, Canonical.sha256(text.toByteArray(Charsets.UTF_8)),
                ContractDigest.parseOrThrow(Canonical.digest("specimen.related/1/contract", value)))
        }
        return ImportManifest("https://github.com/brule-io/taskctl", REVISION, "conformance-related-ledger", "1.0.0", files, sources, DraftUniverse(tasks))
    }
    fun admission(ledger: String) = manifest(ledger).let { ImportAdmission(it, ImportReview(it.id, "related-ledger reviewer",
        OccurredAt.parseOrThrow("2026-09-08T03:00:00Z"), "Reviewed explicit fictional mapping; historical done flags remain unverified assertions.")) }
}
