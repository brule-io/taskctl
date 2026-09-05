package io.brule.tasking.core

import kotlin.test.*

class CoreConformanceTest {
    @Test fun `extension algebra retains exact integers decimals booleans null and nested shape`() {
        val decoded = YamlValues.parse("""
            integer: 123456789012345678901234567890
            decimal: 12345678901234567890.1234567890123456789
            values: [true, null, "001", {nested: false}]
        """.trimIndent()).value as? ObjectValue ?: error("object expected")
        assertEquals("123456789012345678901234567890", assertIs<IntegerValue>(decoded.fields["integer"]).value.toString())
        assertEquals("12345678901234567890.1234567890123456789", assertIs<DecimalValue>(decoded.fields["decimal"]).value.toPlainString())
        val values = decoded.requiredArray("values")
        assertEquals(BooleanValue(true), values[0])
        assertEquals(NullValue, values[1])
        assertEquals(StringValue("001"), values[2])
        assertEquals(obj("nested" to BooleanValue(false)), values[3])
        assertEquals(decoded, YamlValues.parse(Json.encode(decoded)).value)
    }
    private val source = """
        protocol: tasking/core-draft-1
        id: TASK.process.006.workflow-canon-backport
        title: '🧬 Durable task'
        state: open
        intent: Keep fulfillment durable.
        requires: []
        requirements: [Save fulfillment before acknowledging checkout.]
        acceptance: ['- Integration test passes.']
        required_extensions: []
        extensions:
          project.annotation/v1:
            amount: 12345678901234567890.123456789
            # opaque annotation must stay byte-for-byte
            escaped: "a\nb"
            ordered: [one, two]
    """.trimIndent() + "\n"
    private fun record() = DraftDocument.parse(source).record

    @Test fun `unknown core fields fail while optional extensions survive exact round trips`() {
        val document = DraftDocument.parse(source)
        assertEquals(source, document.render())
        val edited = document.withTitle("New title 🦜")
        assertEquals("New title 🦜", edited.record.title)
        assertEquals(source.substringAfter("extensions:"), edited.render().substringAfter("extensions:"))
        assertFails { DraftDocument.parse(source + "gate: 6\n") }
        assertFails { DraftDocument.parse(source.replace("project.annotation/v1", "project")) }
        assertFails { DraftDocument.parse(source.replace("requires: []", "requires: []\nrequires: []")) }
        assertFails { DraftDocument.parse(source.replace("state: open", "state: &s open").replace("intent: Keep fulfillment durable.", "intent: *s")) }
    }

    @Test fun `YAML presentation and optional annotations do not invalidate a semantic contract`() {
        val a = record()
        val b = DraftDocument.parse(source.replace("title: '🧬 Durable task'", "title: \"🧬 Durable task\"")
            .replace("state: open\n", "").replace("id:", "state: open\nid:")
            .replace("12345678901234567890.123456789", "1.0")).record
        assertEquals(DraftLifecycle.contract(a), DraftLifecycle.contract(b))
        assertNotEquals(DraftLifecycle.contract(a), DraftLifecycle.contract(a.copy(acceptance = listOf("Unit test passes."))))
        assertNotEquals(DraftLifecycle.contract(a), DraftLifecycle.contract(a.copy(requires = listOf(TaskId.parseOrThrow("TASK.other")))))
    }

    @Test fun `Markdown contracts ignore wrapping and checkbox progress but retain nested criteria and code`() {
        fun digest(text: String) = Canonical.digest("test", SemanticMarkdown.value(text, acceptance = true))
        assertEquals(digest("- [ ] Durable result\n  survives restart."), digest("* [x] Durable result survives restart."))
        assertNotEquals(digest("- [ ] Durable result\n  survives restart."), digest("- [ ] Durable result\n  survives retry."))
        assertNotEquals(digest("`a  b`"), digest("`a b`"))
        assertNotEquals(digest("[test](one)"), digest("[test](two)"))
        assertNotEquals(digest("one  \ntwo"), digest("one\ntwo"))
        assertNotEquals(digest("```text\n- [ ] literal code\n```"), digest("```text\n- [x] literal code\n```"))
    }

