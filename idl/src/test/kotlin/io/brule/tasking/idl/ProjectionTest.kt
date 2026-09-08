package io.brule.tasking.idl

import io.brule.tasking.core.*
import io.brule.tasking.kernel.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ProjectionTest {
    private val root: Path get() = Path.of(System.getProperty("taskctl.root"))
    @Test fun `Smithy validates the custom protocol and preserves explicit typed wire shapes`() {
        val model = SmithyProjection.validate()
        listOf("TaskLedger", "ApplyOperation", "SnapshotOperation", "EventsOperation", "Value", "IntegerValue", "DecimalValue", "Event", "explicitNull").forEach {
            assertTrue(model.getShape(software.amazon.smithy.model.shapes.ShapeId.from("${WireModel.NAMESPACE}#$it")).isPresent)
        }
        val encoded = Json.encode(SmithyProjection.encode())
        assertFalse(encoded.contains("restJson1" + "\""))
        assertFalse(encoded.contains("\"type\":\"document\""))
        assertContains(encoded, "taskctlIdlDigest")
        assertEquals(setOf("/kernel/alpha1/snapshot", "/kernel/alpha1/apply", "/kernel/alpha1/events"), WireModel.operations.map { it.path }.toSet())
    }
    @Test fun `checked in projections are byte reproducible from the typed AST`() {
        assertEquals(Json.encode(WireModel.encode()) + "\n", Files.readString(root.resolve("idl/generated/taskctl-wire.json")))
        assertEquals(Json.encode(SmithyProjection.encode()) + "\n", Files.readString(root.resolve("idl/generated/taskctl-smithy.json")))
        assertEquals(PythonProjection.encode(), Files.readString(root.resolve("idl/generated/taskctl_client.py")))
    }
    @Test fun `IDL names cross a validity boundary and never admit executable syntax`() {
        listOf("", "banana-name", "Name;exec", "_Private", "🦍").forEach { assertFails { ShapeName.parseOrThrow(it) } }
        listOf("", "Upper", "field-name", "field.foo").forEach { assertFails { FieldName.parseOrThrow(it) } }
        assertEquals("Snapshot", ShapeName.parseOrThrow("Snapshot").value)
        assertEquals("expected_revision", FieldName.parseOrThrow("expected_revision").value)
    }
    @Test fun `core generated envelopes remain the source of the interoperability witnesses`() {
        val task = DraftRecord(TaskId.parseOrThrow("TASK.idl.work"), "Work", "open", "Bounded work", emptyList(), listOf("Durable result"), listOf("Observed result"),
            emptyList(), obj("idl.opaque/v1" to obj("huge" to IntegerValue(java.math.BigInteger("900719925474099312345678901234567890")),
                "scaled" to DecimalValue(java.math.BigDecimal("1.2300000000000000000000000000000000001")), "whole" to DecimalValue(java.math.BigDecimal("1E+30")),
                "zero" to DecimalValue(java.math.BigDecimal("0.00")), "unicode" to StringValue("\u0085\u2028\ufffe🦍"),
                "\uE000" to StringValue("BMP key"), "🦍" to StringValue("supplementary key"))), "tasking/core-draft-2", listOf("test"))
        val snapshot = LedgerSnapshot("idl.goldens", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial()))
        val typed = snapshot.copy(revision = KernelCodec.revision(snapshot))
        val request = KernelCodec.request(typed.revision, Transition.AddRecords(listOf(task)))
        val times = listOf("2024-02-29T12:34:56.123456789+14:00", "2026-09-08T03:00:00Z", "2026-09-08T03:00:00-18:00", "2026-02-30T00:00:00Z", "0000-01-01T00:00:00Z", "2026-09-08T03:00:00-00:00", "2026-09-08T03:00:00+18:01", "2026-09-08T03:00:60Z")
        val values = obj("request" to request, "snapshot" to KernelCodec.snapshot(typed), "value" to task.extensions,
            "acceptance_times" to ArrayValue(times.map { obj("value" to StringValue(it), "valid" to BooleanValue(AcceptedAt.parse(it) != null)) }),
            "value_digest" to StringValue(Canonical.digest("idl.golden/alpha1", task.extensions)))
        val path = root.resolve("build/proof/idl-goldens.json"); Files.createDirectories(path.parent)
        Files.writeString(path, Json.encode(values))
        assertEquals(request, KernelCodec.request(typed.revision, KernelCodec.decodeRequest(request).second))
    }
}
