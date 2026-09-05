package io.brule.tasking.core

/** Executable design experiment; explicitly not native v1. */
data class DraftRecord(
    val id: String, val title: String, val state: String, val intent: String,
    val requires: List<String>, val requirements: List<String>, val acceptance: List<String>,
    val requiredExtensions: List<String>, val extensions: ObjectValue,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && intent.isNotBlank())
        require(state in setOf("open", "closed"))
        require(requires.all { it.isNotBlank() } && requires.distinct().size == requires.size)
        require(requirements.isNotEmpty() && requirements.all { it.isNotBlank() })
        require(acceptance.isNotEmpty() && acceptance.all { it.isNotBlank() })
    }
}

class DraftDocument private constructor(val record: DraftRecord, val source: String, private val titleSpan: SourceSpan) {
    fun render(): String = source
    /** In-memory contract edit; splices one scalar and preserves all other
     * source bytes. Lifecycle writing is not exposed by this codec. */
    fun withTitle(title: String): DraftDocument {
        require(title.isNotBlank())
        return parse(source.replaceRange(titleSpan.start, titleSpan.end, Json.encode(StringValue(title))))
    }
    companion object {
        private val fields = setOf("protocol", "id", "title", "state", "intent", "requires", "requirements", "acceptance", "required_extensions", "extensions")
        private val feature = Regex("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+/v[1-9][0-9]*")
        fun parse(source: String): DraftDocument {
            val decoded = YamlValues.parse(source)
            val root = decoded.value as? ObjectValue ?: error("record must be a YAML mapping")
            val values = root.fields
            require((values.keys - fields).isEmpty()) { "unknown core fields: ${values.keys - fields}" }
            require(root.requiredString("protocol") == "tasking/core-draft-1") { "native v1 is not frozen or accepted" }
            fun text(key: String) = root.requiredString(key).also { require(it.isNotBlank()) { "$key cannot be blank" } }
            fun texts(key: String, required: Boolean = false): List<String> {
                if (key !in values && !required) return emptyList()
                val result = root.requiredArray(key).map { (it as? StringValue)?.value ?: error("$key must contain strings") }
                require(result.all { it.isNotBlank() } && (!required || result.isNotEmpty()))
                return result
            }
            val extensions = (values["extensions"] ?: obj()) as? ObjectValue ?: error("extensions must be a mapping")
            require(extensions.fields.keys.all { feature.matches(it) }) { "extensions require versioned namespaces" }
            val required = texts("required_extensions")
            require(required.distinct().size == required.size && required.all { feature.matches(it) })
            val dependencies = texts("requires")
            require(dependencies.distinct().size == dependencies.size)
            val state = text("state").also { require(it in setOf("open", "closed")) }
            val record = DraftRecord(text("id"), text("title"), state, text("intent"), dependencies,
                texts("requirements", true), texts("acceptance", true), required, extensions)
            return DraftDocument(record, source, decoded.rootFields.getValue("title"))
        }
    }
}
