package io.brule.workflow.model

@JvmInline
public value class TaskRef private constructor(
    public val value: String,
) : Comparable<TaskRef> {
    init {
        require(parseForm(value) != null) { "Invalid task reference: $value" }
    }

    override fun compareTo(other: TaskRef): Int = value.compareTo(other.value)

    override fun toString(): String = value

    public companion object {
        public const val EXPECTED_FORMS: String = "TASK.<namespace>.<NNN>"

        public fun parse(value: String): TaskRef? = if (parseForm(value) != null) TaskRef(value) else null
    }
}

private fun parseForm(value: String): MatchResult? = TASK_REF.matchEntire(value)

private val TASK_REF: Regex = Regex("TASK\\.([a-z][a-z0-9-]*)\\.(\\d{3})")

@JvmInline
public value class RoadmapRef(
    public val value: String,
) : Comparable<RoadmapRef> {
    override fun compareTo(other: RoadmapRef): Int = value.compareTo(other.value)

    override fun toString(): String = value

    public companion object {
        private val PATTERN: Regex = Regex("ROADMAP\\.([a-z][a-z0-9-]*)\\.(\\d{3})")

        public fun parse(value: String): RoadmapRef? = if (PATTERN.matches(value)) RoadmapRef(value) else null
    }
}

@JvmInline
public value class EpicRef(
    public val value: String,
) : Comparable<EpicRef> {
    override fun compareTo(other: EpicRef): Int = value.compareTo(other.value)

    override fun toString(): String = value

    public companion object {
        private val PATTERN: Regex = Regex("EPIC\\.([a-z][a-z0-9-]*)\\.(\\d{3})")

        public fun parse(value: String): EpicRef? = if (PATTERN.matches(value)) EpicRef(value) else null
    }
}

@JvmInline
public value class AdrRef(
    public val value: String,
) : Comparable<AdrRef> {
    override fun compareTo(other: AdrRef): Int = value.compareTo(other.value)

    override fun toString(): String = value

    public companion object {
        private val PATTERN: Regex = Regex("ADR\\.(\\d{4})")

        public fun parse(value: String): AdrRef? = if (PATTERN.matches(value)) AdrRef(value) else null
    }
}
