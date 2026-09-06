package io.brule.workflow.task

import io.brule.workflow.document.MarkdownParser
import io.brule.workflow.document.MarkdownEditor
import io.brule.workflow.document.FrontMatterParser
import java.nio.file.Path
import kotlin.test.*

class MarkdownFenceTest {
    @Test fun `examples cannot impersonate headings checklists or front matter`() {
        for (fence in listOf("```", "~~~~")) {
            val source = "---\nid: example\n---\n# Actual\n## Deliverables\n- [x] Real item\n${fence}markdown\n## Closure\n- [ ] Fictional item\n---\nid: fake\n---\n$fence\n## Closure\nActual evidence\n"
            val front = requireNotNull(FrontMatterParser.parse(Path.of("fixture.md"), source).document)
            assertEquals("example", front.scalar("id"))
            val document = MarkdownParser.parse(Path.of("fixture.md"), front.body, front.bodyStartLine)
            assertEquals(listOf("Deliverables", "Closure"), document.sections.keys.toList())
            assertTrue(document.diagnostics.isEmpty())
            assertEquals(listOf("Real item"), document.checklist("Deliverables").map { it.description })
            val edited = MarkdownEditor.replaceSection(source, "Closure", "New actual evidence")
            assertContains(edited, "## Closure\n- [ ] Fictional item")
            assertContains(edited, "New actual evidence")
        }
    }
    @Test fun `shorter and wrong marker fences never terminate examples`() {
        val body = "# Actual\n## Deliverables\n- [x] Real\n````\n```\n~~~\n## Closure\n- [ ] Example\n"
        val document = MarkdownParser.parse(Path.of("fixture.md"), body, 1)
        assertEquals(listOf("Deliverables"), document.sections.keys.toList())
        assertEquals(1, document.checklist("Deliverables").size)
    }
}
