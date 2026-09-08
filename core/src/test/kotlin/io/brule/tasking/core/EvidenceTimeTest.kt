package io.brule.tasking.core

import java.time.Instant
import kotlin.test.*

class EvidenceTimeTest {
    private val occurred = "2026-09-07T16:15:00.123456789-07:00"
    private fun fixture(name: String): ObjectValue = requireNotNull(javaClass.getResourceAsStream("/evidence-time/$name.json"))
        .bufferedReader(Charsets.UTF_8).use { YamlValues.parse(it.readText()).value as ObjectValue }
    private data class Envelope(val name: String, val protocol: String,
                                val roundTrip: (ObjectValue) -> ObjectValue, val time: (ObjectValue) -> AssertionTime)
    private val envelopes = listOf(
        Envelope("receipt", "taskctl.receipt/alpha2", { NativeCodec.evidence(NativeCodec.decodeEvidence(it)) }, { NativeCodec.decodeEvidence(it).time }),
        Envelope("review", "taskctl.reconciliation/2", { HistoryCodec.review(HistoryCodec.decodeReview(it)) }, { HistoryCodec.decodeReview(it).time }),
        Envelope("import_review", "taskctl.import-review/2", { ImportCodec.review(ImportCodec.decodeReview(it)) }, { ImportCodec.decodeReview(it).time }),
    )
    private fun current(envelope: Envelope): ObjectValue = ObjectValue(fixture(envelope.name).fields - "recorded_at" + mapOf(
        "protocol" to StringValue(envelope.protocol), "occurred_at" to StringValue(occurred)))

    @Test fun `validated nominal times preserve explicit offset and nanosecond spelling`() {
        for (source in listOf(occurred, "0001-01-01T00:00:00Z", "9999-12-31T23:59:59+18:00",
            "2024-02-29T23:59:59.000000001-18:00", "2026-09-07T23:15:00+00:00")) {
            assertEquals(source, OccurredAt.parseOrThrow(source).value)
            assertEquals(source, AcceptedAt.parseOrThrow(source).value)
        }
        assertEquals(Instant.parse("2026-09-07T23:15:00.123456789Z"), OccurredAt.parseOrThrow(occurred).toInstant())
        val supplied = Instant.parse("2026-09-07T23:16:00.000001Z")
        assertEquals(supplied, AcceptedAt.fromInstant(supplied).toInstant())
    }

    @Test fun `ambiguous invalid and lossy timestamp forms cannot cross the new boundary`() {
        val invalid = listOf("", " ", "yesterday", "2026-09-07", "2026-09-07T23:15:00", "2026-09-07T23:15Z",
            "2026-09-07 23:15:00Z", "2026-09-07t23:15:00z", "2026-09-07T23:15:00-00:00", "2026-09-07T23:15:00+00",
            "2026-09-07T23:15:00+0000", "2026-09-07T23:15:00+18:01", "2026-09-07T23:15:00-19:00",
            "2026-09-07T23:15:00+07:60", "2026-09-07T23:15:60Z", "2026-09-07T24:00:00Z",
            "2026-02-29T00:00:00Z", "2026-13-01T00:00:00Z", "2026-09-31T00:00:00Z", "0000-01-01T00:00:00Z",
            "+10000-01-01T00:00:00Z", "2026-09-07T23:15:00.1234567890Z", "2026-09-07T23:15:00.Z",
            "2026-09-07T23:15:00Z\n", " 2026-09-07T23:15:00Z", "２０２６-09-07T23:15:00Z")
        for (source in invalid) {
            assertNull(OccurredAt.parse(source), source)
            assertNull(AcceptedAt.parse(source), source)
        }
        for (envelope in envelopes) for (source in invalid) {
            assertFails(source) { envelope.roundTrip(ObjectValue(current(envelope).fields + ("occurred_at" to StringValue(source)))) }
        }
    }

    @Test fun `old envelopes never silently upgrade even when their strings look like timestamps`() {
        for (envelope in envelopes) for (source in listOf(occurred, "  after lunch 🧬\nunknown timezone  ", "not-a-date")) {
            val old = ObjectValue(fixture(envelope.name).fields + ("recorded_at" to StringValue(source)))
            val time = envelope.time(old)
            assertIs<LegacyRecordedAt>(time)
            assertEquals(source, time.value)
            assertEquals(old, envelope.roundTrip(old))
        }
        assertNull(LegacyRecordedAt.parse("\n \t"))
        assertEquals("x\n ", LegacyRecordedAt.parseOrThrow("x\n ").value)
    }

