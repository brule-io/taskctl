package io.brule.tasking.core


/** Native planning records are durable indexes, never executable DAG nodes.
 * Neither membership nor presentation order grants execution authority. */
sealed interface PlanningRecord {
    val title: String
    val tasks: List<TaskId>
    val requiredExtensions: List<String>
    val extensions: ObjectValue
}

data class DraftRoadmap(
    val id: RoadmapId,
    override val title: String,
    val intent: String,
    override val tasks: List<TaskId> = emptyList(),
    override val requiredExtensions: List<String> = emptyList(),
    override val extensions: ObjectValue = obj(),
) : PlanningRecord {
    init {
        require(title.isNotBlank() && intent.isNotBlank())
        require(tasks.distinct().size == tasks.size) { "duplicate roadmap membership" }
        PlanningRecordCodec.validateExtensions(requiredExtensions, extensions)
    }
}

data class DraftEpic(
    val id: EpicId,
    override val title: String,
    val scope: String,
    override val tasks: List<TaskId> = emptyList(),
    override val requiredExtensions: List<String> = emptyList(),
    override val extensions: ObjectValue = obj(),
) : PlanningRecord {
    init {
        require(title.isNotBlank() && scope.isNotBlank())
        require(tasks.distinct().size == tasks.size) { "duplicate epic association" }
        PlanningRecordCodec.validateExtensions(requiredExtensions, extensions)
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
    private val commonFields = setOf("protocol", "kind", "id", "title", "tasks", "required_extensions", "extensions")
    private val feature = Regex("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+/v[1-9][0-9]*")

    internal fun validateExtensions(required: List<String>, extensions: ObjectValue) {
        require(required.distinct().size == required.size && required.all { feature.matches(it) })
        require(extensions.fields.keys.all { feature.matches(it) })
    }

    fun decode(root: ObjectValue): PlanningRecord {
        require(root.requiredString("protocol") == PROTOCOL) { "unsupported planning protocol; native v1 is not frozen" }
        val kind = root.requiredString("kind")
        val contentKey = when (kind) { "roadmap" -> "intent"; "epic" -> "scope"; else -> error("unknown planning kind: $kind") }
        require((root.fields.keys - commonFields - contentKey).isEmpty()) { "unknown planning core field" }
        fun texts(key: String): List<String> = if (key !in root.fields) emptyList() else root.requiredArray(key).map {
            (it as? StringValue)?.value ?: error("$key must contain strings")
        }
        val tasks = texts("tasks").map(TaskId::parseOrThrow)
        val required = texts("required_extensions")
        val extensions = (root.fields["extensions"] ?: obj()) as? ObjectValue ?: error("extensions must be an object")
        return when (kind) {
            "roadmap" -> DraftRoadmap(RoadmapId.parseOrThrow(root.requiredString("id")), root.requiredString("title"), root.requiredString(contentKey), tasks, required, extensions)
            "epic" -> DraftEpic(EpicId.parseOrThrow(root.requiredString("id")), root.requiredString("title"), root.requiredString(contentKey), tasks, required, extensions)
            else -> error("unknown planning kind")
        }
    }

    fun encode(record: PlanningRecord): ObjectValue {
        val identity = when (record) {
            is DraftRoadmap -> obj("kind" to StringValue("roadmap"), "id" to StringValue(record.id.value), "intent" to StringValue(record.intent))
            is DraftEpic -> obj("kind" to StringValue("epic"), "id" to StringValue(record.id.value), "scope" to StringValue(record.scope))
        }
        return ObjectValue(identity.fields + obj(
            "protocol" to StringValue(PROTOCOL), "title" to StringValue(record.title),
            "tasks" to strings(record.tasks.map { it.value }),
            "required_extensions" to strings(record.requiredExtensions), "extensions" to record.extensions,
        ).fields)
    }
}
