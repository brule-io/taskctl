package io.brule.tasking.core

import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime

/** Actor assertions are not storage clocks. Old strings retain their old meaning. */
sealed interface AssertionTime { val value: String }

@JvmInline
value class LegacyRecordedAt private constructor(override val value: String) : AssertionTime {
    init { require(value.isNotBlank()) { "legacy recorded_at must be nonblank" } }
    override fun toString(): String = value
    companion object {
        fun parse(value: String): LegacyRecordedAt? = if (value.isNotBlank()) LegacyRecordedAt(value) else null
        fun parseOrThrow(value: String): LegacyRecordedAt = parse(value) ?: error("legacy recorded_at must be nonblank")
    }
}

/** occurrence-time/1: explicit known offset, seconds and at most nanosecond precision. */
@JvmInline
value class OccurredAt private constructor(override val value: String) : AssertionTime {
    init { require(validTimestamp(value)) { "invalid occurred_at timestamp" } }
    override fun toString(): String = value
    fun toInstant(): Instant = OffsetDateTime.parse(value).toInstant()
    companion object {
        fun parse(value: String): OccurredAt? = if (validTimestamp(value)) OccurredAt(value) else null
        fun parseOrThrow(value: String): OccurredAt = parse(value) ?: error("invalid occurred_at timestamp: $value")
    }
}

/** Supplied by an accepting storage boundary, never decoded from actor evidence.
 * A valid timestamp is not an authentication mechanism or a revision identity. */
@JvmInline
value class AcceptedAt private constructor(val value: String) {
    init { require(validTimestamp(value)) { "invalid accepted_at timestamp" } }
    override fun toString(): String = value
    fun toInstant(): Instant = OffsetDateTime.parse(value).toInstant()
    companion object {
        fun parse(value: String): AcceptedAt? = if (validTimestamp(value)) AcceptedAt(value) else null
        fun parseOrThrow(value: String): AcceptedAt = parse(value) ?: error("invalid accepted_at timestamp: $value")
        fun fromInstant(value: Instant): AcceptedAt = parseOrThrow(value.toString())
    }
}

private val timestampPattern = Regex("""[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,9})?(Z|[+-][0-9]{2}:[0-9]{2})""")
private fun validTimestamp(value: String): Boolean {
    if (value.length !in 20..35 || !timestampPattern.matches(value) || value.startsWith("0000-") || value.endsWith("-00:00")) return false
    return try { OffsetDateTime.parse(value); true } catch (_: DateTimeException) { false }
}

/** Version selection is explicit. No new envelope may silently fall back to legacy. */
internal enum class AssertionTimeVersion(val field: String) {
    LEGACY("recorded_at"), OCCURRENCE("occurred_at");

    fun decode(value: ObjectValue): AssertionTime = when (this) {
        LEGACY -> LegacyRecordedAt.parseOrThrow(value.requiredString(field))
        OCCURRENCE -> OccurredAt.parseOrThrow(value.requiredString(field))
    }
    fun protocol(legacy: String, current: String): String = when (this) { LEGACY -> legacy; OCCURRENCE -> current }
    companion object {
        fun select(protocol: String, legacy: String, current: String): AssertionTimeVersion = when (protocol) {
            legacy -> LEGACY
            current -> OCCURRENCE
            else -> error("unsupported assertion contract: $protocol")
        }
    }
}
internal val AssertionTime.version: AssertionTimeVersion get() = when (this) {
    is LegacyRecordedAt -> AssertionTimeVersion.LEGACY
    is OccurredAt -> AssertionTimeVersion.OCCURRENCE
}
