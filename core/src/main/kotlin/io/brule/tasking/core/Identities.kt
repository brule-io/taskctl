package io.brule.tasking.core

sealed interface RecordId { val value: String }
sealed interface PlanningId : RecordId {
    companion object {
        fun parse(value: String): PlanningId? = RoadmapId.parse(value) ?: EpicId.parse(value)
        fun parseOrThrow(value: String): PlanningId = parse(value) ?: error("Invalid PlanningId: $value")
    }
}

private fun <T> parseIdentity(value: String, pattern: Regex, construct: (String) -> T): T? =
    if (pattern.matches(value)) construct(value) else null

@JvmInline
value class ImportId private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid ImportId: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): ImportId? = parseIdentity(value, PATTERN, ::ImportId)
        fun parseOrThrow(value: String): ImportId = parse(value) ?: error("Invalid ImportId: $value")
    }
}

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
value class RoadmapId private constructor(override val value: String) : PlanningId, Comparable<RoadmapId> {
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
value class EpicId private constructor(override val value: String) : PlanningId, Comparable<EpicId> {
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

@JvmInline
value class TaskRevisionId private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid TaskRevisionId: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): TaskRevisionId? = parseIdentity(value, PATTERN, ::TaskRevisionId)
        fun parseOrThrow(value: String): TaskRevisionId = parse(value) ?: error("Invalid TaskRevisionId: $value")
    }
}

@JvmInline
value class InputDigest private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid InputDigest: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): InputDigest? = parseIdentity(value, PATTERN, ::InputDigest)
        fun parseOrThrow(value: String): InputDigest = parse(value) ?: error("Invalid InputDigest: $value")
    }
}

@JvmInline
value class PlanningRevisionId private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid PlanningRevisionId: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): PlanningRevisionId? = parseIdentity(value, PATTERN, ::PlanningRevisionId)
        fun parseOrThrow(value: String): PlanningRevisionId = parse(value) ?: error("Invalid PlanningRevisionId: $value")
    }
}

@JvmInline
value class PlanningAssessmentId private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid PlanningAssessmentId: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): PlanningAssessmentId? = parseIdentity(value, PATTERN, ::PlanningAssessmentId)
        fun parseOrThrow(value: String): PlanningAssessmentId = parse(value) ?: error("Invalid PlanningAssessmentId: $value")
    }
}
