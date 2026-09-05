package io.brule.workflow.document

import io.brule.workflow.diagnostics.Diagnostic
import java.nio.file.Path

public sealed interface FrontMatterValue {
    public data class Scalar(
        public val value: String,
    ) : FrontMatterValue

    public data class Sequence(
        public val values: List<String>,
    ) : FrontMatterValue
}

public data class FrontMatterDocument(
    public val path: Path,
    public val fields: Map<String, FrontMatterValue>,
    public val body: String,
    public val normalizedText: String,
    public val bodyStartLine: Int,
) {
    public fun scalar(name: String): String? = (fields[name] as? FrontMatterValue.Scalar)?.value

    public fun sequence(name: String): List<String>? = (fields[name] as? FrontMatterValue.Sequence)?.values
}

public data class FrontMatterParseResult(
    public val document: FrontMatterDocument?,
    public val diagnostics: List<Diagnostic>,
)

public object FrontMatterParser {
    private val keyPattern: Regex = Regex("[a-z][a-z0-9_-]*")

    public fun parse(
        path: Path,
        input: String,
    ): FrontMatterParseResult {
        val diagnostics = mutableListOf<Diagnostic>()
        if (input.startsWith("\uFEFF")) {
            diagnostics += Diagnostic("DOC_ENCODING_BOM", "UTF-8 byte-order marks are not permitted", path, 1)
        }
        if ('\r' in input) {
            diagnostics += Diagnostic("DOC_LINE_ENDING", "documents must use LF line endings", path, 1)
        }
        val normalized = input.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        if (lines.firstOrNull() != "---") {
            return FrontMatterParseResult(
                null,
                listOf(Diagnostic("DOC_FRONTMATTER_OPEN", "document must begin with ---", path, 1)),
            )
        }
        val closingIndex = lines.indexOfFirstFrom(1) { it == "---" }
        if (closingIndex < 0) {
            return FrontMatterParseResult(
                null,
                listOf(Diagnostic("DOC_FRONTMATTER_CLOSE", "front matter is missing its closing ---", path, 1)),
            )
        }

        val fields = linkedMapOf<String, FrontMatterValue>()
        var currentSequence: String? = null
        for (index in 1 until closingIndex) {
            val line = lines[index]
            val lineNumber = index + 1
            if (line.isBlank()) continue
            if (line.startsWith("  - ")) {
                val sequenceName = currentSequence
                if (sequenceName == null) {
                    diagnostics +=
                        Diagnostic(
                            "DOC_SEQUENCE_WITHOUT_KEY",
                            "sequence item has no preceding empty field",
                            path,
                            lineNumber,
                        )
                    continue
                }
                val current = fields[sequenceName] as FrontMatterValue.Sequence
                fields[sequenceName] = FrontMatterValue.Sequence(current.values + line.removePrefix("  - "))
                continue
            }
            if (line.trimStart().startsWith("- ")) {
                diagnostics +=
                    Diagnostic(
                        "DOC_SEQUENCE_INDENT",
                        "sequence items require exactly two spaces of indentation",
                        path,
                        lineNumber,
                    )
                continue
            }

            currentSequence = null
            if (line.firstOrNull()?.isWhitespace() == true) {
                diagnostics +=
                    Diagnostic("DOC_FIELD_INDENT", "front-matter fields must not be indented", path, lineNumber)
                continue
            }
            val separator = line.indexOf(':')
            if (separator <= 0) {
                diagnostics += Diagnostic("DOC_FIELD_SYNTAX", "expected key: value", path, lineNumber)
                continue
            }
            val key = line.substring(0, separator).trim()
            if (!keyPattern.matches(key)) {
                diagnostics += Diagnostic("DOC_FIELD_NAME", "invalid front-matter key '$key'", path, lineNumber)
                continue
            }
            if (key in fields) {
                diagnostics += Diagnostic("DOC_FIELD_DUPLICATE", "duplicate front-matter key '$key'", path, lineNumber)
                continue
            }
            val rawValue = line.substring(separator + 1).trim()
            if (rawValue.isEmpty()) {
                fields[key] = FrontMatterValue.Sequence(emptyList())
                currentSequence = key
            } else {
                if (rawValue.startsWith('[') || rawValue.startsWith('{') || rawValue.startsWith('&') ||
                    rawValue.startsWith('*') || rawValue.startsWith('!') ||
                    (
                        rawValue.length >= 2 && rawValue.first() in
                            setOf(
                                '\'',
                                '"',
                            ) && rawValue.last() == rawValue.first()
                    )
                ) {
                    diagnostics +=
                        Diagnostic(
                            "DOC_SCALAR_COMPLEX",
                            "field '$key' must use an unquoted plain scalar",
                            path,
                            lineNumber,
                        )
                }
                fields[key] = FrontMatterValue.Scalar(rawValue)
            }
        }
        val bodyLines = lines.drop(closingIndex + 1)
        val body = bodyLines.joinToString("\n")
        return FrontMatterParseResult(
            FrontMatterDocument(path, fields.toMap(), body, normalized, closingIndex + 2),
            diagnostics.sorted(),
        )
    }

    public fun replaceScalar(
        input: String,
        name: String,
        value: String,
    ): String {
        val normalized = input.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n').toMutableList()
        val closingIndex = lines.indexOfFirstFrom(1) { it == "---" }
        require(closingIndex > 0) { "front matter is malformed" }
        val fieldIndex =
            (1 until closingIndex).firstOrNull { index ->
                lines[index].substringBefore(':').trim() == name
            }
        if (fieldIndex == null) {
            lines.add(closingIndex, "$name: $value")
        } else {
            lines[fieldIndex] = "$name: $value"
        }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun List<String>.indexOfFirstFrom(
        start: Int,
        predicate: (String) -> Boolean,
    ): Int {
        for (index in start until size) if (predicate(this[index])) return index
        return -1
    }
}
