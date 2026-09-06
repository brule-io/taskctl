package io.brule.workflow.document

import io.brule.workflow.diagnostics.Diagnostic
import java.nio.file.Path

public data class MarkdownSection(
    public val name: String,
    public val content: String,
    public val headingLine: Int,
)

public data class Deliverable(
    public val description: String,
    public val completed: Boolean,
    public val line: Int,
)

public data class ChecklistEntry(
    public val description: String,
    public val completed: Boolean,
    public val line: Int,
)

public data class MarkdownDocument(
    public val heading: String?,
    public val sections: Map<String, MarkdownSection>,
    public val diagnostics: List<Diagnostic>,
) {
    public fun section(name: String): MarkdownSection? = sections[name]

    public fun checklist(sectionName: String): List<ChecklistEntry> {
        val section = section(sectionName) ?: return emptyList()
        val checkbox = Regex("^\\s*- \\[( |x|X)]\\s+(.+?)\\s*$")
        val fences = MarkdownFences()
        return section.content
            .lineSequence()
            .mapIndexedNotNull { offset, line ->
                if (!fences.outside(line)) return@mapIndexedNotNull null
                val match = checkbox.matchEntire(line) ?: return@mapIndexedNotNull null
                ChecklistEntry(
                    description = match.groupValues[2],
                    completed = match.groupValues[1].equals("x", ignoreCase = true),
                    line = section.headingLine + offset + 1,
                )
            }.toList()
    }

    public fun deliverables(): List<Deliverable> =
        checklist("Deliverables").map { entry ->
            Deliverable(entry.description, entry.completed, entry.line)
        }
}

public object MarkdownParser {
    public fun parse(
        path: Path,
        body: String,
        bodyStartLine: Int,
    ): MarkdownDocument {
        val lines = body.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val diagnostics = mutableListOf<Diagnostic>()
        val fences = MarkdownFences()
        val outside = lines.map { fences.outside(it) }
        val heading = lines.withIndex().firstOrNull { outside[it.index] && it.value.startsWith("# ") }?.value?.removePrefix("# ")?.trim()
        val sections = linkedMapOf<String, MarkdownSection>()
        var sectionName: String? = null
        var sectionLine = 0
        var content = mutableListOf<String>()

        fun completeSection() {
            val name = sectionName ?: return
            if (name in sections) {
                diagnostics +=
                    Diagnostic(
                        "DOC_SECTION_DUPLICATE",
                        "duplicate section '$name'",
                        path,
                        sectionLine,
                    )
            } else {
                sections[name] = MarkdownSection(name, content.joinToString("\n").trim(), sectionLine)
            }
        }

        lines.forEachIndexed { index, line ->
            if (outside[index] && line.startsWith("## ")) {
                completeSection()
                sectionName = line.removePrefix("## ").trim()
                sectionLine = bodyStartLine + index
                content = mutableListOf()
            } else if (sectionName != null) {
                content += line
            }
        }
        completeSection()
        return MarkdownDocument(heading, sections.toMap(), diagnostics.sorted())
    }
}

public object MarkdownEditor {
    public fun replaceSection(
        input: String,
        sectionName: String,
        replacementContent: String,
    ): String {
        val normalized = input.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n').toMutableList()
        val heading = "## $sectionName"
        val fences = MarkdownFences()
        val outside = lines.map { fences.outside(it) }
        val start = lines.indices.firstOrNull { outside[it] && lines[it] == heading } ?: -1
        require(start >= 0) { "missing $heading" }
        val next = ((start + 1) until lines.size).firstOrNull { outside[it] && lines[it].startsWith("## ") } ?: lines.size
        val replacement =
            buildList {
                add(heading)
                add("")
                addAll(replacementContent.trim().lines())
                add("")
            }
        lines.subList(start, next).clear()
        lines.addAll(start, replacement)
        return lines.joinToString("\n").trimEnd() + "\n"
    }
}

/** Historical section grammar uses column-zero headings and CommonMark-style
 * backtick/tilde fences indented at most three spaces. An unclosed fence consumes
 * the remainder. Fence contents never contribute checklist state or headings. */
private class MarkdownFences {
    private var marker: Char? = null
    private var length = 0
    fun outside(line: String): Boolean {
        val fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$").matchEntire(line)
        if (marker != null) {
            if (fence != null && fence.groupValues[1].first() == marker && fence.groupValues[1].length >= length && fence.groupValues[2].isBlank()) marker = null
            return false
        }
        if (fence != null && (fence.groupValues[1].first() != '`' || '`' !in fence.groupValues[2])) {
            marker = fence.groupValues[1].first(); length = fence.groupValues[1].length
            return false
        }
        return true
    }
}
