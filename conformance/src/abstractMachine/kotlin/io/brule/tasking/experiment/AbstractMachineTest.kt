package io.brule.tasking.experiment

import io.brule.tasking.core.ArrayValue
import io.brule.tasking.core.BooleanValue
import io.brule.tasking.core.Canonical
import io.brule.tasking.core.ClosureEvidence
import io.brule.tasking.core.Currency
import io.brule.tasking.core.DraftLifecycle
import io.brule.tasking.core.DraftRecord
import io.brule.tasking.core.HistoryCodec
import io.brule.tasking.core.Json
import io.brule.tasking.core.NativeCodec
import io.brule.tasking.core.ObjectValue
import io.brule.tasking.core.Receipt
import io.brule.tasking.core.RevisionConflict
import io.brule.tasking.core.StringValue
import io.brule.tasking.core.TaskId
import io.brule.tasking.core.Transition
import io.brule.tasking.core.YamlValues
import io.brule.tasking.core.currency
import io.brule.tasking.core.integer
import io.brule.tasking.core.obj
import io.brule.tasking.core.stringMap
import io.brule.tasking.core.strings
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Harness controls bounded fuel and fault injection, never machine control flow. */
class AbstractMachineTest {
    @TempDir lateinit var directory: Path
    private val source = Path.of(System.getProperty("tasking.root"))
    private val output = Path.of(System.getProperty("tasking.machine.output"))
    private val first = TaskId.parseOrThrow("TASK.machine.step.0")
    private fun label(text: String) = Label.parse(text)
    private fun frame(pc: String, r0: Int, r1: Int) = Frame(label(pc), r0.toBigInteger(), r1.toBigInteger())
    private fun haltMachine() = Machine(mapOf(label("LH") to Instruction.Halt), frame("LH", 0, 0))
    private fun transfer(r0: BigInteger = 5.toBigInteger()): Machine = Machine(mapOf(
        label("L0") to Instruction.DecJz(Register.R0, label("L1"), label("LH")),
        label("L1") to Instruction.Inc(Register.R1, label("L0")),
        label("LH") to Instruction.Halt,
    ), Frame(label("L0"), r0, 2.toBigInteger()))

    private fun create(name: String, tasks: List<DraftRecord>): FileTaskLedger {
        val lock = Files.readString(source.resolve(".taskctl/toolchain.lock"))
        val version = lock.lineSequence().first { it.startsWith("toolVersion=") }.substringAfter('=')
        val root = directory.resolve(name)
        val distribution = directory.resolve("$name-distribution")
        Files.createDirectories(distribution.resolve("bootstrap"))
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach {
            Files.copy(source.resolve(it), distribution.resolve("bootstrap/$it"))
        }
        Bootstrap.apply(Bootstrap.plan(root, "lab.$name", version, distribution, lock, Transition.AddRecords(tasks)))
        // Sentinels test the execution write boundary, including a Git-shaped path.
        Files.createDirectories(root.resolve(".git"))
        Files.writeString(root.resolve(".git/HEAD"), "ref: refs/heads/laboratory-sentinel\n")
        Files.writeString(root.resolve("unrelated-source.txt"), "must remain unchanged\n")
        return FileTaskLedger(root)
    }

