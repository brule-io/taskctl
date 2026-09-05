package io.brule.tasking.core

import java.math.BigDecimal
import java.math.BigInteger

/** Closed algebra for protocol values. Numbers never pass through binary
 * floating point. The document codec independently preserves source spelling. */
sealed interface Value
data class ObjectValue(val fields: Map<String, Value>) : Value {
    fun requiredString(key: String): String =
        (fields[key] as? StringValue)?.value ?: error("$key must be a string")
    fun requiredArray(key: String): List<Value> =
        (fields[key] as? ArrayValue)?.values ?: error("$key must be an array")
}
data class ArrayValue(val values: List<Value>) : Value
data class StringValue(val value: String) : Value
data class IntegerValue(val value: BigInteger) : Value
data class DecimalValue(val value: BigDecimal) : Value
data class BooleanValue(val value: Boolean) : Value
data object NullValue : Value

fun obj(vararg fields: Pair<String, Value>): ObjectValue {
    require(fields.map { it.first }.distinct().size == fields.size) { "duplicate object key" }
    return ObjectValue(fields.toMap())
}
fun strings(values: Iterable<String>): ArrayValue = ArrayValue(values.map(::StringValue))
fun stringMap(values: Map<String, String>): ObjectValue = ObjectValue(values.mapValues { StringValue(it.value) })
fun optionalString(value: String?): Value = value?.let(::StringValue) ?: NullValue
fun integer(value: Int): IntegerValue = IntegerValue(BigInteger.valueOf(value.toLong()))

/** A named serialization boundary is the only place eligible for a reviewed
 * local escape hatch. Public signatures must remain typed. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class UntypedBoundary(val reason: String)
