package io.brule.tasking.core

import kotlin.test.*

class ValueOwnershipTest {
    @Test fun `object and array values own their contents for empty singleton and larger inputs`() {
        for (size in 0..2) {
            val fields = linkedMapOf<String, Value>(); repeat(size) { fields["key$it"] = StringValue("value$it") }
            val items = fields.values.toMutableList()
            val value = ObjectValue(fields); val array = ArrayValue(items)
            val objectBefore = Json.encode(value); val arrayBefore = Json.encode(array)
            fields["new"] = NullValue; items += NullValue
            assertEquals(objectBefore, Json.encode(value)); assertEquals(arrayBefore, Json.encode(array))
        }
    }
    @Test fun `nested values and keys remain stable after all caller aliases change`() {
        val leaves = mutableListOf<Value>(StringValue("\u0085\u2028\ufffe🦍"), DecimalValue(java.math.BigDecimal("1.2300")), IntegerValue(java.math.BigInteger("9007199254740993123456789")))
        val child = ArrayValue(leaves)
        val fields = linkedMapOf<String, Value>("nested" to child, "other" to NullValue)
        val original = ObjectValue(fields)
        val before = Json.encode(original); val digest = Canonical.digest("ownership", original)
        leaves.clear(); fields.clear(); fields["\uD800"] = NullValue
        assertEquals(before, Json.encode(original)); assertEquals(digest, Canonical.digest("ownership", original))
        assertEquals(original, YamlValues.parse(before).value)
        assertFails { ObjectValue(fields) }
    }
    @Test fun `exposed collection views cannot mutate a nonempty typed value`() {
        val value = ObjectValue(linkedMapOf("first" to NullValue, "second" to StringValue("two")))
        val array = ArrayValue(mutableListOf<Value>(value))
        (value.fields as? MutableMap<*, *>)?.let { assertFailsWith<UnsupportedOperationException> { it.clear() } }
        (array.values as? MutableList<*>)?.let { assertFailsWith<UnsupportedOperationException> { it.clear() } }
        assertEquals(2, value.fields.size); assertEquals(1, array.values.size)
    }
    @Test fun `copy equality hashes display and exact numeric categories retain their prior meaning`() {
        val value = obj("integer" to IntegerValue(java.math.BigInteger.TEN), "decimal" to DecimalValue(java.math.BigDecimal("10.00")))
        assertEquals(value, value.copy()); assertEquals(value.hashCode(), value.fields.hashCode())
        assertEquals("ObjectValue(fields=${value.fields})", value.toString())
        val array = ArrayValue(listOf(value)); assertEquals(array, array.copy()); assertEquals(array.values.hashCode(), array.hashCode())
        assertEquals("ArrayValue(values=${array.values})", array.toString())
        val changed = value.copy(fields = mapOf("changed" to NullValue))
        assertEquals(setOf("integer", "decimal"), value.fields.keys); assertNotEquals(value, changed)
        assertEquals(value, YamlValues.parse(Json.encode(value)).value)
    }
}