    private data class FileStamp(val digest: String, val modified: String)
    private fun objectValue(source: String): ObjectValue = YamlValues.parse(source).value as? ObjectValue ?: error("object required")
    private fun taskPath(ledger: FileTaskLedger): Path = Files.list(ledger.root.resolve(".agents/tasks")).use { it.findFirst().orElseThrow() }
    private fun inventory(root: Path): Map<String, FileStamp> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) }.toList().associate { path ->
            root.relativize(path).toString().replace('\\', '/') to FileStamp(Canonical.sha256(Files.readAllBytes(path)), Files.getLastModifiedTime(path).toString())
        }
    }
    private fun readOnly(ledger: FileTaskLedger) {
        val before = inventory(ledger.root)
        val snapshot = ledger.snapshot()
        ledger.frontier()
        snapshot.universe.tasks.forEach { ledger.task(it.id) }
        assertEquals(before, inventory(ledger.root), "reads modified file bytes/mtime")
    }
    private fun boundedAppend(before: Map<String, FileStamp>, after: Map<String, FileStamp>) {
        assertTrue(after.keys.containsAll(before.keys), "execution removed a file")
        before.forEach { (path, stamp) ->
            if (!path.startsWith(".agents/") || path.startsWith(".agents/history/revisions/") || path.startsWith(".agents/receipts/")) {
                assertEquals(stamp, after.getValue(path), "immutable or unrelated file changed: $path")
            }
        }
        (after.keys - before.keys).forEach { path ->
            assertTrue(path.startsWith(".agents/tasks/") || path.startsWith(".agents/history/") || path.startsWith(".agents/receipts/") || path.startsWith(".agents/runtime/"), path)
        }
    }

    private fun invoke(ledger: FileTaskLedger, prepareOnly: Boolean = false): ObjectValue {
        readOnly(ledger)
        val before = inventory(ledger.root)
        val log = Files.createTempFile(directory, "step-", ".json")
        val errors = Files.createTempFile(directory, "step-", ".stderr")
        val executable = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val command = listOf(executable.toString(), "-cp", System.getProperty("tasking.machine.classpath"), "io.brule.tasking.experiment.FixedMachineKt", ledger.root.toString()) + if (prepareOnly) listOf("prepare-only") else emptyList()
        val builder = ProcessBuilder(command).directory(directory.toFile()).redirectOutput(log.toFile()).redirectError(errors.toFile())
        listOf("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS").forEach { builder.environment().remove(it) }
        val process = builder.start()
        if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); error("one-step process timed out") }
        assertEquals(0, process.exitValue(), Files.readString(errors))
        val result = objectValue(Files.readString(log))
        boundedAppend(before, inventory(ledger.root))
        readOnly(ledger)
        return result
    }

    private fun save(name: String, value: ObjectValue) {
        Files.createDirectories(output)
        Files.writeString(output.resolve("$name.json"), Json.encode(value) + "\n")
    }
    private fun archive(ledger: FileTaskLedger): ObjectValue {
        val history = requireNotNull(ledger.snapshot().history)
        return obj("heads" to HistoryCodec.heads(history),
            "revisions" to ObjectValue(history.revisions.entries.sortedBy { it.key.value }.associate { it.key.value to HistoryCodec.revision(it.value) }),
            "receipts" to ArrayValue(ledger.snapshot().receipts.map(NativeCodec::evidence)),
            "file_sha256" to stringMap(inventory(ledger.root).mapValues { it.value.digest }))
    }

    private fun run(name: String, machine: Machine, expected: Frame, steps: Int): List<ObjectValue> {
        val ledger = create(name, listOf(machineTask(first, machine)))
        val initial = ledger.snapshot()
        val trace = mutableListOf<ObjectValue>()
        // Bounded driver: no opcode inspection, arithmetic, branch evaluation or next-PC selection.
        repeat(steps) { trace += invoke(ledger) }
        assertTrue(ledger.frontier().tasks.isEmpty())
        assertEquals("halt", trace.last().requiredString("branch"))
        val snapshot = ledger.snapshot()
        val history = requireNotNull(snapshot.history)
        assertEquals(steps, snapshot.universe.tasks.size)
        assertEquals(steps, snapshot.receipts.size)
        assertEquals(steps * 2, history.revisions.size)
        assertTrue(snapshot.universe.tasks.all { it.state == "closed" })
        assertTrue(snapshot.currency().values.all { it.state == Currency.CURRENT })
        assertTrue(DraftLifecycle.evaluate(snapshot.universe.tasks).problems.isEmpty())
        assertEquals(expected, MachineCodec.decode(history.head(TaskId.parseOrThrow(trace.last().requiredString("input_task"))).record.extensions.fields[MACHINE_NAMESPACE]).frame)
        trace.forEachIndexed { index, step ->
            assertEquals(listOf(StringValue(step.requiredString("input_task"))), step.requiredArray("frontier_before"))
            assertEquals(if (index == steps - 1) 1 else 2, step.requiredArray("cas").size)
            val task = history.head(TaskId.parseOrThrow(step.requiredString("input_task")))
            assertEquals(if (index == 0) emptyList() else listOf(TaskId.parseOrThrow(trace[index - 1].requiredString("input_task"))), task.record.requires)
            assertEquals(step.requiredString("input_revision"), task.parent?.value)
            if (index > 0) {
                // The successor observed the open predecessor revision. Retirement
                // changes its HEAD, not its contract/inputs; currency remains current.
                assertEquals(trace[index - 1].requiredString("input_revision"), task.dependencies.single().observedRevision?.value)
            }
        }
        save(name, obj("source_commit" to StringValue("54603099da2fbb33eede4011f4fb8cf45a9ef48f"),
            "initial_machine" to MachineCodec.encode(machine), "initial_ledger_revision" to StringValue(initial.revision.value),
            "fresh_process_per_step" to BooleanValue(true), "steps" to ArrayValue(trace),
            "final_frame" to MachineCodec.frame(expected), "history" to archive(ledger)))
        return trace
    }

    @Test fun `transfer unrolls twelve instruction instances across fresh processes`() {
        val trace = run("transfer", transfer(), frame("LH", 0, 7), 12)
        assertEquals(5, trace.count { it.requiredString("branch") == "nonzero" })
        assertEquals(1, trace.count { it.requiredString("branch") == "zero" })
    }

    @Test fun `branch heavy machine exercises both registers and both DECJZ outcomes`() {
        val machine = Machine(mapOf(
            label("L0") to Instruction.DecJz(Register.R0, label("L1"), label("L4")),
            label("L1") to Instruction.DecJz(Register.R1, label("L2"), label("L3")),
            label("L2") to Instruction.Inc(Register.R0, label("L0")),
            label("L3") to Instruction.Inc(Register.R1, label("L0")),
            label("L4") to Instruction.Halt,
        ), frame("L0", 2, 2))
        val trace = run("branch-heavy", machine, frame("L4", 0, 1), 17)
        assertEquals(3, trace.count { it.requiredString("branch") == "zero" })
        assertEquals(8, trace.count { it.requiredString("branch") == "nonzero" })
    }

    @Test fun `prepared successor survives process exit and stale CAS cannot duplicate arithmetic`() {
        val huge = BigInteger("9".repeat(80))
        val machine = Machine(mapOf(label("L0") to Instruction.Inc(Register.R0, label("LH")), label("LH") to Instruction.Halt), Frame(label("L0"), huge, BigInteger.TWO))
        val ledger = create("resume", listOf(machineTask(first, machine)))
        val stale = ledger.snapshot()
        val prepared = invoke(ledger, prepareOnly = true)
        assertEquals(listOf(first), ledger.frontier().tasks)
        val beforeConflict = inventory(ledger.root)
        val conflict = assertFailsWith<RevisionConflict> {
            ledger.apply(stale.revision, Transition.AddRecords(listOf(machineTask(TaskId.parseOrThrow("TASK.machine.step.1"), machine, first))))
        }
        assertEquals(beforeConflict, inventory(ledger.root))
        val resumed = invoke(ledger)
        assertEquals(1, resumed.requiredArray("cas").size)
        val halted = invoke(ledger)
        val final = MachineCodec.decode(requireNotNull(ledger.snapshot().history).head(TaskId.parseOrThrow("TASK.machine.step.1")).record.extensions.fields[MACHINE_NAMESPACE]).frame
        assertEquals(huge + BigInteger.ONE, final.counter0)
        assertEquals(4, requireNotNull(ledger.snapshot().history).revisions.size)
        save("resume-cas", obj("initial_machine" to MachineCodec.encode(machine), "prepared" to prepared,
            "stale_expected_revision" to StringValue(stale.revision.value), "rejected" to StringValue(requireNotNull(conflict.message)),
            "resumed" to resumed, "halted" to halted, "final_frame" to MachineCodec.frame(final), "history" to archive(ledger)))
    }

    @Test fun `optional payload is revision bound but not contract bound or a core arithmetic proof`() {
        val task = machineTask(first, haltMachine())
        val ledger = create("optional", listOf(task))
        val original = ledger.snapshot()
        val changed = task.copy(extensions = obj(MACHINE_NAMESPACE to MachineCodec.encode(haltMachine().copy(frame = frame("LH", 999, 0)))))
        val path = taskPath(ledger)
        Files.writeString(path, Json.encode(NativeCodec.task(changed)) + "\n")
        val drift = ledger.snapshot() // Core deliberately permits optional annotation drift.
        assertEquals(DraftLifecycle.contract(task), DraftLifecycle.contract(changed))
        assertEquals(original.history, drift.history)
        assertEquals(changed.extensions, ledger.task(first)?.extensions)
        val before = inventory(ledger.root)
        val rejection = assertFails { stepLedger(ledger) }
        assertContains(requireNotNull(rejection.message), "projection differs")
        assertEquals(before, inventory(ledger.root))
        ledger.apply(drift.revision, Transition.ReviseTask(changed))
        val revised = ledger.snapshot()
        assertNotEquals(original.history?.heads?.get(first), revised.history?.heads?.get(first))
        assertEquals(Currency.CURRENT, revised.currency().getValue(first).state)
        // A named nonblank assertion is sufficient to core; no machine provider
        // verifies the arithmetic. This negative control is never a program run.
        val evidence = ClosureEvidence(Receipt(first, DraftLifecycle.contract(changed), mapOf("machine-step" to "deliberately unverified negative control")), "lab-negative-control", "2026-09-07T00:00:00Z")
        ledger.apply(revised.revision, Transition.CloseTask(evidence))
        assertTrue(ledger.frontier().tasks.isEmpty())
        save("optional-authority", obj("original_contract" to StringValue(DraftLifecycle.contract(task).value),
            "changed_contract" to StringValue(DraftLifecycle.contract(changed).value),
            "original_head" to StringValue(requireNotNull(original.history).heads.getValue(first).value),
            "revised_head" to StringValue(requireNotNull(revised.history).heads.getValue(first).value),
            "projection_rejection" to StringValue(requireNotNull(rejection.message)), "unverified_assertion_accepted_by_core" to BooleanValue(true), "history" to archive(ledger)))
    }

    @Test fun `required namespace without provider blocks execution and closure`() {
        val task = machineTask(first, haltMachine()).copy(requiredExtensions = listOf(MACHINE_NAMESPACE))
        val ledger = create("required", listOf(task))
        val evaluation = DraftLifecycle.evaluate(ledger.snapshot().universe.tasks)
        assertTrue(evaluation.problems.any { "required semantic provider unavailable" in it })
        assertTrue(evaluation.frontier.isEmpty())
        val before = inventory(ledger.root)
        val frontierError = assertFails { ledger.frontier() }
        val closeError = assertFails { ledger.apply(ledger.snapshot().revision, Transition.CloseTask(ClosureEvidence(
            Receipt(first, DraftLifecycle.contract(task), mapOf("machine-step" to "asserted")), "tester", "2026-09-07T00:00:00Z"))) }
        assertEquals(before, inventory(ledger.root))
        save("required-provider", obj("problems" to strings(evaluation.problems), "frontier_error" to StringValue(requireNotNull(frontierError.message)), "close_error" to StringValue(requireNotNull(closeError.message))))
    }

    @Test fun `cycle rejection and multiple frontier require no hidden scheduler`() {
        val ledger = create("multiple", listOf(machineTask(first, haltMachine()), machineTask(TaskId.parseOrThrow("TASK.independent"), haltMachine())))
        val before = inventory(ledger.root)
        assertEquals(2, ledger.frontier().tasks.size)
        val scalarError = assertFails { stepLedger(ledger) }
        val a = TaskId.parseOrThrow("TASK.cycle.a")
        val b = TaskId.parseOrThrow("TASK.cycle.b")
        val cycleError = assertFails { ledger.apply(ledger.snapshot().revision, Transition.AddRecords(listOf(machineTask(a, haltMachine(), b), machineTask(b, haltMachine(), a)))) }
        assertEquals(before, inventory(ledger.root))
        save("cycle-multifrontier", obj("frontier" to strings(ledger.frontier().tasks.map { it.value }),
            "scalar_rejection" to StringValue(requireNotNull(scalarError.message)), "cycle_rejection" to StringValue(requireNotNull(cycleError.message))))
    }

    @Test fun `current decoder rejects a valid natural literal despite additional storage`() {
        val ledger = create("size-boundary", listOf(machineTask(first, haltMachine())))
        val path = taskPath(ledger)
        val original = Files.readString(path)
        assertContains(original, "\"counter0\":0")
        val oversized = original.replace("\"counter0\":0", "\"counter0\":" + "1" + "0".repeat(1_000_000))
        Files.writeString(path, oversized)
        val before = inventory(ledger.root)
        val failure = assertFails { ledger.snapshot() }
        assertContains(requireNotNull(failure.message), "1000000 code points")
        assertEquals(before, inventory(ledger.root))
        save("storage-boundary", obj("valid_natural_literal_digits" to integer(1_000_001), "document_characters" to integer(oversized.length),
            "document_sha256" to StringValue(Canonical.sha256(oversized.toByteArray(Charsets.UTF_8))), "error" to StringValue(requireNotNull(failure.message)),
            "read_mutated_files" to BooleanValue(false)))
    }

    @Test fun `frame decoder rejects negative fractional and unknown instruction payloads`() {
        val valid = MachineCodec.encode(haltMachine())
        val negative = objectValue(Json.encode(valid).replace("\"counter0\":0", "\"counter0\":-1"))
        val fractional = objectValue(Json.encode(valid).replace("\"counter0\":0", "\"counter0\":0.5"))
        val unknown = objectValue(Json.encode(valid).replace("HALT", "EVAL"))
        listOf(negative, fractional, unknown).forEach { assertFails { MachineCodec.decode(it) } }
        assertEquals(haltMachine(), MachineCodec.decode(valid))
        assertFalse("EVAL" in Json.encode(valid))
    }
}
