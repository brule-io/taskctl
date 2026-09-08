package io.brule.tasking.kernel

import io.brule.tasking.core.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class KernelCodecTest {
    private val id = TaskId.parseOrThrow("TASK.kernel.a")
    private fun task() = DraftRecord(id, "Task", "open", "Bounded intent", emptyList(), listOf("Durable"), listOf("Observed"), emptyList(),
        obj("opaque.values/v1" to obj("integer" to IntegerValue(BigInteger("999999999999999999999999999999999999")),
            "whole" to DecimalValue(BigDecimal("1E+30")), "scaled" to DecimalValue(BigDecimal("1.2300")), "array" to ArrayValue(listOf(NullValue, BooleanValue(true))))),
        "tasking/core-draft-2", listOf("test"))
    private fun current(value: LedgerSnapshot): LedgerSnapshot = value.copy(revision = KernelCodec.revision(value))

    @Test fun `snapshot and transition envelopes preserve exact typed values and old history`() {
        val before = LedgerSnapshot("kernel.test", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial()))
        val seed = Transition.AddRecords(listOf(task()))
        assertEquals(seed, KernelCodec.decodeTransition(KernelCodec.transition(seed)))
        var snapshot = current(LedgerTransitions.evolve(before, seed))
        val evidence = ClosureEvidence(Receipt(id, DraftLifecycle.contract(task()), mapOf("test" to "Observed")), "reviewer", LegacyRecordedAt.parseOrThrow("historical non-timestamp"))
        val close = Transition.CloseTask(evidence)
        assertEquals(close, KernelCodec.decodeTransition(KernelCodec.transition(close)))
        snapshot = current(LedgerTransitions.evolve(snapshot, close))
        val bytes = Json.encode(KernelCodec.snapshot(snapshot)).toByteArray(Charsets.UTF_8)
        assertEquals(snapshot, KernelCodec.decodeSnapshot(KernelCodec.parse(bytes)))
        assertEquals(evidence, snapshot.receipts.single())
        val request = KernelCodec.request(snapshot.revision, Transition.TrackPlanning)
        assertEquals(snapshot.revision to Transition.TrackPlanning, KernelCodec.decodeRequest(KernelCodec.parse(Json.encode(request).toByteArray())))
        assertEquals(Transition.TrackHistory, KernelCodec.decodeTransition(KernelCodec.transition(Transition.TrackHistory)))
    }

    @Test fun `strict envelopes reject unknown fields noncanonical JSON malformed UTF8 and forged revisions`() {
        val snapshot = current(LedgerSnapshot("kernel.test", Revision.initial(), DraftUniverse(emptyList())))
        val value = KernelCodec.snapshot(snapshot)
        assertFails { KernelCodec.decodeSnapshot(ObjectValue(value.fields + ("banana" to NullValue))) }
        assertFails { KernelCodec.decodeSnapshot(ObjectValue(value.fields + ("revision" to StringValue("sha256:" + "0".repeat(64))))) }
        assertFails { KernelCodec.parse("{\"protocol\": \"extra whitespace\"}".toByteArray()) }
        assertFails { KernelCodec.parse("protocol: taskctl.kernel-state/alpha1".toByteArray()) }
        assertFails { KernelCodec.parse(byteArrayOf(0xC3.toByte(), 0x28)) }
        assertFails { KernelCodec.parse(ByteArray(KernelCodec.MAX_BODY_BYTES + 1)) }
        assertFails { KernelCodec.decodeRequest(ObjectValue(KernelCodec.request(snapshot.revision, Transition.TrackHistory).fields + ("accepted_at" to StringValue("2026-09-08T00:00:00Z")))) }
        assertFails { KernelCodec.decodeTransition(obj("protocol" to StringValue("taskctl.kernel-transition/alpha1"), "kind" to StringValue("track_history"), "data" to obj())) }
    }

    @Test fun `effective profiles are refused explicitly and event identities cross validated boundaries`() {
        val audit = ProfileAudit("reviewer", OccurredAt.parseOrThrow("2026-09-08T00:00:00Z"), "Reason", mapOf("test" to "Evidence"))
        val profile = EffectiveProfile(ProfileId.parseOrThrow("kernel.test/v1"), emptyMap())
        val snapshot = LedgerSnapshot("kernel.test", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial()))
        val change = Transition.SetProfile(ProfileChange(null, profile, audit))
        assertFails { KernelCodec.transition(change) }
        assertFails { KernelCodec.state(LedgerTransitions.evolve(snapshot, change)) }
        assertNull(KernelEventId.parse("banana"))
        val result = TransitionResult(snapshot.revision, emptyList(), AcceptedAt.parseOrThrow("2026-09-08T01:00:00Z"))
        val event = KernelEvent(1, null, snapshot.revision, Transition.TrackPlanning, result)
        assertEquals(event, KernelEvent.decode(event.encode()))
        assertNotEquals(event.id, event.copy(result = result.copy(acceptedAt = AcceptedAt.parseOrThrow("2026-09-08T01:00:01Z"))).id)
        assertFails { event.copy(result = result.copy(acceptedAt = null)) }
        assertFails { event.copy(sequence = 2) }
        assertFails { event.copy(sequence = 0) }
        val encoded = KernelCodec.result(result)
        assertFails { KernelCodec.decodeResult(ObjectValue(encoded.fields + ("accepted_at" to NullValue))) }
        assertFails { KernelCodec.result(result.copy(acceptedAt = null)) }
    }
}
