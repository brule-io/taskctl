package io.brule.tasking.kernel

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import org.postgresql.ds.PGSimpleDataSource
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class KernelIntegrationTest {
    @TempDir lateinit var directory: Path
    private val accepted = AcceptedAt.parseOrThrow("2026-09-08T01:00:00Z")
    private val clock = Clock.fixed(accepted.toInstant(), ZoneOffset.UTC)
    private val occurred = OccurredAt.parseOrThrow("2026-09-07T12:00:00Z")
    private val audit = PlanningAudit("fixture reviewer", occurred, "Review exact scope", mapOf("test" to "Explicit fictional observation"))
    private val roadmap = RoadmapId.parseOrThrow("ROADMAP.kernel.delivery")
    private val epic = EpicId.parseOrThrow("EPIC.kernel.feature")
    private fun id(name: String) = TaskId.parseOrThrow("TASK.kernel.$name")
    private fun task(name: String, requires: List<TaskId> = emptyList()) = DraftRecord(id(name), name, "open", "Bounded $name", requires,
        listOf("Durable result"), listOf("Observed result"), emptyList(),
        obj("kernel.opaque/v1" to obj("integer" to IntegerValue(java.math.BigInteger("999999999999999999999999999999999999")),
            "whole_decimal" to DecimalValue(java.math.BigDecimal("1E+30")), "scaled_decimal" to DecimalValue(java.math.BigDecimal("1.2300")))),
        "tasking/core-draft-2", listOf("test"))
    private fun dataSource(): PGSimpleDataSource {
        require(System.getenv("TASKCTL_KERNEL_DISPOSABLE") == "1") { "explicit disposable PostgreSQL required" }
        return PGSimpleDataSource().also {
            it.setURL(requireNotNull(System.getenv("TASKCTL_KERNEL_JDBC_URL")))
            it.user = requireNotNull(System.getenv("TASKCTL_KERNEL_USER")); it.password = requireNotNull(System.getenv("TASKCTL_KERNEL_PASSWORD"))
            it.connectTimeout = 5; it.socketTimeout = 15
        }
    }
    private fun postgres(key: String = "test-${UUID.randomUUID()}"): PostgresTaskLedger {
        val source = dataSource(); PostgresTaskLedger.initializeSchema(source)
        return PostgresTaskLedger(source, key, clock).also {
            it.create(LedgerSnapshot("kernel.conformance", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial())))
        }
    }
    private fun file(): FileTaskLedger {
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "fixture launcher\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        val root = directory.resolve("file")
        Bootstrap.apply(Bootstrap.plan(root, "kernel.conformance", "test", distribution.parent, lock))
        Files.writeString(root.resolve("source.txt"), "Unrelated source\n")
        return FileTaskLedger(root)
    }
    private fun review(snapshot: LedgerSnapshot, task: TaskId): Transition.ReconcileTask {
        val record = snapshot.universe.tasks.single { it.id == task }; val observations = CurrencyEvaluation.observations(snapshot)
        return Transition.ReconcileTask(Reconciliation(task, snapshot.history!!.heads.getValue(task), ReviewOutcome.REVALIDATED,
            record.requires.sorted().map { observations.getValue(it) }, "reviewer", occurred, "Current contract and inputs reviewed", mapOf("test" to "Observed current fixture")))
    }
    private fun assertion(snapshot: LedgerSnapshot): Transition.AssessPlanning {
        val scope = snapshot.universe.planning(epic); val history = snapshot.planningHistory!!
        return Transition.AssessPlanning(PlanningAssessment(history.assessmentHeads[epic], epic, history.heads.getValue(epic), snapshot.planningObservations(scope),
            PlanningAssessmentOutcome.ACCEPTED, audit, listOf("Observed the fixture capability")))
    }
    // File storage enumerates receipt filenames by digest, not acceptance order.
    // Sort only that unordered collection; preserve every complete receipt.
    private fun LedgerSnapshot.orderedReceipts() = copy(receipts = receipts.sortedBy { Json.encode(NativeCodec.evidence(it)) })

    @Test fun `file direct PostgreSQL and HTTP share lifecycle currency planning and historical evidence`() {
        val pg = postgres(); val file = file()
        KernelHttpServer.start(pg).use { server -> HttpTaskLedger(server.uri).use { http ->
            fun step(make: (LedgerSnapshot) -> Transition) {
                for (ledger in listOf<TaskLedger>(file, http)) {
                    val before = ledger.snapshot(); val command = make(before)
                    val expected = LedgerTransitions.evolve(before, command)
                    val result = ledger.apply(before.revision, command); val after = ledger.snapshot()
                    assertEquals(expected.copy(revision = after.revision).orderedReceipts(), after.orderedReceipts())
                    if (ledger === file) assertNull(result.acceptedAt) else assertEquals(accepted, result.acceptedAt)
                }
                val disk = file.snapshot(); val remote = http.snapshot()
                assertEquals(remote, pg.snapshot())
                assertEquals(disk.universe, remote.universe); assertEquals(disk.history, remote.history)
                assertEquals(disk.orderedReceipts().receipts, remote.orderedReceipts().receipts); assertEquals(disk.imports, remote.imports)
                assertEquals(disk.currency(), remote.currency())
                assertEquals(file.frontier().tasks, http.frontier().tasks)
                val remoteEvents = pg.audit()
                assertEquals(remoteEvents.last(), http.eventAfter(remoteEvents.size.toLong() - 1))
                assertNull(http.eventAfter(remoteEvents.size.toLong()))
            }
            step { Transition.AddRecords(listOf(task("a"), task("b", listOf(id("a"))), task("c", listOf(id("b")))),
                listOf(DraftRoadmap(roadmap, "Delivery", "Independent lane", listOf(id("a"), id("b"), id("c")))),
                listOf(DraftEpic(epic, "Feature", "Cross-lane scope", listOf(id("b"), id("c"))))) }
            for (name in listOf("a", "b")) step { snapshot ->
                val record = snapshot.universe.tasks.single { it.id == id(name) }
                Transition.CloseTask(ClosureEvidence(Receipt(record.id, DraftLifecycle.contract(record), mapOf("test" to "Observed")), "reviewer", occurred))
            }
            step { Transition.TrackPlanning }
            step { snapshot -> Transition.AmendPlanning(PlanningAmendment(snapshot.planningHistory!!.heads.getValue(epic),
                snapshot.universe.epics.single().copy(protocol = PlanningRecordCodec.AUDITED_PROTOCOL, acceptance = listOf("Capability observed")), audit)) }
            step(::assertion)
            step { snapshot -> Transition.SetPlanningDisposition(PlanningDispositionChange(roadmap, snapshot.planningHistory!!.heads.getValue(roadmap), PlanningDisposition.ARCHIVED, audit)) }
            assertEquals(listOf(id("c")), http.frontier(FrontierQuery(roadmap, epic)).tasks)
            step { snapshot -> Transition.CloseTask(ClosureEvidence(Receipt(id("c"), DraftLifecycle.contract(snapshot.universe.tasks.single { it.id == id("c") }), mapOf("test" to "Observed")), "reviewer", occurred)) }
            val historicalReceipts = http.snapshot().receipts
            step { snapshot -> Transition.ReviseTask(snapshot.universe.tasks.single { it.id == id("a") }.copy(requirements = listOf("Stronger upstream requirement"))) }
            assertEquals(Currency.AFFECTED, http.snapshot().currency().getValue(id("c")).state)
            for (ledger in listOf<TaskLedger>(file, http)) {
                val snapshot = ledger.snapshot()
                assertFails { ledger.apply(snapshot.revision, assertion(snapshot)) }
                assertEquals(snapshot, ledger.snapshot())
            }
            step { review(it, id("a")) }; step { review(it, id("b")) }
            assertEquals(Currency.AFFECTED, http.snapshot().currency().getValue(id("c")).state)
            step { review(it, id("c")) }; step(::assertion)
            assertEquals(historicalReceipts, http.snapshot().receipts)
            for (ledger in listOf<TaskLedger>(file, http)) {
                val snapshot = ledger.snapshot(); val history = snapshot.planningHistory!!
                val statuses = snapshot.planningAssessmentStatuses(history)
                assertEquals(1, statuses.values.count { it.currency == PlanningAssessmentCurrency.CURRENT })
                assertEquals(1, statuses.values.count { it.currency == PlanningAssessmentCurrency.HISTORICAL })
                assertFailsWith<RevisionConflict> { ledger.apply(Revision.initial(), Transition.AddRecords(listOf(task("stale")))) }
                assertEquals(snapshot, ledger.snapshot())
            }
            assertEquals("Unrelated source\n", Files.readString(file.root.resolve("source.txt")))
        } }
        KernelHttpServer.start(pg).use { restarted -> HttpTaskLedger(restarted.uri).use {
            assertEquals(pg.snapshot(), it.snapshot()); assertEquals(pg.audit().first(), it.eventAfter(0))
        } }
    }

    @Test fun `failed event insert rolls the snapshot and sequence back before explicit retry`() {
        val pg = postgres(); val before = pg.snapshot()
        dataSource().connection.use { c -> c.createStatement().use { it.execute("ALTER TABLE taskctl_kernel_events ADD CONSTRAINT test_reject_event CHECK (false) NOT VALID") } }
        try {
            assertFailsWith<SQLException> { pg.apply(before.revision, Transition.AddRecords(listOf(task("rollback")))) }
            assertEquals(before, pg.snapshot()); assertTrue(pg.audit().isEmpty())
        } finally {
            dataSource().connection.use { c -> c.createStatement().use { it.execute("ALTER TABLE taskctl_kernel_events DROP CONSTRAINT test_reject_event") } }
        }
        pg.apply(before.revision, Transition.AddRecords(listOf(task("rollback"))))
        assertEquals(1, pg.audit().size)
        assertEquals(listOf(id("rollback")), pg.frontier().tasks)
    }

    @Test fun `historical import preserves old assertions without synthesizing receipts or acceptance times`() {
        val pg = postgres(); val file = file()
        val legacy = requireNotNull(javaClass.getResourceAsStream("/historical/source.json")).use { it.readBytes().toString(Charsets.UTF_8) }
        val record = task("old-01").copy(state = "closed")
        val oldContract = ContractDigest.parseOrThrow(Canonical.digest("kernel.fictional-legacy/1", StringValue(legacy)))
        val path = "kernel/src/integration/resources/historical/source.json"
        val manifest = ImportManifest("https://github.com/brule-io/taskctl", "dfa6a036e9e016f3c2b21fe6709d644ea6428722", "kernel-conformance", "1.0.0",
            mapOf(path to legacy), listOf(ImportSource(record.id, path, "closed", Canonical.sha256(legacy.toByteArray()), oldContract)), DraftUniverse(listOf(record)))
        val imported = Transition.ImportRecords(ImportAdmission(manifest, ImportReview(manifest.id, "fixture reviewer", LegacyRecordedAt.parseOrThrow("historical actor label"), "Explicit fixture mapping review")))
        KernelHttpServer.start(pg).use { server -> HttpTaskLedger(server.uri).use { http ->
            for (ledger in listOf<TaskLedger>(file, http)) {
                ledger.apply(ledger.snapshot().revision, imported)
                val snapshot = ledger.snapshot()
                assertEquals(manifest, snapshot.imports.single().manifest)
                assertTrue(snapshot.receipts.isEmpty()); assertTrue(ledger.frontier().tasks.isEmpty())
                assertNotEquals(oldContract, DraftLifecycle.contract(snapshot.universe.tasks.single()))
                ledger.apply(snapshot.revision, review(snapshot, record.id))
                assertEquals(Currency.CURRENT, ledger.snapshot().currency().getValue(record.id).state)
                assertEquals("closed", ledger.task(record.id)!!.state)
                assertTrue(ledger.snapshot().receipts.isEmpty())
            }
            assertEquals(file.snapshot().history, http.snapshot().history)
            assertEquals(file.snapshot().imports, http.snapshot().imports)
            val event = pg.audit().first()
            assertEquals(imported, event.transition); assertEquals(accepted, event.result.acceptedAt)
            assertEquals("historical actor label", http.snapshot().imports.single().review.time.value)
        } }
    }

    @Test fun `HTTP diagnostics and rejected transitions leave the database unchanged`() {
        val pg = postgres(); val before = pg.snapshot()
        KernelHttpServer.start(pg).use { server -> HttpClient.newHttpClient().use { client ->
            fun send(path: String, method: String, body: String = "", contentType: String = "application/json"): Int {
                val request = HttpRequest.newBuilder(server.uri.resolve(path)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofString(body)).build()
                return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
            }
            assertEquals(404, send("/absent", "GET"))
            assertEquals(405, send("/kernel/alpha1/snapshot", "POST"))
            assertEquals(400, send("/kernel/alpha1/snapshot?unknown=1", "GET"))
            assertEquals(400, send("/kernel/alpha1/events?after=-1", "GET"))
            assertEquals(415, send("/kernel/alpha1/apply", "POST", "{}", "text/plain"))
            assertEquals(400, send("/kernel/alpha1/apply", "POST", "kind: seed"))
            val malformedUtf8 = HttpRequest.newBuilder(server.uri.resolve("/kernel/alpha1/apply"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(byteArrayOf(0xc3.toByte(), 0x28))).build()
            val malformedResponse = client.send(malformedUtf8, HttpResponse.BodyHandlers.ofString())
            assertEquals(400, malformedResponse.statusCode())
            assertEquals("VALIDATION", KernelCodec.parse(malformedResponse.body().toByteArray()).requiredString("code"))
            assertEquals(413, send("/kernel/alpha1/apply", "POST", "x".repeat(KernelCodec.MAX_BODY_BYTES + 1)))
            val request = KernelCodec.request(before.revision, Transition.AddRecords(listOf(task("seed"))))
            assertEquals(400, send("/kernel/alpha1/apply", "POST", Json.encode(ObjectValue(request.fields + ("accepted_at" to StringValue(accepted.value))))))
            val closedSeed = KernelCodec.request(before.revision, Transition.AddRecords(listOf(task("invalid").copy(state = "closed"))))
            assertEquals(400, send("/kernel/alpha1/apply", "POST", Json.encode(closedSeed)))
            assertEquals(before, pg.snapshot()); assertTrue(pg.audit().isEmpty())
            HttpTaskLedger(server.uri).use { http ->
                http.apply(before.revision, Transition.AddRecords(listOf(task("required").copy(requiredExtensions = listOf("unavailable.policy/v1")))))
                val blocked = http.snapshot()
                assertFails { http.frontier() }
                assertFails { http.apply(blocked.revision, review(blocked, id("required"))) }
                assertEquals(blocked, pg.snapshot()); assertEquals(1, pg.audit().size)
            }
        } }
    }

    @Test fun `snapshot identity is independent of event sequence and a nonmonotonic acceptance clock`() {
        val source = dataSource(); PostgresTaskLedger.initializeSchema(source)
        val key = "test-${UUID.randomUUID()}"
        val first = PostgresTaskLedger(source, key, clock)
        val initial = first.create(LedgerSnapshot("kernel.conformance", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial())))
        val one = first.apply(initial.revision, Transition.AddRecords())
        val second = PostgresTaskLedger(source, key, Clock.offset(clock, Duration.ofDays(-1)))
        val two = second.apply(initial.revision, Transition.AddRecords())
        assertEquals(initial.revision, one.revision); assertEquals(one.revision, two.revision)
        assertTrue(two.acceptedAt!!.toInstant().isBefore(one.acceptedAt!!.toInstant()))
        val events = second.audit()
        assertEquals(listOf(1L, 2L), events.map { it.sequence })
        assertEquals(events[0].id, events[1].parent)
        assertNotEquals(events[0].id, events[1].id)
        assertEquals(initial, second.snapshot())
    }

    @Test fun `explicit history adoption preserves old native assertions and leaves unobserved inputs unresolved`() {
        val source = dataSource(); PostgresTaskLedger.initializeSchema(source)
        val pg = PostgresTaskLedger(source, "test-${UUID.randomUUID()}", clock)
        val root = task("ancestral").copy(state = "closed")
        val receipt = ClosureEvidence(Receipt(root.id, DraftLifecycle.contract(root), mapOf("test" to "Historical actor assertion")),
            "old actor", LegacyRecordedAt.parseOrThrow("an old unvalidated time label"))
        val before = pg.create(LedgerSnapshot("kernel.ancestral", Revision.initial(), DraftUniverse(listOf(root, task("dependent", listOf(root.id)))), listOf(receipt)))
        pg.apply(before.revision, Transition.TrackHistory)
        val after = pg.snapshot()
        assertEquals(before.revision, after.history!!.origin)
        assertEquals(before.receipts, after.receipts); assertEquals(before.universe, after.universe)
        assertEquals(Currency.UNRESOLVED, after.currency().getValue(id("dependent")).state)
        assertEquals(LedgerTransitions.evolve(before, Transition.TrackHistory).copy(revision = after.revision), after)
        assertEquals(Transition.TrackHistory, pg.audit().single().transition)
    }

    @Test fun `a reply lost after commit is inspected without replaying the accepted command`() {
        val pg = postgres(); val before = pg.snapshot(); val command = Transition.AddRecords(listOf(task("lost-reply")))
        val forwarded = AtomicInteger(); val committed = CountDownLatch(1)
        KernelHttpServer.start(pg).use { server -> HttpClient.newHttpClient().use { forwarder ->
            val proxy = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 4)
            proxy.createContext("/") { exchange ->
                try {
                    forwarded.incrementAndGet()
                    val request = HttpRequest.newBuilder(server.uri.resolve(exchange.requestURI)).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(exchange.requestBody.readNBytes(KernelCodec.MAX_BODY_BYTES + 1))).build()
                    val response = forwarder.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    if (response.statusCode() == 200) committed.countDown()
                    // Intentionally lose only the reply, after the real server committed.
                } finally { exchange.close() }
            }
            proxy.start()
            try {
                HttpTaskLedger(java.net.URI.create("http://127.0.0.1:${proxy.address.port}")).use { client ->
                    assertFailsWith<IOException> { client.apply(before.revision, command) }
                }
                assertTrue(committed.await(10, TimeUnit.SECONDS)); assertEquals(1, forwarded.get())
                assertEquals(listOf(id("lost-reply")), pg.frontier().tasks)
                assertEquals(1, pg.audit().size)
                HttpTaskLedger(server.uri).use { client ->
                    assertFailsWith<RevisionConflict> { client.apply(before.revision, command) }
                    assertEquals(pg.snapshot(), client.snapshot())
                }
                assertEquals(1, pg.audit().size)
            } finally { proxy.stop(0) }
        } }
    }

    @Test fun `event corruption is detected and PostgreSQL stores exact JSON as text`() {
        val key = "test-${UUID.randomUUID()}"; val pg = postgres(key)
        pg.apply(pg.snapshot().revision, Transition.AddRecords(listOf(task("tamper"))))
        val before = pg.snapshot(); val event = pg.audit().single()
        dataSource().connection.use { connection ->
            connection.createStatement().use { sql -> sql.executeQuery("SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'taskctl_kernel_ledgers' AND column_name = 'snapshot'").use {
                assertTrue(it.next()); assertEquals("text", it.getString(1))
            } }
            connection.prepareStatement("UPDATE taskctl_kernel_events SET payload = ? WHERE ledger_key = ? AND sequence = 1").use {
                it.setString(1, Json.encode(event.copy(result = event.result.copy(acceptedAt = AcceptedAt.parseOrThrow("2026-09-08T02:00:00Z"))).encode()))
                it.setString(2, key); assertEquals(1, it.executeUpdate())
            }
        }
        assertFails { pg.events() }; assertFails { pg.audit() }
        assertEquals(before, pg.snapshot())
    }

    @Test fun `adapter capacity refusal is atomic without changing core task validity`() {
        val pg = postgres(); val file = file()
        val payload = obj("kernel.large/v1" to StringValue("x".repeat(70_000)))
        val first = Transition.AddRecords(listOf(task("capacity-first").copy(extensions = payload)))
        val second = Transition.AddRecords(listOf(task("capacity-second").copy(extensions = payload)))
        file.apply(file.snapshot().revision, first); file.apply(file.snapshot().revision, second)
        assertEquals(2, file.snapshot().universe.tasks.size)
        KernelHttpServer.start(pg).use { server -> HttpTaskLedger(server.uri).use { http ->
            http.apply(http.snapshot().revision, first)
            val before = http.snapshot()
            assertEquals(2, LedgerTransitions.evolve(before, second).universe.tasks.size)
            assertFailsWith<IllegalArgumentException> { http.apply(before.revision, second) }
            assertEquals(before, http.snapshot()); assertEquals(1, pg.audit().size)
        } }
    }

    @Test fun `unrelated HTTP writers demonstrate strict whole-ledger contention without automatic retries`() {
        val pg = postgres(); val rounds = mutableListOf<Value>()
        KernelHttpServer.start(pg).use { server ->
            val workers = Executors.newFixedThreadPool(4)
            try {
                repeat(3) { round ->
                    val expected = pg.snapshot().revision; val start = CountDownLatch(1); val ready = CountDownLatch(4)
                    val began = System.nanoTime()
                    val results = (0..3).map { worker -> workers.submit(Callable {
                        HttpTaskLedger(server.uri).use { client ->
                            ready.countDown(); check(start.await(10, TimeUnit.SECONDS))
                            try { client.apply(expected, Transition.AddRecords(listOf(task("round${round}writer$worker")))); true }
                            catch (_: RevisionConflict) { false }
                        }
                    }) }
                    check(ready.await(10, TimeUnit.SECONDS)); start.countDown()
                    val acceptedCount = results.count { it.get(30, TimeUnit.SECONDS) }
                    assertEquals(1, acceptedCount)
                    assertEquals(round + 1, pg.audit().size)
                    rounds += obj("writers" to integer(4), "accepted" to integer(acceptedCount), "stale_rejected" to integer(4 - acceptedCount),
                        "elapsed_nanos" to IntegerValue(java.math.BigInteger.valueOf(System.nanoTime() - began)))
                }
            } finally { workers.shutdownNow() }
        }
        assertEquals(3, pg.snapshot().universe.tasks.size)
        System.getProperty("kernel.proof")?.let { name ->
            val path = Path.of(name); Files.createDirectories(path.parent)
            val database = dataSource().connection.use { connection -> obj("database_version" to StringValue(connection.metaData.databaseProductVersion),
                "driver_version" to StringValue(connection.metaData.driverVersion)) }
            Files.writeString(path, Json.encode(obj("protocol" to StringValue("taskctl.kernel-contention-proof/alpha1"),
                "classification" to StringValue("observed-disposable-kernel-experiment"), "cas" to StringValue("strict-whole-ledger-snapshot"),
                "database" to database, "rounds" to ArrayValue(rounds), "automatic_application_retries" to integer(0), "events" to integer(pg.audit().size))) + "\n")
        }
    }
}
