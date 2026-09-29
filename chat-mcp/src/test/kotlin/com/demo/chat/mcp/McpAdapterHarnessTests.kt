package com.demo.chat.mcp

import com.demo.chat.mcp.tool.FakeTopicBackend
import com.demo.chat.mcp.tool.topicBody
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pinned client harness.
 *
 * A real MCP client, at the pinned version, connects to the adapter over stdio
 * and calls both tools. The adapter runs on the JVM. **This is not the native
 * acceptance test.** See Task 6 in the plan.
 *
 * The harness is a Node program under `chat-mcp/src/test/client/`. It prints one
 * JSON transcript. This class reads that transcript and makes every assertion.
 */
class McpAdapterHarnessTests {
    private val hiddenName: String = "refused-topic-name"

    /** The pinned client. The adapter serves the revision this release declares. */
    private val pinnedSdkVersion: String = "1.31.0"

    /** The protocol revision this adapter serves. See decision D1 in the plan. */
    private val servedRevision: String = "2025-11-25"

    private fun harnessDirectory(): Path {
        val directory = Path.of(System.getProperty("user.dir")).resolve("src/test/client")
        assertTrue(
            Files.isRegularFile(directory.resolve("harness.mjs")),
            "the harness is missing at $directory",
        )
        assertTrue(
            Files.isRegularFile(directory.resolve("package-lock.json")),
            "the harness lock file is missing at $directory. A second machine cannot reproduce it.",
        )
        return directory
    }

    /**
     * The classpath of the child JVM.
     *
     * Surefire writes a booter jar and names the real entries in its manifest.
     * `surefire.test.class.path` carries them directly, so it is preferred when
     * it is present.
     */
    private fun childClasspath(): String =
        System.getProperty("surefire.test.class.path")
            ?: System.getProperty("java.class.path")

    /** Write the configuration that points the adapter at one backend. */
    private fun writeConfig(
        directory: Path,
        backend: FakeTopicBackend,
        topicIds: String,
    ): Path {
        val credential = directory.resolve("credential.txt")
        Files.writeString(credential, "test-credential")
        val config = directory.resolve("adapter.properties")
        Files.writeString(
            config,
            """
            backendBaseUrl=${backend.origin}
            credentialFile=credential.txt
            keyType=long
            topicIds=$topicIds
            """.trimIndent(),
        )
        return config
    }