    @Test fun `historical receipt still addresses old contract after current acceptance changes`() {
        val old = record()
        val receipt = Receipt(old.id, DraftLifecycle.contract(old), mapOf("test" to "integration passed at source revision A"))
        val revised = old.copy(acceptance = old.acceptance + "Result survives process restart.")
        assertTrue(DraftLifecycle.addresses(receipt, old))
        assertFalse(DraftLifecycle.addresses(receipt, revised))
        assertContains(DraftLifecycle.closureProblems(listOf(revised), revised.id, receipt), "receipt does not address current contract")
    }

    @Test fun `required unavailable providers allow inspection but block closure and contract identity includes profile`() {
        val document = DraftDocument.parse(source.replace("required_extensions: []", "required_extensions: [daemon.workspace/v1]"))
        assertEquals(record().id, document.record.id)
        val task = document.record
        val receipt = Receipt(task.id, DraftLifecycle.contract(task), mapOf("test" to "passed"))
        assertTrue(DraftLifecycle.closureProblems(listOf(task), task.id, receipt).any { "unavailable" in it })
        assertNotEquals(DraftLifecycle.contract(task), DraftLifecycle.contract(task, Profile(mapOf("daemon.workspace/v1" to "sha256:one"))))
    }

    private fun provider(contribution: Contribution = Contribution()) = object : SemanticProvider {
        override val identity = "daemon.workspace/v1"
        override val pin = "sha256:fixture-provider-1"
        override fun evaluate(record: DraftRecord) = contribution
        override fun verify(record: DraftRecord, evidence: Map<String, String>) = emptyList<String>()
    }

    @Test fun `installed providers do not activate until pinned and contributions cannot waive core prerequisites`() {
        val p = provider()
        val task = record().copy(requires = listOf(TaskId.parseOrThrow("TASK.dependency")))
        val dependency = record().copy(id = TaskId.parseOrThrow("TASK.dependency"))
        val profile = Profile(mapOf(p.identity to p.pin))
        val receipt = Receipt(task.id, DraftLifecycle.contract(task, profile), mapOf("test" to "passed"))
        val errors = DraftLifecycle.closureProblems(listOf(task, dependency), task.id, receipt, profile, listOf(p))
        assertContains(errors, "prerequisite not closed: TASK.dependency")
        val clean = record()
        val cleanReceipt = Receipt(clean.id, DraftLifecycle.contract(clean), mapOf("test" to "passed"))
        assertTrue(DraftLifecycle.closureProblems(listOf(clean), clean.id, cleanReceipt, providers = listOf(provider(Contribution(blockers = listOf("must not activate"))))).isEmpty())
    }

    @Test fun `contributed dependencies participate in cycle detection and evidence requirements fail closed`() {
        val task = record()
        val p = provider(Contribution(prerequisites = listOf(task.id), evidenceRequirements = listOf("workspace-attestation")))
        val profile = Profile(mapOf(p.identity to p.pin))
        val receipt = Receipt(task.id, DraftLifecycle.contract(task, profile), mapOf("test" to "passed"))
        val errors = DraftLifecycle.closureProblems(listOf(task), task.id, receipt, profile, listOf(p))
        assertTrue(errors.any { "cycle" in it })
        assertContains(errors, "missing evidence: workspace-attestation")
        assertEquals(listOf("duplicate task identity"), DraftLifecycle.closureProblems(listOf(task, task), task.id, receipt))
    }

    @Test fun `provider payloads and pins affect contracts only when semantic`() {
        val a = record()
        val b = a.copy(extensions = ObjectValue(a.extensions.fields + ("project.annotation/v1" to obj("amount" to integer(99)))))
        val profile = Profile(mapOf("project.annotation/v1" to "schema-sha256:one"))
        assertEquals(DraftLifecycle.contract(a), DraftLifecycle.contract(b))
        assertNotEquals(DraftLifecycle.contract(a, profile), DraftLifecycle.contract(b, profile))
        assertNotEquals(DraftLifecycle.contract(a, profile), DraftLifecycle.contract(a, Profile(mapOf("project.annotation/v1" to "schema-sha256:two"))))
    }
}
