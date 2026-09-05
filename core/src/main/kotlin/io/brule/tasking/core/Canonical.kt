package io.brule.tasking.core

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/** Draft framed encoding, not RFC 8785. Maps use JVM ordinal key ordering;
 * arrays retain order and strings carry their UTF-8 byte length. */
object Canonical {
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
        is DecimalValue -> value.value.toPlainString()
        is ArrayValue -> value.values.joinToString(",", "[", "]") { encode(it) }
        is ObjectValue -> value.fields.entries.joinToString(",", "{", "}") { quote(it.key) + ":" + encode(it.value) }
    }
    private fun quote(value: String): String = "\"" + value.map { char ->
        when (char) {
            '"' -> "\\\""
            '\\' -> "\\\\"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> if (char.code < 32) "\\u%04x".format(char.code) else char.toString()
        }
    }.joinToString("") + "\""
}