    /** Run the harness against an adapter that serves one backend. */
    private fun runHarness(
        config: Path,
        readId: String,
        refusedId: String,
    ): JsonObject {
        val client = harnessDirectory()
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val command =
            listOf(
                java,
                "-cp",
                childClasspath(),
                "com.demo.chat.mcp.McpAdapterMainKt",
                "--config",
                config.toAbsolutePath().toString(),
            )
        val arguments =
            listOf(
                "node",
                client.resolve("harness.mjs").toString(),
                "--read-id",
                readId,
                "--refused-id",
                refusedId,
                "--",
            ) + command

        val output = Files.createTempFile("chat-mcp-harness-out", ".json")
        val errors = Files.createTempFile("chat-mcp-harness-err", ".txt")
        val process =
            try {
                ProcessBuilder(arguments)
                    .directory(client.toFile())
                    .redirectOutput(output.toFile())
                    .redirectError(errors.toFile())
                    .start()
            } catch (failure: java.io.IOException) {
                throw AssertionError(
                    "the harness needs Node on the PATH. Node could not start: ${failure.message}",
                    failure,
                )
            }

        val finished = process.waitFor(90, TimeUnit.SECONDS)
        assertTrue(finished, "the harness did not finish within 90 seconds")

        val text = Files.readString(output)
        val errorText = Files.readString(errors)
        assertEquals(0, process.exitValue(), "the harness failed: $errorText")
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun JsonElement.text(): String = (this as JsonPrimitive).content

    private fun JsonObject.array(name: String): JsonArray = getValue(name).jsonArray

    /** Read the error flag. The SDK omits it when it is false. */
    private fun JsonObject.isError(): Boolean = (this["isError"] as? JsonPrimitive)?.content == "true"

    private fun JsonObject.string(name: String): String =
        (getValue(name) as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw AssertionError("the transcript field $name is not a string: $this")

    /** One backend, one allowed topic and one denied topic, for both tests. */
    private fun withBackend(body: (FakeTopicBackend, Path, String, String) -> Unit) {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))
            backend.answer("22", 403, topicBody(22, 7, hiddenName))
            val directory = Files.createTempDirectory("chat-mcp-harness")
            val config = writeConfig(directory, backend, "11,22")
            body(backend, config, "11", "22")
        }
    }

    /**
     * Rules 1 to 5 of Task 5.
     *
     * The pinned client connects, completes discovery, and calls each tool. Each
     * answer is compared against the backend that served it.
     */
    @Test
    fun `the pinned client discovers the tools and calls each one`() {
        withBackend { _, config, readId, refusedId ->
            val transcript = runHarness(config, readId, refusedId)

            // Rule 1. The pin is the version that declares the served revision.
            assertEquals(pinnedSdkVersion, transcript.string("sdkVersion"))
            assertEquals(servedRevision, transcript.string("sdkLatestProtocolVersion"))

            // Rule 2. Record the Node version.
            val node = transcript.string("nodeVersion")
            println("chat-mcp harness: Node $node, SDK ${transcript.string("sdkVersion")}")
            assertTrue(node.startsWith("v"), "the Node version is not recorded: '$node'")

            // Rule 3. The connection completed. The revision is the one D1 names.
            assertEquals("null", transcript.getValue("connectError").toString())
            assertEquals(servedRevision, transcript.string("protocolVersion"))
            assertEquals("demo-chat-mcp", transcript.getValue("serverVersion").jsonObject.string("name"))

            // Rule 9. The adapter ran on the JVM build. This is not native.
            println("chat-mcp harness: the adapter ran on the JVM build. This is not the native acceptance test.")

            // Rule 4. Discovery lists exactly the two tools.
            val tools = transcript.array("tools").map { it.jsonObject.string("name") }
            assertEquals(listOf("chat_list_topics", "chat_get_topic"), tools)

            // Rule 5. Each call is compared against the backend that served it.
            val calls = transcript.array("calls").map { it.jsonObject }
            assertEquals(3, calls.size)

            val list = calls[0]
            assertEquals("chat_list_topics", list.string("name"))
            assertFalse(list.isError())
            val topics = list.getValue("structuredContent").jsonObject.array("topics")
            assertEquals(1, topics.size)
            assertEquals("11", topics[0].jsonObject.string("id"))
            assertEquals("alpha", topics[0].jsonObject.string("name"))

            val read = calls[1]
            assertEquals("chat_get_topic", read.string("name"))
            assertFalse(read.isError())
            val topic = read.getValue("structuredContent").jsonObject.getValue("topic").jsonObject
            assertEquals("11", topic.string("id"))
            assertEquals("7", topic.string("root"))
            assertEquals("alpha", topic.string("name"))

            // The denied topic is refused, and it is named nowhere.
            val refused = calls[2]
            assertEquals("chat_get_topic", refused.string("name"))
            assertTrue(refused.isError())
            assertFalse(refused.string("text").contains(hiddenName))
        }
    }

    /**
     * Rules 6, 7 and 8 of Task 5.
     *
     * Stdout carries protocol frames alone. One diagnostic line reaches stderr
     * for each call. The process exits within the bound after stdin closes.
     */
    @Test
    fun `stdout is pure, stderr is one line per call, and the exit is bounded`() {
        withBackend { _, config, readId, refusedId ->
            val transcript = runHarness(config, readId, refusedId)

            // Rule 6. Every stdout line is a JSON-RPC frame, and nothing else.
            assertTrue(
                transcript.array("stdoutParseFailures").isEmpty(),
                "stdout carried a line that is not JSON: ${transcript.array("stdoutParseFailures")}",
            )
            val frames = transcript.array("stdoutLines").map { it.text() }
            assertEquals(5, frames.size, "the adapter wrote an unexpected stdout line count: $frames")
            frames.forEach { line ->
                val frame = Json.parseToJsonElement(line).jsonObject
                assertEquals("2.0", frame.string("jsonrpc"), "stdout carried a non protocol line: $line")
                assertFalse(line.contains(hiddenName), "stdout carried the denied topic name: $line")
                assertFalse(line.contains("test-credential"), "stdout carried the credential: $line")
            }

            // Rule 7. stderr carries adapter diagnostics alone. A library line
            // that carries no prefix fails here. Measured on 2026-09-29: without
            // the two start-up suppressions, `kotlin-logging` writes to stdout
            // and the SLF4J reporter writes three warning lines to stderr.
            val stderr = transcript.array("stderrLines").map { it.text() }
            assertTrue(
                stderr.all { it.startsWith("chat-mcp: ") },
                "stderr carried a line that is not an adapter diagnostic: $stderr",
            )

            // Rule 7. One diagnostic line for each call.
            val diagnostics = stderr.filter { it.contains("chat-mcp: ") }
            val callLines =
                diagnostics.filter {
                    it.contains("chat_list_topics") || it.contains("chat_get_topic")
                }
            assertEquals(3, callLines.size, "stderr did not carry one line per call: $diagnostics")
            assertEquals(1, callLines.count { it.contains("chat_list_topics") })
            assertEquals(2, callLines.count { it.contains("chat_get_topic") })
            assertTrue(
                callLines.none { it.contains(hiddenName) },
                "stderr carried the denied topic name: $diagnostics",
            )
            assertTrue(
                diagnostics.none { it.contains("test-credential") },
                "stderr carried the credential: $diagnostics",
            )

            // Rule 8. Stdin closed, and the process exited within the bound.
            val exit = transcript.getValue("exit").jsonObject
            assertEquals("true", exit.getValue("withinBound").toString())
            assertEquals("0", exit.getValue("code").toString())
            assertTrue(
                exit.getValue("millis").toString().toLong() <= SHUTDOWN_BOUND_MILLIS,
                "the exit took longer than $SHUTDOWN_BOUND_MILLIS ms: $exit",
            )
        }
    }

    /** The harness is pinned, and the lock file holds the same version. */
    @Test
    fun `the harness lock file pins the same client version`() {
        val lock = Json.parseToJsonElement(
            Files.readString(harnessDirectory().resolve("package-lock.json")),
        ).jsonObject
        val packages = lock.getValue("packages").jsonObject
        val sdk = packages.getValue("node_modules/@modelcontextprotocol/sdk").jsonObject

        assertEquals(pinnedSdkVersion, sdk.string("version"))
        assertNotNull(packages[""], "the lock file carries no root package")
    }
}
