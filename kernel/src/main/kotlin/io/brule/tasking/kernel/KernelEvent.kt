package io.brule.tasking.kernel

import io.brule.tasking.core.*
import io.brule.tasking.kernel.KernelCodec.exact
import io.brule.tasking.kernel.KernelCodec.nullableText
import io.brule.tasking.kernel.KernelCodec.objectAt
import java.math.BigInteger

@JvmInline
value class KernelEventId private constructor(val value: String) {
    init { require(PATTERN.matches(value)) { "invalid kernel event identity" } }
    companion object {
        private val PATTERN = Regex("sha256:[0-9a-f]{64}")
        fun parse(value: String): KernelEventId? = if (PATTERN.matches(value)) KernelEventId(value) else null
        fun parseOrThrow(value: String): KernelEventId = parse(value) ?: error("invalid kernel event identity")
    }
}

/** Acceptance is storage metadata, never substituted for an actor occurrence. */
data class KernelEvent(val sequence: Long, val parent: KernelEventId?, val before: Revision,
                       val transition: Transition, val result: TransitionResult) {
    init { require(sequence > 0 && (sequence == 1L) == (parent == null) && result.acceptedAt != null) { "invalid acceptance event" } }
    val id: KernelEventId get() = KernelEventId.parseOrThrow(Canonical.digest("taskctl.kernel-event/alpha1", encode()))
    fun encode(): ObjectValue = obj("protocol" to StringValue("taskctl.kernel-event/alpha1"),
        "sequence" to IntegerValue(BigInteger.valueOf(sequence)), "parent" to optionalString(parent?.value), "before" to StringValue(before.value),
        "transition" to KernelCodec.transition(transition), "result" to KernelCodec.result(result))
    companion object {
        fun decode(value: ObjectValue): KernelEvent {
            value.exact("protocol", "sequence", "parent", "before", "transition", "result")
            require(value.requiredString("protocol") == "taskctl.kernel-event/alpha1") { "unsupported kernel event" }
            val sequence = (value.fields["sequence"] as? IntegerValue)?.value?.longValueExact() ?: error("event sequence integer required")
            return KernelEvent(sequence, value.nullableText("parent")?.let(KernelEventId::parseOrThrow),
                Revision.parseOrThrow(value.requiredString("before")), KernelCodec.decodeTransition(value.objectAt("transition")), KernelCodec.decodeResult(value.objectAt("result")))
        }
    }
}

internal fun changed(transition: Transition, before: LedgerSnapshot): List<RecordId> = when (transition) {
    is Transition.AddRecords -> transition.tasks.map { it.id } + transition.roadmaps.map { it.id } + transition.epics.map { it.id }
    is Transition.CloseTask -> listOf(transition.evidence.receipt.taskId)
    is Transition.ReviseTask -> listOf(transition.record.id)
    is Transition.ReconcileTask -> listOf(transition.review.task)
    Transition.TrackHistory -> before.universe.tasks.map { it.id }
    is Transition.ImportRecords -> transition.admission.manifest.universe.let { universe -> universe.tasks.map { it.id } + universe.planningRecords.map { it.id } }
    Transition.TrackPlanning -> before.universe.planningRecords.map { it.id }
    is Transition.AmendPlanning -> listOf(transition.amendment.record.id)
    is Transition.SetPlanningDisposition -> listOf(transition.change.planning)
    is Transition.AssessPlanning -> listOf(transition.assessment.planning)
    is Transition.SetProfile -> error("kernel supports only the unadopted minimal profile")
}.sortedBy { it.value }
