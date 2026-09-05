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
        return section.content
            .lineSequence()
            .mapIndexedNotNull { offset, line ->
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
        val heading = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
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
            if (line.startsWith("## ")) {
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
        val start = lines.indexOfFirst { it == heading }
        require(start >= 0) { "missing $heading" }
        val next = ((start + 1) until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
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
