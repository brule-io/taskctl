package io.brule.workflow.model

/** One parsing convention, four distinct nominal types. */
private fun <T> parseReference(value: String, pattern: Regex, construct: (String) -> T): T? =
    if (pattern.matches(value)) construct(value) else null

@JvmInline
public value class TaskRef private constructor(public val value: String) : Comparable<TaskRef> {
    init { require(PATTERN.matches(value)) { "Invalid reference (expected $EXPECTED_FORMS): $value" } }
    override fun compareTo(other: TaskRef): Int = value.compareTo(other.value)
    override fun toString(): String = value
    public companion object {
        public const val EXPECTED_FORMS: String = "TASK.<namespace>.<NNN>"
        private val PATTERN: Regex = Regex("TASK\\.([a-z][a-z0-9-]*)\\.(\\d{3})")
        public fun parse(value: String): TaskRef? = parseReference(value, PATTERN, ::TaskRef)
    }
}

@JvmInline
public value class RoadmapRef private constructor(public val value: String) : Comparable<RoadmapRef> {
    init { require(PATTERN.matches(value)) { "Invalid reference (expected $EXPECTED_FORMS): $value" } }
    override fun compareTo(other: RoadmapRef): Int = value.compareTo(other.value)
    override fun toString(): String = value
    public companion object {
        public const val EXPECTED_FORMS: String = "ROADMAP.<namespace>.<NNN>"
        private val PATTERN: Regex = Regex("ROADMAP\\.([a-z][a-z0-9-]*)\\.(\\d{3})")
        public fun parse(value: String): RoadmapRef? = parseReference(value, PATTERN, ::RoadmapRef)
    }
}

@JvmInline
public value class EpicRef private constructor(public val value: String) : Comparable<EpicRef> {
    init { require(PATTERN.matches(value)) { "Invalid reference (expected $EXPECTED_FORMS): $value" } }
    override fun compareTo(other: EpicRef): Int = value.compareTo(other.value)
    override fun toString(): String = value
    public companion object {
        public const val EXPECTED_FORMS: String = "EPIC.<namespace>.<NNN>"
        private val PATTERN: Regex = Regex("EPIC\\.([a-z][a-z0-9-]*)\\.(\\d{3})")
        public fun parse(value: String): EpicRef? = parseReference(value, PATTERN, ::EpicRef)
    }
}

@JvmInline
public value class AdrRef private constructor(public val value: String) : Comparable<AdrRef> {
    init { require(PATTERN.matches(value)) { "Invalid reference (expected $EXPECTED_FORMS): $value" } }
    override fun compareTo(other: AdrRef): Int = value.compareTo(other.value)
    override fun toString(): String = value
    public companion object {
        public const val EXPECTED_FORMS: String = "ADR.<NNNN>"
        private val PATTERN: Regex = Regex("ADR\\.(\\d{4})")
        public fun parse(value: String): AdrRef? = parseReference(value, PATTERN, ::AdrRef)
    }
}
