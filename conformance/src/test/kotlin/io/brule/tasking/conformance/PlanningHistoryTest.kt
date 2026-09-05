package io.brule.tasking.conformance

import io.brule.tasking.core.*
import kotlin.test.*

/** Historical facts are tested as source witnesses, not converted to invented
 * native task records/receipts. Synthetic native model tests live in core. */
class PlanningHistoryTest {
    private fun text(file: String): String = javaClass.getResourceAsStream("/planning-history/$file")!!.bufferedReader().use { it.readText() }
    private fun objectValue(value: Value) = value as? ObjectValue ?: error("object expected")
    private fun manifest() = objectValue(YamlValues.parse(text("sources.json")).value)

    @Test fun `every planning witness binds exact source revision and source bytes`() {
        assertEquals("historical-source-evidence-not-native-import", manifest().requiredString("classification"))
        for (entry in manifest().requiredArray("sources")) {
            val source = objectValue(entry)
            assertTrue(source.requiredString("revision").matches(Regex("[a-f0-9]{40}")))
            val bytes = javaClass.getResourceAsStream("/planning-history/${source.requiredString("file")}")!!.readAllBytes()
            assertEquals(source.requiredString("sha256"), Canonical.sha256(bytes))
        }
    }

    @Test fun `Geist roadmap contains tasks associated with two separate epic scopes`() {
        val prefix = "daemon-geist-workspace/"
        val roadmap = text(prefix + "roadmaps/closed/ROADMAP.geist.converse-tool-call-and-copyability.md")
        val members = Regex("(?m)^- \\[x\\] `\\.agents/(tasks/[^`]+)md`").findAll(roadmap).map { it.groupValues[1] + "md" }.toList()
        assertEquals(4, members.size)
        val epics = members.map { path ->
            Regex("(?m)^epic: (.+)$").find(text(prefix + path))!!.groupValues[1]
        }.toSet()
        assertEquals(setOf("EPIC.core.077.tool-catalog-prompt-contract-hardening", "EPIC.operator.148.converse-tool-call-loop-and-copyability"), epics)
        val loop = text(prefix + "tasks/closed/operator/TASK.operator.1159.converse-prompt-tool-call-loop.md")
        assertContains(loop, "depends_on:\n  - TASK.core.388.shell-exec-agent-prompt-contract")
    }

    @Test fun `historical roadmap overlap and active-lane exclusivity are distinguishable`() {
        val prefix = "daemon-platform-workspace/"
        val task = "TASK.alpha.034.spec-first-tooling.md"
        assertContains(text(prefix + "roadmaps/closed/ROADMAP.platform.alpha.hardening.phase2.md"), task)
        assertContains(text(prefix + "roadmaps/closed/ROADMAP.platform.alpha.release.md"), task)
        val policy = text(prefix + "memos/MEMO.process.multi-roadmap-contract.md")
        assertContains(policy, "One task belongs to one roadmap.")
        assertContains(policy, "primary")
        // Both facts must survive: overlapping durable views do not establish
        // concurrent execution ownership or override project policy.
    }

    @Test fun `observed repositories include both many-roadmap and roadmap-free task universes`() {
        val inventory = manifest().requiredArray("local_inventory").map(::objectValue).associateBy { it.requiredString("repository") }
        fun count(repo: String, kind: String): Int =
            assertIs<IntegerValue>(objectValue(inventory.getValue(repo).fields.getValue("counts")).fields[kind]).value.intValueExact()
        assertEquals(20, count("loom-ir", "roadmaps"))
        assertEquals(1, count("loom-ir", "epics"))
        assertEquals(7, count("brule-message-bus", "roadmaps"))
        for (repo in listOf("dropzone/dropzone-app", "dropzone/dropzone-biz")) {
            assertEquals(0, count(repo, "roadmaps"))
            assertTrue(count(repo, "tasks") > 0 && count(repo, "epics") > 0)
        }
    }
}
