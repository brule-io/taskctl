package io.brule.tasking.core

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.*
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Collections
import java.util.IdentityHashMap

data class SourceSpan(val start: Int, val end: Int)
data class DecodedYaml(val value: Value, val rootFields: Map<String, SourceSpan>)

/** Compose nodes instead of instantiating generic objects; validate the whole
 * tree before any values cross the decoding boundary. */
@UntypedBoundary("SnakeYAML composed input nodes; only typed values leave this codec")
object YamlValues {
    fun parse(source: String): DecodedYaml {
        val options = LoaderOptions().apply { isAllowDuplicateKeys = false; maxAliasesForCollections = 0; codePointLimit = 1_000_000 }
        val root = Yaml(options).compose(StringReader(source)) ?: error("empty YAML document")
        val seen = Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())
        fun decode(node: Node): Value {
            require(seen.add(node)) { "aliases and recursive YAML are not permitted" }
            return when (node) {
                is MappingNode -> {
                    require(node.tag == Tag.MAP)
                    val fields = linkedMapOf<String, Value>()
                    node.value.forEach { entry ->
                        val key = (decode(entry.keyNode) as? StringValue)?.value ?: error("mapping keys must be strings")
                        require(!fields.containsKey(key)) { "duplicate field: $key" }
                        fields[key] = decode(entry.valueNode)
                    }
                    ObjectValue(fields.toMap())
                }
                is SequenceNode -> { require(node.tag == Tag.SEQ); ArrayValue(node.value.map(::decode)) }
                is ScalarNode -> when (node.tag) {
                    Tag.STR -> StringValue(node.value)
                    Tag.NULL -> NullValue
                    Tag.BOOL -> BooleanValue(when (node.value.lowercase()) { "true" -> true; "false" -> false; else -> error("use true/false booleans") })
                    Tag.INT -> { require(node.value.matches(Regex("-?(0|[1-9][0-9]*)"))); IntegerValue(BigInteger(node.value)) }
                    Tag.FLOAT -> DecimalValue(BigDecimal(node.value))
                    else -> error("unsupported YAML tag: ${node.tag}")
                }
                else -> error("unsupported YAML node")
            }
        }
        val value = decode(root)
        val spans = if (root is MappingNode) root.value.associate { entry ->
            val key = (entry.keyNode as? ScalarNode)?.value ?: error("mapping key must be scalar")
            key to SourceSpan(source.offsetByCodePoints(0, entry.valueNode.startMark.index),
                source.offsetByCodePoints(0, entry.valueNode.endMark.index))
        } else emptyMap()
        return DecodedYaml(value, spans)
    }
}
