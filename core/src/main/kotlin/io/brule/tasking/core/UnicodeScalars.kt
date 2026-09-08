package io.brule.tasking.core

/** JVM strings may contain unpaired surrogates; protocol strings may not. */
internal fun requireUnicodeScalars(value: String) {
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char.isHighSurrogate() -> {
                require(index + 1 < value.length && value[index + 1].isLowSurrogate()) { "unpaired Unicode surrogate" }
                index += 2
            }
            char.isLowSurrogate() -> throw IllegalArgumentException("unpaired Unicode surrogate")
            else -> index++
        }
    }
}
