package com.demo.chat.mcp

import com.demo.chat.mcp.client.JdkBackendHttp
import com.demo.chat.mcp.config.AdapterConfig
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val PIPE_BYTES: Int = 1 shl 16

private const val DEFAULT_TIMEOUT_MILLIS: Long = 15_000

/**
 * An MCP client and one adapter, joined by two pipes.
 *
 * The client writes JSON-RPC frames to the adapter and reads the frames that
 * answer them. The adapter is the production server, so the tool surface is
 * exercised through the real transport.
 */
class StdioHarness(config: AdapterConfig) : AutoCloseable {
    private val toServer = PipedOutputStream()
    private val serverInput = PipedInputStream(toServer, PIPE_BYTES)
    private val fromServer = PipedOutputStream()
    private val clientInput = PipedInputStream(fromServer, PIPE_BYTES)
    private val frames = LinkedBlockingQueue<String>()

    /** Every frame that the adapter wrote, in order. It keeps what the queue consumed. */
    val rawFrames: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The one transport that this adapter owns.
     *
     * The production adapter owns one transport for its process and closes it
     * at shutdown. The harness follows that rule, so a case exercises the real
     * transport over a real loopback backend.
     */
    private val http = JdkBackendHttp(config.backendBaseUrl)

    init {
        val reader =
            Thread {
                runCatching {
                    BufferedReader(InputStreamReader(clientInput, StandardCharsets.UTF_8))
                        .forEachLine { line ->
                            if (line.isNotBlank()) {
                                rawFrames.add(line)
                                frames.add(line)
                            }
                        }
                }
            }
        reader.isDaemon = true
        reader.start()

        val server = createMcpServer(config, http)
        val transport = StdioServerTransport(serverInput.asSource().buffered(), fromServer.asSink().buffered())
        scope.launch { server.createSession(transport) }
    }

    /** Send one frame. */
    fun send(frame: String) {
        toServer.write((frame + "\n").toByteArray(StandardCharsets.UTF_8))
        toServer.flush()
    }

    /** Send the initialize handshake. It returns the server information. */
    fun initialize(): JsonObject {
        send(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{""" +
                """"protocolVersion":"2025-11-25","capabilities":{},""" +
                """"clientInfo":{"name":"chat-mcp-test","version":"0.0.1"}}}""",
        )
        val answer = awaitResult(1)
        send("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        return answer
    }

    /** Call one tool and return its result object. */
    fun callTool(id: Int, name: String, arguments: String): JsonObject {
        send(
            """{"jsonrpc":"2.0","id":$id,"method":"tools/call","params":{""" +
                """"name":"$name","arguments":$arguments}}""",
        )
        return awaitResult(id)
    }

    /** Send the tools list request and return its result object. */
    fun listTools(id: Int): JsonObject {
        send("""{"jsonrpc":"2.0","id":$id,"method":"tools/list","params":{}}""")
        return awaitResult(id)
    }

    /** Wait for the answer that carries one id. */
    fun awaitResult(id: Int, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): JsonObject =
        awaitFrame(id, timeoutMillis)["result"]?.jsonObject
            ?: error("the answer for id $id carries no result")

    /** Wait for the whole frame that carries one id. */
    private fun awaitFrame(id: Int, timeoutMillis: Long): JsonObject {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) {
                error("no answer for id $id within $timeoutMillis ms")
            }
            val line = frames.poll(remaining, TimeUnit.NANOSECONDS)
            if (line == null) {
                error("no answer for id $id within $timeoutMillis ms")
            }
            val message = Json.parseToJsonElement(line).jsonObject
            val answered = (message["id"] as? JsonPrimitive)?.content
            if (answered == id.toString()) {
                return message
            }
        }
    }

    /**
     * Wait for the whole message that carries one id.
     *
     * A protocol error carries no result, so this reader returns the frame
     * itself. A caller that expects an error reads it here.
     */
    fun awaitMessage(id: Int, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): JsonObject =
        awaitFrame(id, timeoutMillis)

    /** Read the text of one tool result. */
    fun textOf(result: JsonObject): String = result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitiveText()

    /** Read the structured content of one tool result. */
    fun structuredOf(result: JsonObject): JsonObject = result["structuredContent"]!!.jsonObject

    /** Read the error flag of one tool result. */
    fun isError(result: JsonObject): Boolean = (result["isError"] as? JsonPrimitive)?.content == "true"

    /**
     * Read the application error data of one tool result.
     *
     * The MCP wire name of this field is `_meta`.
     */
    fun metaOf(result: JsonObject): JsonObject =
        result["_meta"]?.jsonObject
            ?: error("the tool result carries no application error data: $result")

    /** Report whether a tool result carries application error data. */
    fun hasMeta(result: JsonObject): Boolean = result["_meta"] != null

    override fun close() {
        scope.cancel()
        runCatching { toServer.close() }
        runCatching { fromServer.close() }
        http.close()
    }
}

private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveText(): String =
    (this as JsonPrimitive).content
