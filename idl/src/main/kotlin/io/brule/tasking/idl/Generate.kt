package io.brule.tasking.idl

import io.brule.tasking.core.*
import java.nio.file.Files
import java.nio.file.Path

object PythonProjection {
    private fun literal(value: String) = Json.encode(StringValue(value))
    private val shapes = WireModel.shapes.associateBy { it.name.value }
    private fun type(name: String): String = when (name) {
        "PositiveLong" -> "int"
        else -> when (val shape = shapes[name]) {
            is ListShape -> "tuple[${type(shape.member.value)}, ...]"
            else -> name
        }
    }
    private fun read(name: String, expression: String): String = when (name) {
        "Value" -> expression
        "ObjectValue" -> "object_value($expression)"
        "PositiveLong" -> "positive_long($expression)"
        else -> when (val shape = shapes[name]) {
            is ListShape -> "tuple(${read(shape.member.value, "item")} for item in array_value($expression))"
            else -> "$name.from_value($expression)"
        }
    }
    private fun write(name: String, expression: String): String = when (name) {
        "Value", "ObjectValue" -> expression
        "PositiveLong" -> "IntegerValue($expression)"
        else -> when (val shape = shapes[name]) {
            is ListShape -> "ArrayValue(tuple(${write(shape.member.value, "item")} for item in $expression))"
            else -> "$expression.to_value()"
        }
    }
    fun encode(): String = buildString {
        append("# Generated from taskctl.wire-idl/alpha1. Run :idl:generate; do not edit.\n")
        append("# Model digest: ${Canonical.digest("taskctl.wire-idl/alpha1", WireModel.encode())}\n")
        append("# Smithy projection digest: ${Canonical.digest("taskctl.smithy-projection/alpha1", SmithyProjection.encode())}\n")
        append(requireNotNull(PythonProjection::class.java.getResourceAsStream("/python/runtime.py")).use { it.readBytes().toString(Charsets.UTF_8) })
        append("\n\n")
        WireModel.shapes.forEach { shape -> when (shape) {
            is TextShape -> {
                val name = shape.name.value
                append("@dataclass(frozen=True)\nclass $name:\n    value: str\n    def __post_init__(self):\n        scalar(self.value)\n")
                shape.pattern?.let { append("        if re.fullmatch(${literal(it)}, self.value) is None: raise ValueError('invalid $name')\n") }
                if (shape.validation == TextValidation.ACCEPTANCE_TIME) append("        validate_time(self.value)\n")
                if (shape.choices.isNotEmpty()) append("        if self.value not in (${shape.choices.joinToString(", ") { literal(it) }},): raise ValueError('invalid $name')\n")
                append("    def to_value(self) -> StringValue: return StringValue(self.value)\n    @classmethod\n    def from_value(cls, value: Value) -> $name: return cls(string_value(value))\n\n\n")
            }
            is ListShape -> Unit
            is StructShape -> {
                val name = shape.name.value
                append("@dataclass(frozen=True)\nclass $name:\n")
                shape.fields.forEach { append("    ${it.name.value}: ${type(it.target.value)}${if (it.nullable) " | None" else ""}\n") }
                append("    def __post_init__(self):\n")
                shape.fields.forEach { field ->
                    val property = "self.${field.name.value}"
                    val target = field.target.value
                    val check = when {
                        target == "Value" -> "isinstance($property, VALUE_TYPES)"
                        target == "PositiveLong" -> "type($property) is int and 0 < $property <= 9223372036854775807"
                        shapes[target] is ListShape -> "type($property) is tuple and all(isinstance(item, ${(shapes.getValue(target) as ListShape).member.value}) for item in $property)"
                        else -> "isinstance($property, $target)"
                    }
                    append("        if not (${if (field.nullable) "$property is None or ($check)" else check}): raise ValueError('invalid $name.${field.name.value}')\n")
                }
                when (shape.validation) {
                    StructValidation.FIELDS -> Unit
                    StructValidation.UNIQUE_CHANGED -> append("        if len(set(self.changed)) != len(self.changed): raise ValueError('duplicate changed identity')\n")
                    StructValidation.EVENT_PARENT -> append("        if (self.sequence == 1) != (self.parent is None): raise ValueError('invalid event parent boundary')\n")
                    StructValidation.EVENT_PAGE -> append("        if (self.event is None) != (self.event_id is None): raise ValueError('event page nullability mismatch')\n")
                }
                append("    def to_value(self) -> ObjectValue:\n        return ObjectValue((\n")
                shape.fields.forEach { field ->
                    val property = "self.${field.name.value}"
                    val encoded = write(field.target.value, property)
                    append("            (${literal(field.name.value)}, ${if (field.nullable) "NullValue() if $property is None else $encoded" else encoded}),\n")
                }
                append("        ))\n    @classmethod\n    def from_value(cls, value: Value) -> $name:\n        value=object_value(value)\n        value.exact((${shape.fields.joinToString(", ") { literal(it.name.value) }},))\n        return cls(\n")
                shape.fields.forEach { field ->
                    val expression = "value.get(${literal(field.name.value)})"
                    val decoded = read(field.target.value, expression)
                    append("            ${field.name.value}=${if (field.nullable) "None if isinstance($expression, NullValue) else $decoded" else decoded},\n")
                }
                append("        )\n\n\n")
            }
        } }
        append("class TaskLedgerClient:\n    def __init__(self, endpoint: str): self._transport=_Transport(endpoint)\n")
        WireModel.operations.forEach { operation ->
            val method = operation.name.value.lowercase()
            when {
                operation.input != null -> {
                    append("    def $method(self, expected_revision: Revision, transition: Transition) -> ${operation.output.value}:\n")
                    append("        request=ApplyRequest(ApplyProtocol('taskctl.kernel-apply/alpha1'),expected_revision,transition)\n")
                    append("        return ${operation.output.value}.from_value(self._transport.exchange('${operation.method.name}',${literal(operation.path)},request.to_value()))\n")
                }
                operation.query != null -> {
                    append("    def $method(self, after: int = 0) -> ${operation.output.value}:\n        if type(after) is not int or not 0 <= after <= 9223372036854775807: raise ValueError('nonnegative signed long cursor required')\n")
                    append("        page=${operation.output.value}.from_value(self._transport.exchange('${operation.method.name}',${literal(operation.path + "?" + operation.query + "=")}+str(after)))\n")
                    append("        if (page.event is None) != (page.event_id is None): raise ValueError('event page nullability mismatch')\n        if page.event is not None:\n            if page.event.sequence <= after or digest('taskctl.kernel-event/alpha1',page.event.to_value()) != page.event_id.value: raise ValueError('event page identity mismatch')\n        return page\n")
                }
                else -> {
                    append("    def $method(self) -> ${operation.output.value}:\n        snapshot=${operation.output.value}.from_value(self._transport.exchange('${operation.method.name}',${literal(operation.path)}))\n")
                    append("        if digest('taskctl.kernel-snapshot/alpha1',snapshot.state) != snapshot.revision.value: raise ValueError('snapshot identity mismatch')\n        return snapshot\n")
                }
            }
        }
    }
}

fun main(arguments: Array<String>) {
    require(arguments.size == 1) { "explicit repository output root required" }
    val root = Path.of(arguments.single()).toAbsolutePath().normalize()
    SmithyProjection.validate()
    val output = root.resolve("idl/generated"); Files.createDirectories(output)
    Files.writeString(output.resolve("taskctl-wire.json"), Json.encode(WireModel.encode()) + "\n")
    Files.writeString(output.resolve("taskctl-smithy.json"), Json.encode(SmithyProjection.encode()) + "\n")
    Files.writeString(output.resolve("taskctl_client.py"), PythonProjection.encode())
    println("Validated Smithy model and generated the bounded Python client")
}
