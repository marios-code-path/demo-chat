package com.demo.chat.mcp.tool

import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.AdapterId
import com.demo.chat.mcp.config.KeyType
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** One canned answer from the fake backend. */
data class BackendAnswer(val status: Int, val body: String)

/**
 * A fake topic backend.
 *
 * It answers `GET /topic/id/{id}` from a table. A path that names no topic
 * answers 404. Every id that reaches it is recorded, so a test can prove that
 * the adapter asked for the ids it should and no others.
 */
class FakeTopicBackend : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val answers = ConcurrentHashMap<String, BackendAnswer>()

    /** Every topic id that reached this backend, in order. */
    val requestedIds: MutableList<String> = Collections.synchronizedList(mutableListOf())

    init {
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
    }

    /** The origin of this backend. It is a loopback address and a random port. */
    val origin: URI get() = URI("http://127.0.0.1:${server.address.port}")

    /** Set the answer for one topic id. */
    fun answer(id: String, status: Int, body: String) {
        answers[id] = BackendAnswer(status, body)
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val prefix = "/topic/id/"
        val id = if (path.startsWith(prefix)) path.removePrefix(prefix) else null
        if (id != null) {
            requestedIds.add(id)
        }
        val answer = id?.let { answers[it] }
        if (answer == null) {
            respond(exchange, 404, """{"error":"no such topic"}""")
        } else {
            respond(exchange, answer.status, answer.body)
        }
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() {
        server.stop(0)
    }
}

/**
 * The wire body of one topic.
 *
 * The shape is the one Task 3 measured against a memory deployment. Both
 * wrappers are present, and both ids are JSON numbers.
 */
fun topicBody(id: Long, root: Long, name: String): String =
    """{"keyValue":{"data":"$name","key":{"key":{"id":$id,"root":$root,"empty":false}}}}"""

/** Write a credential file and mark it for removal at exit. */
fun writeCredentialFile(): Path {
    val path = Files.createTempFile("chat-mcp-test-credential", ".txt")
    Files.writeString(path, "test-credential")
    path.toFile().deleteOnExit()
    return path
}

/** Build one adapter configuration for a test. */
fun testConfig(
    origin: URI,
    topicIds: List<AdapterId>,
    credentialFile: Path = writeCredentialFile(),
    keyType: KeyType = KeyType.LONG,
): AdapterConfig =
    AdapterConfig(
        backendBaseUrl = origin,
        credentialFile = credentialFile,
        keyType = keyType,
        topicIds = topicIds,
        enableSend = false,
        enableSearch = false,
    )
