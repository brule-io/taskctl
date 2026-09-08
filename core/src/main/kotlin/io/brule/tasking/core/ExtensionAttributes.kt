package io.brule.tasking.core

/** The same namespace boundary applies to decoded and programmatic records. */
internal fun validateExtensionAttributes(required: List<String>, extensions: ObjectValue) {
    require(extensions.fields.keys.all { ExtensionId.parse(it) != null }) { "extensions require versioned namespaces" }
    require(required.distinct().size == required.size && required.all { ExtensionId.parse(it) != null }) {
        "required extensions need unique versioned namespaces"
    }
}
