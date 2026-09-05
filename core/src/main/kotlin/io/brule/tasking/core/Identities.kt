package io.brule.tasking.core

sealed interface RecordId { val value: String }

private fun <T> parseIdentity(value: String, pattern: Regex, construct: (String) -> T): T? =
    if (pattern.matches(value)) construct(value) else null

@JvmInline
value class TaskId private constructor(override val value: String) : RecordId, Comparable<TaskId> {
    init { require(PATTERN.matches(value)) { "Invalid TaskId: $value" } }
    override fun compareTo(other: TaskId): Int = value.compareTo(other.value)
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("TASK\\.[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*")
        fun parse(value: String): TaskId? = parseIdentity(value, PATTERN, ::TaskId)
        fun parseOrThrow(value: String): TaskId = parse(value) ?: error("Invalid TaskId: $value")
    }
}

@JvmInline
value class RoadmapId private constructor(override val value: String) : RecordId, Comparable<RoadmapId> {
    init { require(PATTERN.matches(value)) { "Invalid RoadmapId: $value" } }
    override fun compareTo(other: RoadmapId): Int = value.compareTo(other.value)
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("ROADMAP\\.[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*")
        fun parse(value: String): RoadmapId? = parseIdentity(value, PATTERN, ::RoadmapId)
        fun parseOrThrow(value: String): RoadmapId = parse(value) ?: error("Invalid RoadmapId: $value")
    }
}

@JvmInline
value class EpicId private constructor(override val value: String) : RecordId, Comparable<EpicId> {
    init { require(PATTERN.matches(value)) { "Invalid EpicId: $value" } }
    override fun compareTo(other: EpicId): Int = value.compareTo(other.value)
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("EPIC\\.[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*")
        fun parse(value: String): EpicId? = parseIdentity(value, PATTERN, ::EpicId)
        fun parseOrThrow(value: String): EpicId = parse(value) ?: error("Invalid EpicId: $value")
    }
}

@JvmInline
value class Revision private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid Revision: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): Revision? = parseIdentity(value, PATTERN, ::Revision)
        fun parseOrThrow(value: String): Revision = parse(value) ?: error("Invalid Revision: $value")
        fun initial(): Revision = parseOrThrow(Canonical.digest("taskctl.initial/alpha1", obj()))
    }
}

@JvmInline
value class ContractDigest private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid ContractDigest: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): ContractDigest? = parseIdentity(value, PATTERN, ::ContractDigest)
        fun parseOrThrow(value: String): ContractDigest = parse(value) ?: error("Invalid ContractDigest: $value")
    }
}
