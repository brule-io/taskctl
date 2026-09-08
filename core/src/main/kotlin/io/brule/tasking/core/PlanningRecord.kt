package io.brule.tasking.core


/** Native planning records are durable indexes, never executable DAG nodes.
 * Neither membership nor presentation order grants execution authority. */
sealed interface PlanningRecord {
    val id: PlanningId
    val title: String
    val tasks: List<TaskId>
    val requiredExtensions: List<String>
    val extensions: ObjectValue
    val protocol: String
    val disposition: PlanningDisposition
    val acceptance: List<String>
}

enum class PlanningDisposition { ACTIVE, ARCHIVED }

data class DraftRoadmap(
    override val id: RoadmapId,
    override val title: String,
    val intent: String,
    override val tasks: List<TaskId> = emptyList(),
    override val requiredExtensions: List<String> = emptyList(),
    override val extensions: ObjectValue = obj(),
    override val protocol: String = PlanningRecordCodec.PROTOCOL,
    override val disposition: PlanningDisposition = PlanningDisposition.ACTIVE,
    override val acceptance: List<String> = emptyList(),
) : PlanningRecord {
    init {
        require(title.isNotBlank() && intent.isNotBlank())
        require(tasks.distinct().size == tasks.size) { "duplicate roadmap membership" }
        PlanningRecordCodec.validateExtensions(requiredExtensions, extensions)
        PlanningRecordCodec.validateVersion(protocol, disposition, acceptance)
    }
}

data class DraftEpic(
    override val id: EpicId,
    override val title: String,
    val scope: String,
    override val tasks: List<TaskId> = emptyList(),
    override val requiredExtensions: List<String> = emptyList(),
    override val extensions: ObjectValue = obj(),
    override val protocol: String = PlanningRecordCodec.PROTOCOL,
    override val disposition: PlanningDisposition = PlanningDisposition.ACTIVE,
    override val acceptance: List<String> = emptyList(),
) : PlanningRecord {
    init {
        require(title.isNotBlank() && scope.isNotBlank())
        require(tasks.distinct().size == tasks.size) { "duplicate epic association" }
        PlanningRecordCodec.validateExtensions(requiredExtensions, extensions)
        PlanningRecordCodec.validateVersion(protocol, disposition, acceptance)
    }
}

/** Separate draft envelope: adding planning records does not reinterpret any
 * tasking/core-draft-1 task contract or the historical compatibility dialect. */
class PlanningDocument private constructor(
    val record: PlanningRecord, val source: String, private val titleSpan: SourceSpan,
) {
    fun render(): String = source
    fun withTitle(title: String): PlanningDocument {
        require(title.isNotBlank())
        return parse(source.replaceRange(titleSpan.start, titleSpan.end, Json.encode(StringValue(title))))
    }
    companion object {
        fun parse(source: String): PlanningDocument {
            val decoded = YamlValues.parse(source)
            val root = decoded.value as? ObjectValue ?: error("planning record must be an object")
            return PlanningDocument(PlanningRecordCodec.decode(root), source, decoded.rootFields.getValue("title"))
        }
    }
}

object PlanningRecordCodec {
    const val PROTOCOL = "tasking/planning-draft-1"
    const val AUDITED_PROTOCOL = "tasking/planning-draft-2"
    private val commonFields = setOf("protocol", "kind", "id", "title", "tasks", "required_extensions", "extensions")

    internal fun validateExtensions(required: List<String>, extensions: ObjectValue) {
        validateExtensionAttributes(required, extensions)
    }

    internal fun validateVersion(protocol: String, disposition: PlanningDisposition, acceptance: List<String>) {
        require(protocol in setOf(PROTOCOL, AUDITED_PROTOCOL)) { "unsupported planning protocol; native v1 is not frozen" }
        require(acceptance.all { it.isNotBlank() }) { "planning acceptance criteria must be nonblank" }
        require(protocol != PROTOCOL || (disposition == PlanningDisposition.ACTIVE && acceptance.isEmpty())) {
            "legacy planning records have no archival or acceptance contract"
        }
    }

    fun decode(root: ObjectValue): PlanningRecord {
        val protocol = root.requiredString("protocol")
        require(protocol in setOf(PROTOCOL, AUDITED_PROTOCOL)) { "unsupported planning protocol; native v1 is not frozen" }
        val kind = root.requiredString("kind")
        val contentKey = when (kind) { "roadmap" -> "intent"; "epic" -> "scope"; else -> error("unknown planning kind: $kind") }
        val extra = if (protocol == AUDITED_PROTOCOL) setOf("disposition", "acceptance") else emptySet()
        require((root.fields.keys - commonFields - contentKey - extra).isEmpty()) { "unknown planning core field" }
        fun texts(key: String): List<String> = if (key !in root.fields) emptyList() else root.requiredArray(key).map {
            (it as? StringValue)?.value ?: error("$key must contain strings")
        }
        val tasks = texts("tasks").map(TaskId::parseOrThrow)
        val required = texts("required_extensions")
        val extensions = (root.fields["extensions"] ?: obj()) as? ObjectValue ?: error("extensions must be an object")
        val disposition = if (protocol == PROTOCOL) PlanningDisposition.ACTIVE else
            PlanningDisposition.entries.singleOrNull { it.name.lowercase() == root.requiredString("disposition") } ?: error("unknown planning disposition")
        if (protocol == AUDITED_PROTOCOL) require("acceptance" in root.fields) { "new planning records require explicit acceptance criteria (which may be empty)" }
        val acceptance = texts("acceptance")
        return when (kind) {
            "roadmap" -> DraftRoadmap(RoadmapId.parseOrThrow(root.requiredString("id")), root.requiredString("title"), root.requiredString(contentKey), tasks, required, extensions, protocol, disposition, acceptance)
            "epic" -> DraftEpic(EpicId.parseOrThrow(root.requiredString("id")), root.requiredString("title"), root.requiredString(contentKey), tasks, required, extensions, protocol, disposition, acceptance)
            else -> error("unknown planning kind")
        }
    }

    fun encode(record: PlanningRecord): ObjectValue {
        val identity = when (record) {
            is DraftRoadmap -> obj("kind" to StringValue("roadmap"), "id" to StringValue(record.id.value), "intent" to StringValue(record.intent))
            is DraftEpic -> obj("kind" to StringValue("epic"), "id" to StringValue(record.id.value), "scope" to StringValue(record.scope))
        }
        return ObjectValue(identity.fields + obj(
            "protocol" to StringValue(record.protocol), "title" to StringValue(record.title),
            "tasks" to strings(record.tasks.map { it.value }),
            "required_extensions" to strings(record.requiredExtensions), "extensions" to record.extensions,
        ).fields).let { if (record.protocol == PROTOCOL) it else ObjectValue(it.fields + mapOf(
            "disposition" to StringValue(record.disposition.name.lowercase()), "acceptance" to strings(record.acceptance))) }
    }
}

fun PlanningRecord.withDisposition(value: PlanningDisposition): PlanningRecord = when (this) {
    is DraftRoadmap -> copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, disposition = value)
    is DraftEpic -> copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, disposition = value)
}
