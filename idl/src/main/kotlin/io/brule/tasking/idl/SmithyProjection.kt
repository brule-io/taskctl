package io.brule.tasking.idl

import io.brule.tasking.core.*
import software.amazon.smithy.model.Model

object SmithyProjection {
    private val namespace = WireModel.NAMESPACE
    private fun id(name: String) = "$namespace#$name"
    private fun ref(name: String) = obj("target" to StringValue(id(name)))
    private fun simple(type: String) = obj("type" to StringValue(type))
    private fun traits(vararg values: Pair<String, Value>) = obj(*values)
    fun encode(): ObjectValue {
        val shapes = linkedMapOf<String, Value>()
        shapes[id("kernelJson")] = obj("type" to StringValue("structure"), "members" to obj(), "traits" to traits(
            "smithy.api#trait" to obj("selector" to StringValue("service")),
            "smithy.api#protocolDefinition" to obj(),
            "smithy.api#documentation" to StringValue("Experimental exact taskctl typed JSON; untagged Value union, explicit nullable members, exact numeric category and scale. Not AWS restJson1 or RPC v2.")))
        shapes[id("explicitNull")] = obj("type" to StringValue("structure"), "members" to obj(), "traits" to traits(
            "smithy.api#trait" to obj("selector" to StringValue("member")),
            "smithy.api#documentation" to StringValue("This custom protocol requires the member to be present; null is an explicit permitted value.")))
        shapes[id("wireValidation")] = obj("type" to StringValue("string"), "traits" to traits(
            "smithy.api#trait" to obj("selector" to StringValue("*")),
            "smithy.api#documentation" to StringValue("Named validation rule from the authoritative taskctl wire AST; consumed by this custom projection, not a stock protocol serializer.")))
        shapes[id("StringValue")] = simple("string")
        shapes[id("BooleanValue")] = simple("boolean")
        shapes[id("IntegerValue")] = simple("bigInteger")
        shapes[id("DecimalValue")] = simple("bigDecimal")
        shapes[id("NullValue")] = obj("type" to StringValue("structure"), "members" to obj())
        shapes[id("ObjectValue")] = obj("type" to StringValue("map"), "key" to ref("StringValue"), "value" to ref("Value"))
        shapes[id("ArrayValue")] = obj("type" to StringValue("list"), "member" to ref("Value"))
        shapes[id("Value")] = obj("type" to StringValue("union"), "members" to ObjectValue(listOf("Null", "Boolean", "String", "Integer", "Decimal", "Array", "Object")
            .associate { it.lowercase() to ref(it + "Value") }), "traits" to traits("smithy.api#documentation" to StringValue("The kernelJson protocol maps this algebra to untagged JSON values without binary floats or loss of decimal type/scale.")))
        shapes[id("PositiveLong")] = obj("type" to StringValue("long"), "traits" to traits("smithy.api#range" to obj("min" to integer(1))))
        shapes[id("Cursor")] = obj("type" to StringValue("long"), "traits" to traits("smithy.api#range" to obj("min" to integer(0))))
        WireModel.shapes.forEach { shape -> shapes[id(shape.name.value)] = when (shape) {
            is TextShape -> {
                val details = linkedMapOf<String, Value>()
                shape.pattern?.let { details["smithy.api#pattern"] = StringValue("^(?:$it)$") }
                if (shape.validation == TextValidation.ACCEPTANCE_TIME) {
                    details["smithy.api#documentation"] = StringValue("Core acceptance-time/1: real Gregorian date, known offset within 18 hours, seconds, up to nine fractional digits; retain the exact string.")
                    details[id("wireValidation")] = StringValue(shape.validation.name.lowercase())
                }
                if (shape.choices.isNotEmpty()) details["smithy.api#enum"] = ArrayValue(shape.choices.map { obj("value" to StringValue(it)) })
                obj("type" to StringValue("string"), "traits" to ObjectValue(details))
            }
            is ListShape -> obj("type" to StringValue("list"), "member" to ref(shape.member.value))
            is StructShape -> obj("type" to StringValue("structure"), "traits" to traits(id("wireValidation") to StringValue(shape.validation.name.lowercase())), "members" to ObjectValue(shape.fields.associate { field ->
                field.name.value to ObjectValue(ref(field.target.value).fields + ("traits" to if (field.nullable) traits(id("explicitNull") to obj()) else traits("smithy.api#required" to obj())))
            }))
        } }
        val errors = listOf(400, 404, 405, 409, 413, 415, 500).map { status ->
            val error = id("HttpError$status")
            val fields = (shapes.getValue(id("KernelError")) as ObjectValue).fields
            shapes[error] = ObjectValue(fields + ("traits" to traits("smithy.api#error" to StringValue(if (status == 500) "server" else "client"), "smithy.api#httpError" to integer(status))))
            obj("target" to StringValue(error))
        }
        WireModel.operations.forEach { operation ->
            val fields = linkedMapOf<String, Value>("type" to StringValue("operation"), "output" to ref(operation.output.value),
                "errors" to ArrayValue(errors), "traits" to traits("smithy.api#http" to obj("method" to StringValue(operation.method.name), "uri" to StringValue(operation.path), "code" to integer(200))))
            operation.input?.let { fields["input"] = ref(it.value) }
            operation.query?.let { query ->
                shapes[id(operation.name.value + "Input")] = obj("type" to StringValue("structure"), "members" to obj(query to obj("target" to StringValue(id("Cursor")), "traits" to traits("smithy.api#required" to obj(), "smithy.api#httpQuery" to StringValue(query)))))
                fields["input"] = ref(operation.name.value + "Input")
            }
            shapes[id(operation.name.value + "Operation")] = ObjectValue(fields)
        }
        shapes[id("TaskLedger")] = obj("type" to StringValue("service"), "version" to StringValue("kernel-alpha1"), "operations" to ArrayValue(WireModel.operations.map { ref(it.name.value + "Operation") }),
            "traits" to traits(id("kernelJson") to obj()))
        return obj("smithy" to StringValue("2.0"), "metadata" to obj("taskctlProjection" to StringValue("taskctl.wire-idl/alpha1"),
            "taskctlIdlDigest" to StringValue(Canonical.digest("taskctl.wire-idl/alpha1", WireModel.encode()))), "shapes" to ObjectValue(shapes))
    }
    fun validate(): Model = Model.assembler().addUnparsedModel("taskctl-kernel.json", Json.encode(encode())).assemble().unwrap()
}
