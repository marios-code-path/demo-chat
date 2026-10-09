package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.BackendResponse
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal data class MessageHttpRequest(
    val method: String,
    val rawPath: String,
    val credential: String?,
    val requestId: String?,
    val text: String,
)

internal class MessagingTestBackend : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val answers = ConcurrentHashMap<String, BackendResponse>()
    val requests = CopyOnWriteArrayList<MessageHttpRequest>()
    val origin: URI get() = URI("http://127.0.0.1:${server.address.port}")

    init {
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.rawPath
            requests.add(MessageHttpRequest(
                exchange.requestMethod, path,
                exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestHeaders.getFirst("Idempotency-Key"),
                exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) },
            ))
            val answer = answers[path] ?: BackendResponse(404, "not available")
            val bytes = answer.body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(answer.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    fun answer(path: String, status: Int, body: String) {
        answers[path] = BackendResponse(status, body)
    }

    override fun close() {
        server.stop(0)
    }
}
