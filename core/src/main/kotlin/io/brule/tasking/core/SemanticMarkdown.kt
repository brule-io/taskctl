package io.brule.tasking.core

import org.commonmark.node.*
import org.commonmark.parser.Parser

/** CommonMark AST projection. Wrapping, bullet markers and fence lengths are
 * presentation; code, link destinations, nesting and hard breaks matter. */
object SemanticMarkdown {
    private val parser = Parser.builder().build()
    fun value(markdown: String, acceptance: Boolean = false): Value {
        return node(parser.parse(markdown), acceptance)
    }
    private fun node(node: Node, acceptance: Boolean): Value {
        val attributes: List<Value> = when (node) {
            is Heading -> listOf(integer(node.level))
            is Code -> listOf(StringValue(node.literal))
            is FencedCodeBlock -> listOf(StringValue(node.info), StringValue(node.literal))
            is IndentedCodeBlock -> listOf(StringValue(node.literal))
            is HtmlBlock -> listOf(StringValue(node.literal))
            is HtmlInline -> listOf(StringValue(node.literal))
            is Link -> listOf(StringValue(node.destination), optionalString(node.title))
            is Image -> listOf(StringValue(node.destination), optionalString(node.title))
            is OrderedList -> listOf(integer(requireNotNull(node.markerStartNumber)))
            is Document, is Paragraph, is BulletList, is ListItem, is BlockQuote,
            is Emphasis, is StrongEmphasis, is HardLineBreak, is ThematicBreak -> emptyList()
            else -> error("unsupported semantic Markdown node: ${node.javaClass.simpleName}")
        }
        val children = mutableListOf<Value>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                children.add(strings(listOf("Text", text.toString().replace(Regex("[ \\t\\r\\n]+"), " "))))
                text.setLength(0)
            }
        }
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> {
                    // Strip progress markers only in actual checklist nodes;
                    // a matching line inside a fenced code example is contract.
                    val checklistStart = acceptance && node is Paragraph && node.parent is ListItem &&
                        node.parent.firstChild == node && node.firstChild == child
                    text.append(if (checklistStart) child.literal.replace(Regex("^\\[[ xX]\\] "), "") else child.literal)
                }
                is SoftLineBreak -> text.append(' ')
                else -> { flush(); children.add(node(child, acceptance)) }
            }
            child = child.next
        }
        flush()
        return ArrayValue(listOf(StringValue(node.javaClass.simpleName), ArrayValue(attributes), ArrayValue(children)))
    }
}
