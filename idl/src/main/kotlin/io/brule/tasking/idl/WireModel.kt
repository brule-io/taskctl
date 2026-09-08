package io.brule.tasking.idl

import io.brule.tasking.core.*

@JvmInline
value class ShapeName private constructor(val value: String) {
    companion object {
        fun parseOrThrow(value: String): ShapeName {
            require(Regex("[A-Z][A-Za-z0-9]*").matches(value)) { "invalid wire shape name" }
            return ShapeName(value)
        }
    }
}
@JvmInline
value class FieldName private constructor(val value: String) {
    companion object {
        fun parseOrThrow(value: String): FieldName {
            require(Regex("[a-z][a-z0-9_]*").matches(value)) { "invalid wire field name" }
            return FieldName(value)
        }
    }
}
sealed interface Shape { val name: ShapeName }
enum class TextValidation { TEXT, ACCEPTANCE_TIME }
data class TextShape(override val name: ShapeName, val pattern: String? = null, val choices: List<String> = emptyList(), val validation: TextValidation = TextValidation.TEXT) : Shape
data class ListShape(override val name: ShapeName, val member: ShapeName) : Shape
enum class StructValidation { FIELDS, UNIQUE_CHANGED, EVENT_PARENT, EVENT_PAGE }
data class StructShape(override val name: ShapeName, val fields: List<Field>, val validation: StructValidation = StructValidation.FIELDS) : Shape
data class Field(val name: FieldName, val target: ShapeName, val nullable: Boolean = false)
enum class HttpMethod { GET, POST }
data class Operation(val name: ShapeName, val method: HttpMethod, val path: String, val input: ShapeName?, val output: ShapeName, val query: String? = null)

/** This projection owns wire structure only. Core codecs/reducer remain the
 * authority for task, planning, evidence and transition semantics. */
object WireModel {
    const val NAMESPACE = "io.brule.taskctl.kernel.alpha1"
    private fun name(value: String) = ShapeName.parseOrThrow(value)
    private fun field(value: String, type: String, nullable: Boolean = false) = Field(FieldName.parseOrThrow(value), name(type), nullable)
    private fun text(value: String, pattern: String? = null, vararg choices: String, validation: TextValidation = TextValidation.TEXT) = TextShape(name(value), pattern, choices.toList(), validation)
    private fun struct(value: String, vararg fields: Field, validation: StructValidation = StructValidation.FIELDS) = StructShape(name(value), fields.toList(), validation)
    val shapes: List<Shape> = listOf(
        text("Revision", "sha256:[0-9a-f]{64}"), text("EventId", "sha256:[0-9a-f]{64}"),
        text("RecordId", "(TASK|ROADMAP|EPIC)\\.[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*"),
        text("AcceptedAt", "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?(Z|[+-][0-9]{2}:[0-9]{2})", validation = TextValidation.ACCEPTANCE_TIME),
        text("SnapshotProtocol", null, "taskctl.kernel-snapshot/alpha1"),
        text("TransitionProtocol", null, "taskctl.kernel-transition/alpha1"),
        text("ApplyProtocol", null, "taskctl.kernel-apply/alpha1"),
        text("ResultProtocol", null, "taskctl.kernel-result/alpha1"),
        text("EventProtocol", null, "taskctl.kernel-event/alpha1"),
        text("EventPageProtocol", null, "taskctl.kernel-event-page/alpha1"),
        text("ErrorProtocol", null, "taskctl.kernel-error/alpha1"),
        text("TransitionKind", null, "seed", "close", "revise", "reconcile", "track_history", "import", "track_planning", "amend_planning", "planning_disposition", "assess_planning"),
        text("ErrorCode", null, "STALE_REVISION", "VALIDATION", "ABSENT_RECORD", "CONTENT_TYPE", "BODY_LIMIT", "METHOD", "PATH", "STORAGE"),
        text("Message"), ListShape(name("ChangedRecords"), name("RecordId")),
        struct("Snapshot", field("protocol", "SnapshotProtocol"), field("revision", "Revision"), field("state", "ObjectValue")),
        struct("Transition", field("protocol", "TransitionProtocol"), field("kind", "TransitionKind"), field("data", "Value")),
        struct("ApplyRequest", field("protocol", "ApplyProtocol"), field("expected_revision", "Revision"), field("transition", "Transition")),
        struct("Result", field("protocol", "ResultProtocol"), field("revision", "Revision"), field("changed", "ChangedRecords"), field("accepted_at", "AcceptedAt"), validation = StructValidation.UNIQUE_CHANGED),
        struct("Event", field("protocol", "EventProtocol"), field("sequence", "PositiveLong"), field("parent", "EventId", true), field("before", "Revision"), field("transition", "Transition"), field("result", "Result"), validation = StructValidation.EVENT_PARENT),
        struct("EventPage", field("protocol", "EventPageProtocol"), field("event", "Event", true), field("event_id", "EventId", true), validation = StructValidation.EVENT_PAGE),
        struct("KernelError", field("protocol", "ErrorProtocol"), field("code", "ErrorCode"), field("message", "Message")),
    )
    val operations = listOf(
        Operation(name("Snapshot"), HttpMethod.GET, "/kernel/alpha1/snapshot", null, name("Snapshot")),
        Operation(name("Apply"), HttpMethod.POST, "/kernel/alpha1/apply", name("ApplyRequest"), name("Result")),
        Operation(name("Events"), HttpMethod.GET, "/kernel/alpha1/events", null, name("EventPage"), "after"),
    )
    init {
        require(shapes.map { it.name }.distinct().size == shapes.size)
        val known = shapes.map { it.name }.toSet() + listOf("Value", "ObjectValue", "PositiveLong").map(::name)
        shapes.forEach { shape -> when (shape) {
            is TextShape -> { shape.pattern?.let(::Regex); require(shape.choices.distinct().size == shape.choices.size) }
            is ListShape -> require(shape.member in known)
            is StructShape -> { require(shape.fields.map { it.name }.distinct().size == shape.fields.size); require(shape.fields.all { it.target in known }) }
        } }
        require(operations.all { it.output in known && (it.input == null || it.input in known) && it.path.startsWith("/kernel/alpha1/") })
    }
    fun encode(): ObjectValue = obj("protocol" to StringValue("taskctl.wire-idl/alpha1"),
        "domain_authority" to StringValue("taskctl core codecs and LedgerTransitions; this AST is a projection"),
        "value_algebra" to strings(listOf("null", "boolean", "string", "integer", "decimal", "array", "object")),
        "shapes" to ArrayValue(shapes.map { shape -> obj("name" to StringValue(shape.name.value), "definition" to when (shape) {
            is TextShape -> obj("kind" to StringValue("text"), "pattern" to optionalString(shape.pattern), "choices" to strings(shape.choices), "validation" to StringValue(shape.validation.name.lowercase()))
            is ListShape -> obj("kind" to StringValue("list"), "member" to StringValue(shape.member.value))
            is StructShape -> obj("kind" to StringValue("structure"), "validation" to StringValue(shape.validation.name.lowercase()), "fields" to ArrayValue(shape.fields.map {
                obj("name" to StringValue(it.name.value), "target" to StringValue(it.target.value), "nullable" to BooleanValue(it.nullable)) }))
        }) }),
        "operations" to ArrayValue(operations.map { obj("name" to StringValue(it.name.value), "method" to StringValue(it.method.name), "path" to StringValue(it.path),
            "input" to optionalString(it.input?.value), "output" to StringValue(it.output.value), "query" to optionalString(it.query)) }))
}
