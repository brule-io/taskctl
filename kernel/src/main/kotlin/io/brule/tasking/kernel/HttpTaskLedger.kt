package io.brule.tasking.kernel

import io.brule.tasking.core.*
import io.brule.tasking.kernel.KernelCodec.exact
import io.brule.tasking.kernel.KernelCodec.objectAt
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.Executors

/** Explicit, loopback-only test lifetime. There is no service entry point. */
class KernelHttpServer private constructor(private val ledger: PostgresTaskLedger) : AutoCloseable {
    private val executor = Executors.newFixedThreadPool(4, Thread.ofPlatform().daemon().name("taskctl-kernel-test-", 0).factory())
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 16)
    val uri: URI get() = URI.create("http://127.0.0.1:${server.address.port}")
    init {
        server.executor = executor
        server.createContext("/") { exchange -> handle(exchange) }
    }
    companion object {
        fun start(ledger: PostgresTaskLedger): KernelHttpServer = KernelHttpServer(ledger).also { it.server.start() }
    }
    override fun close() { server.stop(0); executor.shutdownNow() }
    private fun respond(exchange: HttpExchange, status: Int, value: ObjectValue) {
        val bytes = Json.encode(value).toByteArray(Charsets.UTF_8)
        check(bytes.size <= KernelCodec.MAX_BODY_BYTES) { "kernel response exceeds byte limit" }
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    private fun failure(exchange: HttpExchange, status: Int, code: String, message: String) = respond(exchange, status,
        obj("protocol" to StringValue("taskctl.kernel-error/alpha1"), "code" to StringValue(code), "message" to StringValue(message)))
    private fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.rawPath
            when {
                path == "/kernel/alpha1/snapshot" && exchange.requestMethod == "GET" -> {
                    require(exchange.requestURI.rawQuery == null) { "snapshot query unsupported" }
                    respond(exchange, 200, KernelCodec.snapshot(ledger.snapshot()))
                }
                path == "/kernel/alpha1/events" && exchange.requestMethod == "GET" -> {
                    val query = exchange.requestURI.rawQuery ?: "after=0"
                    require(Regex("after=(0|[1-9][0-9]{0,18})").matches(query)) { "one nonnegative after cursor required" }
                    val after = query.removePrefix("after=").toLong()
                    val event = ledger.events(after, 1).singleOrNull()
                    respond(exchange, 200, obj("protocol" to StringValue("taskctl.kernel-event-page/alpha1"),
                        "event" to (event?.encode() ?: NullValue), "event_id" to optionalString(event?.id?.value)))
                }
                path == "/kernel/alpha1/apply" && exchange.requestMethod == "POST" -> {
                    require(exchange.requestURI.rawQuery == null) { "apply query unsupported" }
                    if (exchange.requestHeaders.getFirst("Content-Type") != "application/json") {
                        failure(exchange, 415, "CONTENT_TYPE", "application/json required"); return
                    }
                    val bytes = exchange.requestBody.readNBytes(KernelCodec.MAX_BODY_BYTES + 1)
                    if (bytes.size > KernelCodec.MAX_BODY_BYTES) { failure(exchange, 413, "BODY_LIMIT", "bounded request exceeds byte limit"); return }
                    val (expected, transition) = KernelCodec.decodeRequest(KernelCodec.parse(bytes))
                    val result = ledger.apply(expected, transition)
                    respond(exchange, 200, KernelCodec.result(result))
                }
                path in setOf("/kernel/alpha1/snapshot", "/kernel/alpha1/events", "/kernel/alpha1/apply") -> failure(exchange, 405, "METHOD", "method unsupported")
                else -> failure(exchange, 404, "PATH", "kernel path absent")
            }
        } catch (_: RevisionConflict) {
            failure(exchange, 409, "STALE_REVISION", "stale whole-ledger revision; inspect before retrying")
        } catch (_: SQLException) {
            failure(exchange, 500, "STORAGE", "kernel storage operation failed")
        } catch (_: IOException) {
            // A disconnected client can have an uncertain result. A committed
            // operation is not undone or automatically retried after reply loss.
        } catch (failure: IllegalArgumentException) {
            failure(exchange, 400, "VALIDATION", failure.message ?: "invalid kernel request")
        } catch (failure: IllegalStateException) {
            failure(exchange, 400, "VALIDATION", failure.message ?: "invalid kernel state or transition")
        } catch (_: NoSuchElementException) {
            failure(exchange, 400, "ABSENT_RECORD", "required ledger record absent")
        } finally { exchange.close() }
    }
}

class KernelTransportException(message: String) : IllegalStateException(message)

/** Same domain seam; transport failures never trigger an application retry. */
class HttpTaskLedger(private val endpoint: URI) : TaskLedger, AutoCloseable {
    init {
        require(endpoint.scheme == "http" && endpoint.host == "127.0.0.1" && endpoint.port in 1..65535 &&
            endpoint.rawUserInfo == null && endpoint.rawQuery == null && endpoint.rawFragment == null && endpoint.rawPath in listOf("", "/")) {
            "bounded kernel client requires an explicit loopback endpoint"
        }
    }
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()
    override fun close() { client.close() }
    private fun exchange(path: String, value: ObjectValue? = null): ObjectValue {
        val request = HttpRequest.newBuilder(endpoint.resolve(path)).timeout(Duration.ofSeconds(15))
        if (value == null) request.GET() else {
            val bytes = KernelCodec.text(value).toByteArray(Charsets.UTF_8)
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
        }
        val response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
        require(response.headers().firstValue("Content-Type").orElse("") == "application/json") { "kernel response content type invalid" }
        val body = KernelCodec.parse(response.body())
        if (response.statusCode() != 200) {
            body.exact("protocol", "code", "message")
            require(body.requiredString("protocol") == "taskctl.kernel-error/alpha1") { "kernel error envelope invalid" }
            val message = body.requiredString("message")
            when (response.statusCode()) {
                409 -> throw RevisionConflict(message)
                400 -> throw IllegalArgumentException(message)
                else -> throw KernelTransportException("kernel HTTP ${response.statusCode()}: $message")
            }
        }
        return body
    }
    override fun snapshot(): LedgerSnapshot = KernelCodec.decodeSnapshot(exchange("/kernel/alpha1/snapshot"))
    override fun apply(expectedRevision: Revision, transition: Transition): TransitionResult =
        KernelCodec.decodeResult(exchange("/kernel/alpha1/apply", KernelCodec.request(expectedRevision, transition)))
    fun eventAfter(sequence: Long): KernelEvent? {
        require(sequence >= 0) { "negative event cursor" }
        val body = exchange("/kernel/alpha1/events?after=$sequence")
        body.exact("protocol", "event", "event_id")
        require(body.requiredString("protocol") == "taskctl.kernel-event-page/alpha1") { "kernel event page invalid" }
        if (body.fields["event"] == NullValue) { require(body.fields["event_id"] == NullValue); return null }
        return KernelEvent.decode(body.objectAt("event")).also { require(it.id.value == body.requiredString("event_id") && it.sequence > sequence) { "event page identity invalid" } }
    }
}
