package io.brule.tasking.core

private fun <T> parseProfileIdentity(value: String, pattern: Regex, construct: (String) -> T): T? =
    if (pattern.matches(value)) construct(value) else null

@JvmInline
value class ExtensionId private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid ExtensionId: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+/v[1-9][0-9]*")
        fun parse(value: String): ExtensionId? = parseProfileIdentity(value, PATTERN, ::ExtensionId)
        fun parseOrThrow(value: String): ExtensionId = parse(value) ?: error("Invalid ExtensionId: $value")
    }
}

@JvmInline
value class ProviderId private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid ProviderId: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+/v[1-9][0-9]*")
        fun parse(value: String): ProviderId? = parseProfileIdentity(value, PATTERN, ::ProviderId)
        fun parseOrThrow(value: String): ProviderId = parse(value) ?: error("Invalid ProviderId: $value")
    }
}

@JvmInline
value class ProfileId private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid ProfileId: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+/v[1-9][0-9]*")
        fun parse(value: String): ProfileId? = parseProfileIdentity(value, PATTERN, ::ProfileId)
        fun parseOrThrow(value: String): ProfileId = parse(value) ?: error("Invalid ProfileId: $value")
    }
}

@JvmInline
value class ProviderVersion private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid ProviderVersion: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}")
        fun parse(value: String): ProviderVersion? = parseProfileIdentity(value, PATTERN, ::ProviderVersion)
        fun parseOrThrow(value: String): ProviderVersion = parse(value) ?: error("Invalid ProviderVersion: $value")
    }
}

@JvmInline
value class ProviderDigest private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid ProviderDigest: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): ProviderDigest? = parseProfileIdentity(value, PATTERN, ::ProviderDigest)
        fun parseOrThrow(value: String): ProviderDigest = parse(value) ?: error("Invalid ProviderDigest: $value")
    }
}

@JvmInline
value class ProfileDigest private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid ProfileDigest: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): ProfileDigest? = parseProfileIdentity(value, PATTERN, ::ProfileDigest)
        fun parseOrThrow(value: String): ProfileDigest = parse(value) ?: error("Invalid ProfileDigest: $value")
    }
}

@JvmInline
value class ProfileRevisionId private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "Invalid ProfileRevisionId: $value" } }
    override fun toString(): String = value
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): ProfileRevisionId? = parseProfileIdentity(value, PATTERN, ::ProfileRevisionId)
        fun parseOrThrow(value: String): ProfileRevisionId = parse(value) ?: error("Invalid ProfileRevisionId: $value")
    }
}
