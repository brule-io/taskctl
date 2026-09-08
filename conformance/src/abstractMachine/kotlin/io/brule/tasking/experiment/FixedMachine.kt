package io.brule.tasking.experiment

import io.brule.tasking.core.LegacyRecordedAt

import io.brule.tasking.core.ArrayValue
import io.brule.tasking.core.BooleanValue
import io.brule.tasking.core.ClosureEvidence
import io.brule.tasking.core.DraftLifecycle
import io.brule.tasking.core.DraftRecord
import io.brule.tasking.core.IntegerValue
import io.brule.tasking.core.Json
import io.brule.tasking.core.ObjectValue
import io.brule.tasking.core.Receipt
import io.brule.tasking.core.StringValue
import io.brule.tasking.core.TaskId
import io.brule.tasking.core.Transition
import io.brule.tasking.core.Value
import io.brule.tasking.core.obj
import io.brule.tasking.core.optionalString
import io.brule.tasking.core.strings
import io.brule.tasking.repository.FileTaskLedger
import java.math.BigInteger
import java.nio.file.Path
import java.time.Instant

// Laboratory only. No provider registration, expression evaluator or instruction loop.
const val MACHINE_NAMESPACE = "lab.minsky/v1"

@JvmInline
value class Label private constructor(val value: String) {
    companion object {
        fun parse(value: String): Label {
            require(Regex("L[A-Za-z0-9]+").matches(value)) { "invalid machine label" }
            return Label(value)
        }
    }
}

enum class Register { R0, R1 }

sealed interface Instruction {
    data class Inc(val register: Register, val next: Label) : Instruction
    data class DecJz(val register: Register, val nonzero: Label, val zero: Label) : Instruction
    data object Halt : Instruction
}

data class Frame(val pc: Label, val counter0: BigInteger, val counter1: BigInteger) {
    init { require(counter0.signum() >= 0 && counter1.signum() >= 0) { "counters must be natural integers" } }
    fun read(register: Register): BigInteger = when (register) { Register.R0 -> counter0; Register.R1 -> counter1 }
    fun write(register: Register, value: BigInteger, next: Label): Frame = when (register) {
        Register.R0 -> copy(pc = next, counter0 = value)
        Register.R1 -> copy(pc = next, counter1 = value)
    }
}

data class Machine(val program: Map<Label, Instruction>, val frame: Frame) {
    init {
        require(frame.pc in program && program.isNotEmpty()) { "continuation absent from program" }
        program.values.forEach { instruction ->
            val targets = when (instruction) {
                is Instruction.Inc -> listOf(instruction.next)
                is Instruction.DecJz -> listOf(instruction.nonzero, instruction.zero)
                Instruction.Halt -> emptyList()
            }
            require(targets.all { it in program }) { "branch target absent from program" }
        }
    }
}

data class Step(val next: Frame?, val branch: String)

/** The entire execution unit: one lookup, +/-1, zero test, or halt. */
fun executeOne(machine: Machine): Step {
    val frame = machine.frame
    return when (val instruction = machine.program.getValue(frame.pc)) {
        is Instruction.Inc -> Step(frame.write(instruction.register, frame.read(instruction.register) + BigInteger.ONE, instruction.next), "next")
        is Instruction.DecJz -> if (frame.read(instruction.register) == BigInteger.ZERO) {
            Step(frame.copy(pc = instruction.zero), "zero")
        } else {
            Step(frame.write(instruction.register, frame.read(instruction.register) - BigInteger.ONE, instruction.nonzero), "nonzero")
        }
        Instruction.Halt -> Step(null, "halt")
    }
}

object MachineCodec {
    private fun objectAt(value: Value?, keys: Set<String>): ObjectValue {
        val result = value as? ObjectValue ?: error("machine object required")
        require(result.fields.keys == keys) { "unexpected machine fields" }
        return result
    }
    fun frame(frame: Frame): ObjectValue = obj("pc" to StringValue(frame.pc.value),
        "counter0" to IntegerValue(frame.counter0), "counter1" to IntegerValue(frame.counter1))

    fun instruction(instruction: Instruction): ObjectValue = when (instruction) {
        is Instruction.Inc -> obj("op" to StringValue("INC"), "register" to StringValue(instruction.register.name), "next" to StringValue(instruction.next.value))
        is Instruction.DecJz -> obj("op" to StringValue("DECJZ"), "register" to StringValue(instruction.register.name),
            "nonzero" to StringValue(instruction.nonzero.value), "zero" to StringValue(instruction.zero.value))
        Instruction.Halt -> obj("op" to StringValue("HALT"))
    }

    fun encode(machine: Machine): ObjectValue = obj("program" to ObjectValue(machine.program.entries.associate {
        it.key.value to instruction(it.value)
    }), "frame" to frame(machine.frame))

    fun decode(value: Value?): Machine {
        val root = objectAt(value, setOf("program", "frame"))
        val frame = objectAt(root.fields["frame"], setOf("pc", "counter0", "counter1"))
        fun natural(key: String): BigInteger = (frame.fields[key] as? IntegerValue)?.value
            ?.also { require(it.signum() >= 0) { "negative machine counter" } } ?: error("integer machine counter required")
        val program = root.fields["program"] as? ObjectValue ?: error("program mapping required")
        return Machine(program.fields.entries.associate { (label, raw) ->
            val instruction = raw as? ObjectValue ?: error("instruction object required")
            val op = instruction.requiredString("op")
            val keys = when (op) {
                "INC" -> setOf("op", "register", "next")
                "DECJZ" -> setOf("op", "register", "nonzero", "zero")
                "HALT" -> setOf("op")
                else -> error("unknown machine instruction")
            }
            objectAt(instruction, keys)
            Label.parse(label) to when (op) {
                "INC" -> Instruction.Inc(Register.valueOf(instruction.requiredString("register")), Label.parse(instruction.requiredString("next")))
                "DECJZ" -> Instruction.DecJz(Register.valueOf(instruction.requiredString("register")),
                    Label.parse(instruction.requiredString("nonzero")), Label.parse(instruction.requiredString("zero")))
                else -> Instruction.Halt
            }
        }, Frame(Label.parse(frame.requiredString("pc")), natural("counter0"), natural("counter1")))
    }
}

