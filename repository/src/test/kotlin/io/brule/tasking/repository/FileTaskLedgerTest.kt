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
        val evidence = ClosureEvidence(Receipt(task.id, DraftLifecycle.contract(record), mapOf("test" to "passed")), "tester", LegacyRecordedAt.parseOrThrow("2026-09-05T00:00:00Z"))
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

    @Test fun `revision and review persistence preserve old receipts across a cold load`() {
        val initial = task.copy(protocol = "tasking/core-draft-2", verification = listOf("test"))
        val ledger = create(Transition.AddRecords(listOf(initial)))
        val evidence = ClosureEvidence(Receipt(initial.id, DraftLifecycle.contract(initial), mapOf("test" to "passed")), "tester", LegacyRecordedAt.parseOrThrow("2026-09-06T00:00:00Z"))
        ledger.apply(ledger.snapshot().revision, Transition.CloseTask(evidence))
        val before = ledger.snapshot()
        val revised = initial.copy(state = "closed", acceptance = listOf("A stronger outcome"))
        val plan = ledger.plan(before.revision, Transition.ReviseTask(revised))
        assertTrue(plan.requiredArray("writes").isNotEmpty())
        assertEquals(before.revision, ledger.snapshot().revision)
        ledger.apply(before.revision, Transition.ReviseTask(revised))
        val cold = FileTaskLedger(ledger.root)
        assertEquals("closed", cold.task(initial.id)!!.state)
        assertEquals(Currency.AFFECTED, cold.snapshot().currency().getValue(initial.id).state)
        assertEquals(before.receipts, cold.snapshot().receipts)
        val review = Reconciliation(initial.id, cold.snapshot().history!!.heads.getValue(initial.id), ReviewOutcome.REVALIDATED,
            emptyList(), "tester", LegacyRecordedAt.parseOrThrow("2026-09-06T00:00:00Z"), "Revalidated the stronger contract.", mapOf("test" to "passed"))
        cold.apply(cold.snapshot().revision, Transition.ReconcileTask(review))
        assertEquals(Currency.CURRENT, FileTaskLedger(ledger.root).snapshot().currency().getValue(initial.id).state)
        assertEquals(before.receipts, cold.snapshot().receipts)
        val revisionPath = Files.list(ledger.root.resolve(".agents/history/revisions")).use { it.findFirst().orElseThrow() }
        Files.writeString(revisionPath, Files.readString(revisionPath).replace("Bounded work", "Forged work"))
        assertFails { cold.snapshot() }
    }

    @Test fun `new occurrence envelopes survive restart alongside legacy evidence without storage clock claims`() {
        val a = task.copy(protocol = "tasking/core-draft-2", verification = listOf("test"))
        val b = a.copy(id = TaskId.parseOrThrow("TASK.b"), requires = listOf(a.id))
        val ledger = create(Transition.AddRecords(listOf(a, b)))
        val original = ledger.snapshot()
        val legacy = ClosureEvidence(Receipt(a.id, DraftLifecycle.contract(a), mapOf("test" to "Old assertion")),
            "actor", LegacyRecordedAt.parseOrThrow("  before the clock was recorded\n "))
        ledger.apply(original.revision, Transition.CloseTask(legacy))
        val afterA = ledger.snapshot()
        val evidence = ClosureEvidence(Receipt(b.id, DraftLifecycle.contract(b), mapOf("test" to "New assertion")),
            "actor", OccurredAt.parseOrThrow("9999-12-31T23:59:59.000000001+00:00"))
        assertFailsWith<RevisionConflict> { ledger.apply(original.revision, Transition.CloseTask(evidence)) }
        assertEquals(afterA, FileTaskLedger(ledger.root).snapshot())
        assertNull(ledger.apply(afterA.revision, Transition.CloseTask(evidence)).acceptedAt)
        val beforeRevision = FileTaskLedger(ledger.root).snapshot()
        val receiptFiles = NativeFiles.regularFiles(ledger.root, ".agents/receipts")
        assertEquals(listOf(legacy, evidence).toSet(), beforeRevision.receipts.toSet())
        ledger.apply(beforeRevision.revision, Transition.ReviseTask(a.copy(state = "closed", acceptance = listOf("Stronger durability"))))
        val inspected = ledger.snapshot()
        val review = Reconciliation(a.id, inspected.history!!.heads.getValue(a.id), ReviewOutcome.REVALIDATED,
            emptyList(), "actor", OccurredAt.parseOrThrow("0001-01-01T00:00:00Z"), "Explicitly rechecked the changed contract", mapOf("test" to "Revalidated"))
        assertNull(ledger.apply(inspected.revision, Transition.ReconcileTask(review)).acceptedAt)
        val cold = FileTaskLedger(ledger.root).snapshot()
        val coldHistory = requireNotNull(cold.history)
        assertEquals(review, coldHistory.head(a.id).review)
        assertEquals(Currency.CURRENT, cold.currency().getValue(a.id).state)
        assertEquals(Currency.AFFECTED, cold.currency().getValue(b.id).state)
        assertEquals(receiptFiles, NativeFiles.regularFiles(ledger.root, ".agents/receipts"))
        assertTrue(beforeRevision.history!!.revisions.all { (id, value) -> coldHistory.revisions[id] == value })
        val afterFiles = NativeFiles.regularFiles(ledger.root, ".agents/history")
        assertFailsWith<RevisionConflict> { ledger.apply(inspected.revision, Transition.ReconcileTask(review)) }
        assertEquals(afterFiles, NativeFiles.regularFiles(ledger.root, ".agents/history"))
    }

    @Test fun `existing code adoption preserves source and contributor instructions`() {
        val empty = create()
        val existing = directory.resolve("existing")
        Files.createDirectories(existing)
        Files.writeString(existing.resolve("app.txt"), "existing code\n")
        Files.writeString(existing.resolve("AGENTS.md"), "Keep this contributor contract.\n")
        val plan = Bootstrap.plan(existing, "existing", "0.2.0-alpha.1", directory.resolve("distribution"),
            Files.readString(empty.root.resolve(".taskctl/toolchain.lock")), adopt = true)
        assertFalse("AGENTS.md" in plan.files)
        assertEquals("existing code\n", Files.readString(existing.resolve("app.txt")))
        Bootstrap.apply(plan)
        assertEquals("Keep this contributor contract.\n", Files.readString(existing.resolve("AGENTS.md")))
        assertTrue(Files.exists(existing.resolve(".agents/README.md")))
        assertTrue(FileTaskLedger(existing).frontier().tasks.isEmpty())
        assertFails { Bootstrap.plan(existing, "existing", "0.2.0-alpha.1", directory.resolve("distribution"),
            Files.readString(empty.root.resolve(".taskctl/toolchain.lock")), adopt = true) }
    }

    @Test fun `explicit legacy tracking fences older writers without inventing observations`() {
        val template = create()
        val legacy = directory.resolve("legacy/.agents")
        Files.createDirectories(legacy.resolve("tasks"))
        Files.writeString(legacy.resolve("config.toml"), Files.readString(template.root.resolve(".agents/config.toml")).replace("taskctl.native/alpha2", "taskctl.native/alpha1"))
        Files.copy(template.root.resolve(".agents/policy.toml"), legacy.resolve("policy.toml"))
        val dependent = task.copy(id = TaskId.parseOrThrow("TASK.b"), requires = listOf(task.id))
        listOf(task, dependent).forEach { Files.writeString(legacy.resolve("tasks/${it.id.value}.json"), Json.encode(NativeCodec.task(it))) }
        val ledger = FileTaskLedger(legacy.parent)
        val before = ledger.snapshot()
        assertNull(before.history)
        ledger.plan(before.revision, Transition.TrackHistory)
        assertFalse(Files.exists(legacy.resolve("runtime")))
        ledger.apply(before.revision, Transition.TrackHistory)
        val after = FileTaskLedger(legacy.parent).snapshot()
        assertEquals(before.revision, after.history!!.origin)
        assertEquals(Currency.UNRESOLVED, after.currency().getValue(dependent.id).state)
        assertContains(Files.readString(legacy.resolve("config.toml")), "taskctl.native/alpha2")
        assertEquals("tasking/core-draft-1", after.universe.tasks.first().protocol)
    }

    @Test fun `recovery completes only matching preimages and rejects unrelated paths`() {
        val ledger = create()
        val path = ".agents/tasks/" + NativeFiles.fileName(task.id.value)
        val content = Json.encode(NativeCodec.task(task)) + "\n"
        val history = LedgerTransitions.evolve(ledger.snapshot(), Transition.AddRecords(listOf(task))).history!!
        val historyPath = ".agents/history/revisions/${history.heads.getValue(task.id).value.removePrefix("sha256:")}.json"
        val operation = obj("contract" to StringValue("taskctl.transaction/alpha1"),
            "before" to obj(path to NullValue, historyPath to NullValue,
                ".agents/history/heads.json" to StringValue(Files.readString(ledger.root.resolve(".agents/history/heads.json")))),
            "after" to obj(path to StringValue(content), historyPath to StringValue(Json.encode(HistoryCodec.revision(history.head(task.id))) + "\n"),
                ".agents/history/heads.json" to StringValue(Json.encode(HistoryCodec.heads(history)) + "\n")))
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
