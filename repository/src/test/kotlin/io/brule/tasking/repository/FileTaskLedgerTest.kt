package io.brule.tasking.repository

import io.brule.tasking.core.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class FileTaskLedgerTest {
    @TempDir lateinit var directory: Path
    private val task = DraftRecord(TaskId.parseOrThrow("TASK.a"), "A", "open", "Bounded work", emptyList(), listOf("Persist"), listOf("Durable"), emptyList(), obj())
    private fun create(seed: Transition.AddRecords = Transition.AddRecords()): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "launcher fixture\n") }
        val lock = "lockFormat=2\nwrapperVersion=2\ntoolVersion=0.2.0-alpha.1\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val root = directory.resolve("project")
        val plan = Bootstrap.plan(root, "test.repo", "0.2.0-alpha.1", distribution.parent, lock, seed)
        assertFalse(Files.exists(root))
        Bootstrap.apply(plan)
        return FileTaskLedger(root)
    }

    @Test fun `bootstrap and reads are empty valid and mutation free`() {
        val ledger = create()
        fun inventory() = Files.walk(ledger.root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
            it to Pair(Files.readString(it), Files.getLastModifiedTime(it))
        } }
        val before = inventory()
        assertTrue(ledger.snapshot().universe.tasks.isEmpty())
        assertTrue(ledger.frontier().tasks.isEmpty())
        assertEquals(before, inventory())
        assertFails { create() }
    }

    @Test fun `CAS and shared closure preserve opaque source and historical evidence`() {
        val ledger = create(Transition.AddRecords(listOf(task)))
        val taskPath = Files.list(ledger.root.resolve(".agents/tasks")).use { it.findFirst().orElseThrow() }
        val document = """
            protocol: tasking/core-draft-1
            id: TASK.a
            title: A
            state: open
            intent: Bounded work
            requirements: [Persist]
            acceptance: [Durable]
            extensions:
              test.opaque/v1:
                # retain exact source
                huge: 123456789012345678901234567890.000001
        """.trimIndent() + "\n"
        Files.writeString(taskPath, document)
        val before = ledger.snapshot()
        val record = before.universe.tasks.single()
        val evidence = ClosureEvidence(Receipt(task.id, DraftLifecycle.contract(record), mapOf("test" to "passed")), "tester", "2026-09-05T00:00:00Z")
        assertFailsWith<RevisionConflict> { ledger.apply(Revision.parseOrThrow("sha256:" + "f".repeat(64)), Transition.CloseTask(evidence)) }
        ledger.apply(before.revision, Transition.CloseTask(evidence))
        assertEquals("closed", ledger.task(task.id)!!.state)
        assertEquals(document.substringAfter("extensions:"), Files.readString(taskPath).substringAfter("extensions:"))
        assertEquals(evidence, ledger.snapshot().receipts.single())
        Files.writeString(taskPath, Files.readString(taskPath).replace("[Durable]", "[More durable]"))
        assertFails { ledger.snapshot() } // Old evidence remains, but cannot prove changed acceptance.
    }

    @Test fun `missing prerequisites wrong kind and fabricated closed seeds fail before writes`() {
        val ledger = create()
        val revision = ledger.snapshot().revision
        assertFails { ledger.apply(revision, Transition.AddRecords(listOf(task.copy(requires = listOf(TaskId.parseOrThrow("TASK.missing")))))) }
        assertFails { ledger.apply(revision, Transition.AddRecords(listOf(task.copy(state = "closed")))) }
        assertEquals(revision, ledger.snapshot().revision)
    }

    @Test fun `recovery completes only matching preimages and rejects unrelated paths`() {
        val ledger = create()
        val path = ".agents/tasks/" + NativeFiles.fileName(task.id.value)
        val content = Json.encode(NativeCodec.task(task)) + "\n"
        val operation = obj("contract" to StringValue("taskctl.transaction/alpha1"),
            "before" to obj(path to NullValue), "after" to obj(path to StringValue(content)))
        NativeFiles.atomicWrite(ledger.root, ".agents/runtime/transaction.json", Json.encode(operation))
        assertFails { ledger.snapshot() }
        ledger.recover()
        assertEquals(task, ledger.task(task.id))
        NativeFiles.atomicWrite(ledger.root, ".agents/runtime/transaction.json", Json.encode(operation))
        Files.writeString(ledger.root.resolve(path), content + "# External change\n")
        assertFails { ledger.recover() }
        assertTrue(Files.readString(ledger.root.resolve(path)).endsWith("# External change\n"))
        val escape = obj("contract" to StringValue("taskctl.transaction/alpha1"), "before" to obj("source.txt" to NullValue), "after" to obj("source.txt" to StringValue("bad")))
        NativeFiles.atomicWrite(ledger.root, ".agents/runtime/transaction.json", Json.encode(escape))
        assertFails { ledger.recover() }
        assertFalse(Files.exists(ledger.root.resolve("source.txt")))
    }
}