fun machineTask(id: TaskId, machine: Machine, predecessor: TaskId? = null): DraftRecord = DraftRecord(
    id, "Execute one fixed machine instruction", "open", "Execute the instruction in this immutable revision's frame once.",
    listOfNotNull(predecessor), listOf("Use only INC, DECJZ or HALT; preserve the immutable program."),
    listOf("Record the input revision and prepared successor revision, or HALT, as actor evidence."),
    emptyList(), obj(MACHINE_NAMESPACE to MachineCodec.encode(machine)), "tasking/core-draft-2", listOf("machine-step"),
)

/** Every invocation reconstructs all authority from a cold ledger snapshot. */
fun stepLedger(ledger: FileTaskLedger, prepareOnly: Boolean = false): ObjectValue {
    val initial = ledger.snapshot()
    val issue = ledger.frontier()
    require(issue.revision == initial.revision) { "snapshot changed; retry" }
    require(issue.tasks.size == 1) { "scalar machine requires exactly one ready task" }
    val current = issue.tasks.single()
    val history = requireNotNull(initial.history)
    val input = history.head(current)
    // Core treats optional values as annotations. Do not let projection drift
    // silently replace the full immutable revision selected as machine authority.
    initial.universe.tasks.forEach { task ->
        require(task.extensions == history.head(task.id).record.extensions) { "machine projection differs from immutable HEAD" }
    }
    val machine = MachineCodec.decode(input.record.extensions.fields[MACHINE_NAMESPACE])
    val step = executeOne(machine)
    val events = mutableListOf<Value>()
    var state = initial
    fun commit(phase: String, transition: Transition) {
        val plan = ledger.plan(state.revision, transition)
        val result = ledger.apply(state.revision, transition)
        val frontier = ledger.frontier()
        events += obj("phase" to StringValue(phase), "expected_revision" to StringValue(state.revision.value),
            "result_revision" to StringValue(result.revision.value), "frontier" to strings(frontier.tasks.map { it.value }), "plan" to plan)
        state = ledger.snapshot()
        check(result.revision == state.revision && frontier.revision == state.revision)
    }
    val prepared = if (step.next != null) {
        // Allocation serial is not a program-visible register. No PC or frame
        // comes from this allocator, the invocation count, or the test driver.
        require(current.value.startsWith("TASK.machine.step."))
        val serial = current.value.removePrefix("TASK.machine.step.").toBigInteger()
        val nextId = TaskId.parseOrThrow("TASK.machine.step.${serial + BigInteger.ONE}")
        val nextTask = machineTask(nextId, machine.copy(frame = step.next), current)
        val existing = initial.universe.tasks.singleOrNull { it.id == nextId }
        require(initial.universe.tasks.filter { it.state == "open" }.all { it.id == current || it.id == nextId }) { "unexpected pending machine work" }
        if (existing == null) {
            commit("prepare", Transition.AddRecords(tasks = listOf(nextTask)))
        } else {
            require(existing == nextTask) { "prepared successor does not match the recomputed instruction" }
        }
        check(ledger.frontier().tasks == listOf(current)) { "preparation changed the issue location" }
        requireNotNull(state.history).head(nextId)
    } else {
        require(initial.universe.tasks.filter { it.state == "open" }.map { it.id } == listOf(current)) { "HALT has pending work" }
        null
    }
    if (!prepareOnly || prepared == null) {
        val assertion = Json.encode(obj("input_revision" to StringValue(input.id.value), "branch" to StringValue(step.branch),
            "successor_revision" to optionalString(prepared?.id?.value), "halt" to BooleanValue(step.next == null)))
        val receipt = Receipt(current, DraftLifecycle.contract(input.record), mapOf("machine-step" to assertion))
        commit("retire", Transition.CloseTask(ClosureEvidence(receipt, "fixed-minsky-stepper/lab1", LegacyRecordedAt.parseOrThrow(Instant.now().toString()))))
        check(ledger.frontier().tasks == listOfNotNull(prepared?.record?.id))
    }
    return obj("input_ledger_revision" to StringValue(initial.revision.value), "input_task" to StringValue(current.value),
        "input_revision" to StringValue(input.id.value), "frontier_before" to strings(issue.tasks.map { it.value }),
        "frame" to MachineCodec.frame(machine.frame), "instruction" to MachineCodec.instruction(machine.program.getValue(machine.frame.pc)),
        "branch" to StringValue(step.branch), "successor_task" to optionalString(prepared?.record?.id?.value),
        "successor_revision" to optionalString(prepared?.id?.value), "prepared_only" to BooleanValue(prepareOnly && prepared != null),
        "cas" to ArrayValue(events), "frontier_after" to strings(ledger.frontier().tasks.map { it.value }))
}

/** CLI entry point executes exactly ONE instruction. No fuel/program loop here. */
fun main(args: Array<String>) {
    require(args.size in 1..2 && (args.size == 1 || args[1] == "prepare-only"))
    println(Json.encode(stepLedger(FileTaskLedger(Path.of(args[0])), args.size == 2)))
}
