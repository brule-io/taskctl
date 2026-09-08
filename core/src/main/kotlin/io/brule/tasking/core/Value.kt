package io.brule.tasking.core

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Collections

/** Closed algebra for protocol values. Numbers never pass through binary
 * floating point. The document codec independently preserves source spelling. */
sealed interface Value
private data class ObjectFields(val fields: Map<String, Value>)
private data class ArrayElements(val values: List<Value>)

/** Own the collection at the validity boundary. A Kotlin read-only view alone
 * would still let a caller change the value through its original mutable alias. */
@ConsistentCopyVisibility
data class ObjectValue private constructor(private val content: ObjectFields) : Value {
    constructor(fields: Map<String, Value>) : this(ObjectFields(Collections.unmodifiableMap(LinkedHashMap(fields))))
    val fields: Map<String, Value> get() = content.fields
    init { fields.keys.forEach(::requireUnicodeScalars) }
    fun copy(fields: Map<String, Value> = this.fields): ObjectValue = ObjectValue(fields)
    override fun toString(): String = "ObjectValue(fields=$fields)"
    fun requiredString(key: String): String =
        (fields[key] as? StringValue)?.value ?: error("$key must be a string")
    fun requiredArray(key: String): List<Value> =
        (fields[key] as? ArrayValue)?.values ?: error("$key must be an array")
}
@ConsistentCopyVisibility
data class ArrayValue private constructor(private val content: ArrayElements) : Value {
    constructor(values: List<Value>) : this(ArrayElements(Collections.unmodifiableList(ArrayList(values))))
    val values: List<Value> get() = content.values
    fun copy(values: List<Value> = this.values): ArrayValue = ArrayValue(values)
    override fun toString(): String = "ArrayValue(values=$values)"
}
data class StringValue(val value: String) : Value {
    init { requireUnicodeScalars(value) }
}
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
