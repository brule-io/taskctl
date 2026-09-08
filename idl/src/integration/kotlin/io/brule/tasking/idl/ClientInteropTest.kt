package io.brule.tasking.idl

import io.brule.tasking.core.*
import io.brule.tasking.kernel.*
import org.postgresql.ds.PGSimpleDataSource
import org.junit.jupiter.api.io.TempDir
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ClientInteropTest {
    @TempDir lateinit var directory: Path
    private val root: Path get() = Path.of(System.getProperty("taskctl.root"))
    private val occurred = OccurredAt.parseOrThrow("2026-09-08T03:00:00Z")
    private val clock = Clock.fixed(Instant.parse("2026-09-08T03:00:01Z"), ZoneOffset.UTC)
    private fun id(name: String) = TaskId.parseOrThrow("TASK.idl.$name")
    private fun task(name: String, depends: List<TaskId> = emptyList()) = DraftRecord(id(name), name, "open", "Bounded $name", depends,
        listOf("Durable result"), listOf("Observed result"), emptyList(), obj("idl.opaque/v1" to obj(
            "integer" to IntegerValue(java.math.BigInteger("900719925474099312345678901234567890")),
            "decimal" to DecimalValue(java.math.BigDecimal("1.2300000000000000000000000000000000001")),
            "whole" to DecimalValue(java.math.BigDecimal("1E+30")), "\uE000" to StringValue("BMP"), "🦍" to StringValue("\u0085\u2028\ufffe"))), "tasking/core-draft-2", listOf("test"))
    private fun postgres(): PostgresTaskLedger {
        require(System.getenv("TASKCTL_KERNEL_DISPOSABLE") == "1") { "explicit disposable database required" }
        val source = PGSimpleDataSource().also {
            it.setURL(requireNotNull(System.getenv("TASKCTL_KERNEL_JDBC_URL")))
            it.user = requireNotNull(System.getenv("TASKCTL_KERNEL_USER")); it.password = requireNotNull(System.getenv("TASKCTL_KERNEL_PASSWORD"))
            it.connectTimeout = 5; it.socketTimeout = 15
        }
        PostgresTaskLedger.initializeSchema(source)
        return PostgresTaskLedger(source, "idl-${UUID.randomUUID()}", clock).also {
            it.create(LedgerSnapshot("idl.interop", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial())))
        }
    }
    private fun close(task: DraftRecord) = Transition.CloseTask(ClosureEvidence(Receipt(task.id, DraftLifecycle.contract(task), mapOf("test" to "Explicit fictional observed result")), "SDK interop reviewer", occurred))
    private fun seed() = Transition.AddRecords(listOf(task("root"), task("leaf", listOf(id("root")))),
        listOf(DraftRoadmap(RoadmapId.parseOrThrow("ROADMAP.idl.delivery"), "Delivery", "Orthogonal scope", listOf(id("root"), id("leaf")))),
        listOf(DraftEpic(EpicId.parseOrThrow("EPIC.idl.audit"), "Audit", "Feature scope", listOf(id("leaf")))))
    private fun fixture(snapshot: LedgerSnapshot): ObjectValue = obj("before" to KernelCodec.snapshot(snapshot), "seed" to KernelCodec.transition(seed()),
        "close_root" to KernelCodec.transition(close(task("root"))), "close_leaf" to KernelCodec.transition(close(task("leaf", listOf(id("root"))))),
        "invalid_close" to KernelCodec.transition(close(task("root").copy(acceptance = listOf("Changed unproved acceptance")))))
    private fun client(mode: String, endpoint: String, snapshot: LedgerSnapshot): ObjectValue {
        val input = directory.resolve("input.json"); val output = directory.resolve("output.json")
        Files.writeString(input, Json.encode(fixture(snapshot)))
        val marker = directory.resolve(".agents-unrelated"); Files.writeString(marker, "untouched\n")
        val before = Pair(Files.readString(marker), Files.getLastModifiedTime(marker))
        val process = ProcessBuilder("python", root.resolve("idl/tests/interop.py").toString(), mode, endpoint, input.toString(), output.toString())
            .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve("client.log").toFile())
            .also { it.environment()["PYTHONDONTWRITEBYTECODE"] = "1" }.start()
        if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("generated client timed out") }
        assertEquals(0, process.exitValue(), Files.readString(directory.resolve("client.log")))
        assertEquals(before, Pair(Files.readString(marker), Files.getLastModifiedTime(marker)))
        return KernelCodec.parse(Files.readAllBytes(output))
    }
    @Test fun `generated Python client interoperates with real PostgreSQL HTTP lifecycle CAS and typed event history`() {
        val ledger = postgres(); val before = ledger.snapshot()
        KernelHttpServer.start(ledger).use { server ->
            val result = client("lifecycle", server.uri.toString(), before)
            val returned = KernelCodec.decodeSnapshot(result.fields.getValue("snapshot") as ObjectValue)
            assertEquals(ledger.snapshot(), returned)
            assertEquals(listOf("closed", "closed"), returned.universe.tasks.map { it.state })
            assertEquals(seed().roadmaps, returned.universe.roadmaps); assertEquals(seed().epics, returned.universe.epics)
            assertEquals(2, returned.receipts.size); assertTrue(returned.currency().values.all { it.state == Currency.CURRENT })
            assertEquals(seed().tasks.map { it.extensions }, returned.universe.tasks.map { it.extensions })
            val events = ledger.audit(); assertEquals(3, events.size)
            result.requiredArray("events").forEachIndexed { index, value ->
                val page = value as ObjectValue
                val event = KernelEvent.decode(page.fields.getValue("event") as ObjectValue)
                assertEquals(events[index], event); assertEquals(event.id.value, page.requiredString("event_id"))
                assertEquals("2026-09-08T03:00:01Z", event.result.acceptedAt!!.value)
            }
            HttpTaskLedger(server.uri).use { assertEquals(returned, it.snapshot()) }
        }
    }
    @Test fun `generated client never replays a command after its committed reply is lost`() {
        val ledger = postgres(); val before = ledger.snapshot(); val attempts = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/kernel/alpha1/apply") { exchange ->
            try {
                attempts.incrementAndGet()
                val (revision, transition) = KernelCodec.decodeRequest(KernelCodec.parse(exchange.requestBody.readNBytes(KernelCodec.MAX_BODY_BYTES+1)))
                ledger.apply(revision, transition)
            } finally { exchange.close() } // Deliberately lose the real committed reply.
        }
        server.start()
        try {
            val result = client("lost-reply", "http://127.0.0.1:${server.address.port}", before)
            assertEquals("uncertain-no-retry", result.requiredString("outcome"))
            assertEquals(1, attempts.get()); assertEquals(1, ledger.audit().size)
            assertEquals(seed().tasks, ledger.snapshot().universe.tasks)
        } finally { server.stop(0) }
    }
}
