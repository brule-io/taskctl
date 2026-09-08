package io.brule.tasking.core

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/** Draft framed encoding, not RFC 8785. Maps use JVM ordinal key ordering;
 * arrays retain order and strings carry their UTF-8 byte length. */
object Canonical {
    /** A semantic view for deterministic providers; the original Value stays
     * lossless. Only representation details already ignored by encode change. */
    internal fun semanticValue(value: ObjectValue): ObjectValue = ObjectValue(
        value.fields.keys.sorted().associateWith { semanticValue(value.fields.getValue(it)) })
    internal fun semanticValue(value: Value): Value = when (value) {
        is ObjectValue -> semanticValue(value)
        is ArrayValue -> ArrayValue(value.values.map(::semanticValue))
        is DecimalValue -> DecimalValue(value.value.stripTrailingZeros())
        is StringValue, is IntegerValue, is BooleanValue, NullValue -> value
    }
    fun encode(value: Value): String = when (value) {
        NullValue -> "n"
        is StringValue -> "s${value.value.toByteArray(UTF_8).size}:${value.value}"
        is BooleanValue -> if (value.value) "b1" else "b0"
        is DecimalValue -> "d${encode(StringValue(value.value.stripTrailingZeros().toPlainString()))}"
        is IntegerValue -> "i${encode(StringValue(value.value.toString()))}"
        is ArrayValue -> "l${value.values.size}:" + value.values.joinToString("") { encode(it) }
        is ObjectValue -> "m${value.fields.size}:" + value.fields.keys.sorted().joinToString("") {
            encode(StringValue(it)) + encode(value.fields.getValue(it))
        }
    }
    fun digest(domain: String, value: Value): String =
        "sha256:" + sha256(encode(ArrayValue(listOf(StringValue(domain), value))).toByteArray(UTF_8))
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

object Json {
    fun encode(value: Value): String = when (value) {
        NullValue -> "null"
        is StringValue -> quote(value.value)
        is BooleanValue -> value.value.toString()
        is IntegerValue -> value.value.toString()
        is DecimalValue -> if (value.value.scale() > 0) value.value.toPlainString() else {
            // A bare whole-number token decodes as IntegerValue. Retain the
            // decimal category and BigDecimal scale without changing existing
            // fractional spellings or routing a number through floating point.
            value.value.toString().let { if ('E' in it) it else it + "E+0" }
        }
        is ArrayValue -> value.values.joinToString(",", "[", "]") { encode(it) }
        is ObjectValue -> value.fields.entries.joinToString(",", "{", "}") { quote(it.key) + ":" + encode(it.value) }
    }
    private fun quote(value: String): String {
        requireUnicodeScalars(value)
        return "\"" + value.map { char ->
            when (char) {
                '"' -> "\\\""
                '\\' -> "\\\\"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                // These valid Unicode scalars must be escaped for the shared
                // YAML decoder: line separators affect folding/flow keys, while
                // the C1 controls and BMP noncharacters can be rejected raw.
                else -> if (char.code < 32 || char.code in 0x7f..0x9f || char.code in 0x2028..0x2029 || char.code in 0xfffe..0xffff)
                    "\\u%04x".format(char.code) else char.toString()
            }
        }.joinToString("") + "\""
    }
}