    @Test fun `new envelopes reject storage authority spoofing mixed fields and unknown versions`() {
        for (envelope in envelopes) {
            val value = current(envelope)
            assertIs<OccurredAt>(envelope.time(value))
            assertEquals(value, envelope.roundTrip(value))
            assertEquals(occurred, envelope.time(value).value)
            for (extra in listOf("accepted_at", "recorded_at", "server_time")) {
                assertFails { envelope.roundTrip(ObjectValue(value.fields + (extra to StringValue(occurred)))) }
                val legacyExtra = if (extra == "recorded_at") "occurred_at" else extra
                assertFails { envelope.roundTrip(ObjectValue(fixture(envelope.name).fields + (legacyExtra to StringValue(occurred)))) }
            }
            assertFails { envelope.roundTrip(ObjectValue(value.fields - "occurred_at")) }
            assertFails { envelope.roundTrip(ObjectValue(value.fields + ("occurred_at" to NullValue))) }
            assertFails { envelope.roundTrip(ObjectValue(value.fields + ("protocol" to StringValue("future/99")))) }
            assertFails { envelope.roundTrip(ObjectValue(value.fields + ("classification" to StringValue("storage-accepted")))) }
        }
    }

    @Test fun `equivalent instants remain distinct assertions without changing task contracts`() {
        val old = NativeCodec.decodeEvidence(fixture("receipt"))
        val a = old.copy(time = OccurredAt.parseOrThrow(occurred))
        val b = a.copy(time = OccurredAt.parseOrThrow("2026-09-07T23:15:00.123456789Z"))
        assertEquals((a.time as OccurredAt).toInstant(), (b.time as OccurredAt).toInstant())
        assertEquals(a.receipt, b.receipt)
        assertNotEquals(Json.encode(NativeCodec.evidence(a)), Json.encode(NativeCodec.evidence(b)))
        val revision = HistoryCodec.decodeRevision(fixture("revision"))
        val updated = revision.copy(review = revision.review!!.copy(time = a.time))
        assertNotEquals(revision.id, updated.id)
        assertEquals(DraftLifecycle.contract(revision.record), DraftLifecycle.contract(updated.record))
        assertEquals(updated, HistoryCodec.decodeRevision(HistoryCodec.revision(updated)))
    }

    @Test fun `occurrence order cannot confer storage acceptance or reorder lifecycle transitions`() {
        val task = DraftRecord(TaskId.parseOrThrow("TASK.time"), "Time", "open", "Persist a result.", emptyList(),
            listOf("Durable"), listOf("Survives restart"), emptyList(), obj(), "tasking/core-draft-2", listOf("check"))
        val initial = LedgerSnapshot("time", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial()))
        val seeded = LedgerTransitions.evolve(initial, Transition.AddRecords(listOf(task)))
        fun close(time: AssertionTime) = LedgerTransitions.evolve(seeded, Transition.CloseTask(ClosureEvidence(
            Receipt(task.id, DraftLifecycle.contract(task), mapOf("check" to "Explicit caller assertion")), "actor", time)))
        val early = close(OccurredAt.parseOrThrow("0001-01-01T00:00:00Z"))
        val late = close(OccurredAt.parseOrThrow("9999-12-31T23:59:59Z"))
        assertEquals(early.universe, late.universe)
        assertEquals(early.history, late.history)
        assertEquals(early.currency(), late.currency())
        assertEquals(early.frontier(), late.frontier())
        assertNull(TransitionResult(Revision.initial(), listOf(task.id)).acceptedAt)
        val boundaryTime = AcceptedAt.fromInstant(Instant.parse("2026-09-07T23:16:00Z"))
        val accepted = TransitionResult(Revision.initial(), listOf(task.id), boundaryTime)
        assertEquals(boundaryTime, accepted.acceptedAt)
        // No accepted-time member exists on AssertionTime or any actor transition.
    }
}
