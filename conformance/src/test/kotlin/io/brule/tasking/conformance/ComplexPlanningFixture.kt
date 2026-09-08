package io.brule.tasking.conformance

import io.brule.tasking.core.*

/** Test-local adapter for an explicitly fictional, non-native source dialect. */
internal object ComplexPlanningFixture {
    const val REVISION = "445b637b02c4451977e015af5d6301132f8db75c"
    const val ADAPTER = "conformance-planning-indexes"
    const val VERSION = "1.0.0"
    private fun text(resource: String): String = requireNotNull(javaClass.getResourceAsStream("/complex-planning/$resource")).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun objectValue(value: Value): ObjectValue = value as? ObjectValue ?: error("fixture object required")
    fun files(): Map<String, String> {
        val index = objectValue(YamlValues.parse(text("sources.json")).value)
        require(index.requiredString("source_repository") == "https://github.com/brule-io/taskctl" && index.requiredString("source_revision") == REVISION)
        require(index.requiredString("classification") == "independently-authored-fictional-legacy-specimen")
        require(index.requiredString("adapter") == ADAPTER && index.requiredString("adapter_version") == VERSION)
        return index.requiredArray("files").map(::objectValue).associate { item ->
            val content = text(item.requiredString("resource"))
            require(Canonical.sha256(content.toByteArray(Charsets.UTF_8)) == item.requiredString("sha256")) { "source witness bytes changed" }
            item.requiredString("path") to content
        }
    }
    fun taskId(source: String): TaskId {
        require(Regex("W-[0-9]+").matches(source)) { "invalid specimen work identity" }
        return TaskId.parseOrThrow("TASK.specimen.$source")
    }
    private fun ObjectValue.texts(name: String): List<String> = requiredArray(name).map { (it as? StringValue)?.value ?: error("source string list required") }
    private fun ObjectValue.exact(vararg names: String) { require(fields.keys == names.toSet()) { "unknown or missing specimen fields; no implicit capability mapping" } }
    fun inspect(files: Map<String, String> = files()): ImportManifest {
        val tasks = mutableListOf<DraftRecord>(); val roadmaps = mutableListOf<DraftRoadmap>(); val epics = mutableListOf<DraftEpic>()
        val sources = mutableListOf<ImportSource>()
        files.filterKeys { it.endsWith(".json") }.toSortedMap().forEach { (path, source) ->
            val value = objectValue(YamlValues.parse(source).value)
            require(value.requiredString("dialect") == "specimen.planning/1") { "explicit specimen dialect required" }
            val kind = value.requiredString("kind"); val oldId = value.requiredString("id")
            val state = when (value.requiredString("status")) { "open" -> "open"; "done" -> "closed"; else -> error("stateful legacy status requires a preserving capability mapping") }
            val notes = objectValue(value.fields.getValue("annotations"))
            val extensions = obj("specimen.origin/v1" to obj("dialect" to StringValue("specimen.planning/1"), "kind" to StringValue(kind),
                "identity" to StringValue(oldId), "historical_status" to StringValue(value.requiredString("status"))), "specimen.notes/v1" to notes)
            val id: RecordId
            val contract: ContractDigest?
            when (kind) {
                "work" -> {
                    value.exact("dialect", "kind", "id", "status", "after", "intent", "requirements", "checks", "annotations")
                    val checks = value.requiredArray("checks").map(::objectValue).map { check ->
                        check.exact("done", "text"); require(check.fields["done"] is BooleanValue) { "source checkbox must be boolean" }; check.requiredString("text")
                    }
                    id = taskId(oldId)
                    tasks += DraftRecord(id, "Legacy $oldId", state, value.requiredString("intent"), value.texts("after").map(::taskId),
                        value.texts("requirements"), checks, emptyList(), extensions, "tasking/core-draft-2", listOf("mapping"))
                    contract = ContractDigest.parseOrThrow(Canonical.digest("specimen.planning/1/work-contract", value))
                }
                "lane", "capability" -> {
                    value.exact("dialect", "kind", "id", "status", "members", "scope", "annotations")
                    require(Regex("[a-z][a-z0-9-]*").matches(oldId)) { "invalid specimen planning identity" }
                    val members = value.texts("members").map(::taskId)
                    if (kind == "lane") {
                        id = RoadmapId.parseOrThrow("ROADMAP.specimen.$oldId")
                        roadmaps += DraftRoadmap(id, "Legacy $oldId", value.requiredString("scope"), members, extensions = extensions)
                    } else {
                        id = EpicId.parseOrThrow("EPIC.specimen.$oldId")
                        epics += DraftEpic(id, "Legacy $oldId", value.requiredString("scope"), members, extensions = extensions)
                    }
                    contract = null // A narrative planning status is not a native task contract.
                }
                else -> error("unknown specimen record kind")
            }
            sources += ImportSource(id, path, state, Canonical.sha256(source.toByteArray(Charsets.UTF_8)), contract)
        }
        return ImportManifest("https://github.com/brule-io/taskctl", REVISION, ADAPTER, VERSION, files, sources, DraftUniverse(tasks, roadmaps, epics))
    }
    fun admission(manifest: ImportManifest = inspect()) = ImportAdmission(manifest,
        ImportReview(manifest.id, "conformance reviewer", OccurredAt.parseOrThrow("2026-09-08T00:00:00Z"), "Explicit fictional mapping review; source assertions remain unverified history."))
}
