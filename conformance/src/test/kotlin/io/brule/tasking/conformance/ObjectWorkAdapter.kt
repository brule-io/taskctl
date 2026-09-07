package io.brule.tasking.conformance

import io.brule.tasking.core.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Conformance-local adapter, deliberately absent from the shipped CLI registry.
 * It establishes a bounded mapping for independently authored fictional records. */
internal object ObjectWorkAdapter {
    const val ID = "conformance-object-work"
    const val VERSION = "1.0.0"
    const val EXTENSION = "legacy.object-work/v1"
    private val work = Regex("WORK-[0-9]+")
    private val feature = Regex("FEATURE-[A-Z]+")

    private fun ObjectValue.exact(vararg names: String) {
        require(fields.keys == names.toSet()) { "unsupported object-work fields: ${fields.keys}" }
    }
    private fun ObjectValue.objectAt(name: String): ObjectValue =
        fields[name] as? ObjectValue ?: error("$name must be an object")
    private fun Value.objectRecord(): ObjectValue = this as? ObjectValue ?: error("object-work mapping required")
    private fun ObjectValue.texts(name: String): List<String> = requiredArray(name).map {
        (it as? StringValue)?.value?.takeIf(String::isNotBlank) ?: error("$name requires nonblank strings")
    }
    fun taskId(source: String): TaskId {
        require(work.matches(source)) { "invalid full work identity" }
        return TaskId.parseOrThrow("TASK.specimen.$source")
    }
    private fun epicId(source: String): EpicId {
        require(feature.matches(source)) { "invalid feature identity" }
        return EpicId.parseOrThrow("EPIC.specimen.$source")
    }

    fun inspect(root: Path, repository: String, revision: String): ImportManifest {
        fun witnesses() = Files.walk(root).use { paths ->
            paths.sorted().filter { !Files.isDirectory(it, NOFOLLOW_LINKS) }.toList().associate { path ->
                require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, NOFOLLOW_LINKS)) { "unsupported source entry" }
                val relative = root.relativize(path).toString().replace('\\', '/')
                require(relative == "README.md" || relative.matches(Regex("(?:work/WORK-[0-9]+|features/FEATURE-[A-Z]+)\\.json"))) {
                    "unsupported source path: $relative"
                }
                relative to Files.readString(path)
            }
        }
        val files = witnesses()
        require("README.md" in files) { "source dialect description required" }
        val sources = mutableListOf<ImportSource>()
        val members = mutableMapOf<EpicId, MutableList<TaskId>>()
        val tasks = files.filterKeys { it.startsWith("work/") }.map { (path, text) ->
            val record = YamlValues.parse(text).value.objectRecord()
            record.exact("schema", "identity", "lifecycle", "contract", "feature", "labels")
            require(record.requiredString("schema") == "specimen.work/1") { "unsupported source schema" }
            val identity = record.requiredString("identity")
            require(path == "work/$identity.json") { "source identity/path mismatch" }
            val id = taskId(identity)
            val lifecycle = record.objectAt("lifecycle")
            lifecycle.exact("state", "narrative")
            val state = lifecycle.requiredString("state")
            require(state in setOf("open", "closed")) { "unsupported lifecycle requires an execution-preserving mapping: $state" }
            require(if (state == "open") lifecycle.fields["narrative"] == NullValue else
                (lifecycle.fields["narrative"] as? StringValue)?.value?.isNotBlank() == true) { "invalid historical narrative" }
            val contract = record.objectAt("contract")
            contract.exact("title", "intent", "prerequisites", "requirements", "checks")
            val checks = contract.requiredArray("checks").map { value ->
                value.objectRecord().also {
                    it.exact("text", "checked")
                    require(it.fields["checked"] is BooleanValue) { "historical checked flag must be boolean" }
                    require(it.requiredString("text").isNotBlank())
                }
            }
            require(checks.isNotEmpty())
            val epic = epicId(record.requiredString("feature"))
            members.getOrPut(epic) { mutableListOf() }.add(id)
            val labels = record.objectAt("labels")
            sources += ImportSource(id, path, state, Canonical.sha256(text.toByteArray(Charsets.UTF_8)),
                ContractDigest.parseOrThrow(Canonical.digest("specimen.work/1/adapter-contract/1", contract)))
            DraftRecord(id, contract.requiredString("title"), state, contract.requiredString("intent"),
                contract.texts("prerequisites").map(::taskId), contract.texts("requirements"), checks.map { it.requiredString("text") },
                emptyList(), obj(EXTENSION to obj("source_identity" to StringValue(identity), "feature" to StringValue(record.requiredString("feature")),
                    "labels" to labels, "historical_checks" to ArrayValue(checks))),
                protocol = "tasking/core-draft-2", verification = listOf("specimen"))
        }
        require(tasks.isNotEmpty()) { "object-work tasks required" }
        val epics = files.filterKeys { it.startsWith("features/") }.map { (path, text) ->
            val record = YamlValues.parse(text).value.objectRecord()
            record.exact("schema", "identity", "title", "scope")
            require(record.requiredString("schema") == "specimen.work/1")
            val identity = record.requiredString("identity")
            require(path == "features/$identity.json")
            val id = epicId(identity)
            sources += ImportSource(id, path, "open", Canonical.sha256(text.toByteArray(Charsets.UTF_8)), null)
            DraftEpic(id, record.requiredString("title"), record.requiredString("scope"), members[id].orEmpty(),
                extensions = obj(EXTENSION to obj("source_identity" to StringValue(identity))))
        }
        require(members.keys.all { member -> epics.any { it.id == member } }) { "missing feature witness" }
        require(files == witnesses()) { "source changed during inspection" }
        return ImportManifest(repository, revision, ID, VERSION, files, sources.sortedBy { it.id.value }, DraftUniverse(tasks, epics = epics))
    }
}
