package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByStringRequest
import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@EnabledIf("com.demo.chat.test.deploy.memory.RestAgentSelectionTests#webfluxPresent")
@Import(McpMessageStoreGateConfiguration::class)
@DirtiesContext
@Execution(ExecutionMode.SAME_THREAD)
@TestPropertySource(properties = [
    "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
    "spring.application.name=test-mcp-messaging",
    "app.primary=REST", "app.server.proto=rest",
    "app.key.type=long", "app.nodeid=1",
    "app.service.core.key=memory", "app.service.core.pubsub=memory",
    "app.service.core.index=lucene", "app.service.core.persistence=memory",
    "app.service.core.secrets=memory", "app.service.composite=true", "app.command.bus=memory",
    "app.service.composite.auth=true", "app.command.completion.requirement=P,I",
    "app.command.completion.timeout=100ms",
    "app.service.security.userdetails=true", "app.users.create=true",
    "app.controller.topic=true", "app.controller.user=true", "app.controller.message=true",
    "app.init.initial-users[Claude].handle=Claude",
    "app.init.initial-users[Claude].name=Claude",
    "app.init.initial-users[Claude].image-uri=chatimg://agent.png",
    "app.security.required-scope=chat.mcp",
    "app.security.agents[0].client-id=client-agent", "app.security.agents[0].username=Agent",
    "app.security.agents[1].client-id=client-claude", "app.security.agents[1].username=Claude",
])
class McpMessagingDeploymentTests {
    @LocalServerPort var port: Int = 0
    @Autowired lateinit var gate: MessageStoreGate
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @TempDir lateinit var directory: Path

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

    @AfterEach
    fun releasePersistence() {
        gate.open()
        http.close()
    }

    @Test
    fun `an authenticated MCP submission reaches both read routes with the token sender`() {
        val room = readyRoom()
        val sent = submit(room, "hello through MCP")
        val receipt = sent.getValue("receipt").jsonObject
        awaitStatus(room, receipt.text("commandId"))
        val message = readBoth(room, receipt.getValue("messageKey").jsonObject.text("id"), "hello through MCP")
        val agent = composite.userService().findByUsername(ByStringRequest("Agent"))
            .filter { it.handle == "Agent" }.single().block(Duration.ofSeconds(10))!!
        assertThat(message.text("senderId")).isEqualTo(agent.key.id.toString())
    }

    @Test
    fun `an agent joins an existing room before MCP history and send succeed`() {
        val room = createRoom("client-claude")
        assertThat(request("client-agent", "PUT", "/topic/join/$room").statusCode()).isBetween(200, 299)
        probe(room)
        val sent = submit(room, "joined room message")
        val receipt = sent.getValue("receipt").jsonObject
        awaitStatus(room, receipt.text("commandId"))
        readBoth(room, receipt.getValue("messageKey").jsonObject.text("id"), "joined room message")
    }

    @Test
    fun `a held persistence attempt returns pending and later succeeds through MCP`() {
        val room = readyRoom()
        gate.arm()
        try {
            val sent = submit(room, "held message")
            assertThat(sent.text("outcome")).isEqualTo("PENDING")
            assertThat(gate.awaitEntry()).describedAs("the real persistence handler reached the gate").isTrue()
            val receipt = sent.getValue("receipt").jsonObject
            val persistence = sent.getValue("backends").jsonObject.getValue("PERSISTENCE").jsonObject
            assertThat(persistence.text("state")).isIn("PENDING", "UNCERTAIN")
            gate.open()
            awaitStatus(room, receipt.text("commandId"))
            readBoth(room, receipt.getValue("messageKey").jsonObject.text("id"), "held message")
        } finally {
            gate.open()
        }
    }

    @Test
    fun `separate adapter processes repeat one command and reject changed content`() {
        val room = readyRoom()
        val requestId = "repeat:${UUID.randomUUID()}"
        val first = submit(room, "one stored message", requestId)
        val second = submit(room, "one stored message", requestId)
        assertThat(second.getValue("receipt")).isEqualTo(first.getValue("receipt"))
        val command = first.getValue("receipt").jsonObject.text("commandId")
        awaitStatus(room, command)
        val conflict = calls(room, "chat_send_message" to args(
            "topicId" to room, "text" to "changed message", "requestId" to requestId,
        )).single()
        assertThat(errorCode(conflict)).isEqualTo("REQUEST_CONFLICT")
        val otherRoom = readyRoom()
        val changedRoom = calls("$room,$otherRoom", "chat_send_message" to args(
            "topicId" to otherRoom, "text" to "one stored message", "requestId" to requestId,
        )).single()
        assertThat(errorCode(changedRoom)).isEqualTo("REQUEST_CONFLICT")
        val history = success(calls(room, "chat_list_messages" to args("topicId" to room)).single())
        val stored = history.getValue("messages").jsonArray.filter { it.jsonObject.text("text") == "one stored message" }
        assertThat(stored).hasSize(1)
    }

    @Test
    fun `the server refuses room access and the adapter hides a globally readable message`() {
        val allowed = readyRoom()
        val denied = createRoom("client-claude")
        val hiddenText = "private${UUID.randomUUID()}"
        val raw = request("client-claude", "POST", "/message/submit/$denied", hiddenText,
            "text/plain", "hidden:${UUID.randomUUID()}")
        assertThat(raw.statusCode()).isIn(201, 202)
        val receipt = Json.parseToJsonElement(raw.body()).jsonObject.getValue("receipt").jsonObject
        val messageId = receipt.getValue("messageKey").jsonObject.getValue("key").jsonObject.text("id")
        awaitRawStatus("client-claude", receipt.text("commandId"))
        val readable = request("client-agent", "GET", "/message/id/$messageId")
        assertThat(readable.statusCode()).isEqualTo(200)
        assertThat(readable.body()).contains(hiddenText)

        // Both rooms are configured so the server evaluates history and send access.
        val refused = calls("$allowed,$denied",
            "chat_list_messages" to args("topicId" to denied),
            "chat_send_message" to args("topicId" to denied, "text" to "refused", "requestId" to "denied:${UUID.randomUUID()}"),
        )
        assertThat(refused.map(::errorCode)).containsExactly("NOT_AVAILABLE", "NOT_AVAILABLE")
        val hidden = calls(allowed, "chat_get_message" to args("messageId" to messageId)).single()
        assertThat(errorCode(hidden)).isEqualTo("NOT_AVAILABLE")
        assertThat(hidden.getValue("structuredContent")).isEqualTo(JsonNull)
        assertThat(hidden.getValue("text").toString() + hidden.getValue("meta").toString())
            .doesNotContain(hiddenText, messageId, denied)
    }

    @Test
    fun `another owner cannot distinguish an existing command from a missing command`() {
        val room = readyRoom()
        val receipt = submit(room, "owner private command").getValue("receipt").jsonObject
        awaitStatus(room, receipt.text("commandId"))
        val results = calls(room,
            "chat_get_command_status" to args("commandId" to receipt.text("commandId")),
            "chat_get_command_status" to args("commandId" to "missing:${UUID.randomUUID()}"),
            clientId = "client-claude",
        )
        assertThat(results.map(::errorCode)).containsExactly("NOT_AVAILABLE", "NOT_AVAILABLE")
        assertThat(results[0].getValue("meta")).isEqualTo(results[1].getValue("meta"))
        assertThat(results.all { it.getValue("structuredContent") == JsonNull }).isTrue()
    }

    private fun readyRoom(): String = createRoom("client-agent").also(::probe)

    private fun createRoom(clientId: String): String {
        val name = "mcp" + UUID.randomUUID().toString().replace("-", "")
        val response = request(clientId, "POST", "/topic/new", "{\"type\":\"ByNameRequest\",\"name\":\"$name\"}", "application/json")
        assertThat(response.statusCode()).isEqualTo(201)
        return Json.parseToJsonElement(response.body()).jsonObject.getValue("key").jsonObject.text("id")
    }

    private fun probe(room: String) {
        assertThat(request("client-agent", "GET", "/message/list/$room", accept = "application/x-ndjson").statusCode()).isEqualTo(200)
        val response = request("client-agent", "POST", "/message/submit/$room", "setup probe", "text/plain", "setup:${UUID.randomUUID()}")
        assertThat(response.statusCode()).isIn(201, 202)
        val command = Json.parseToJsonElement(response.body()).jsonObject.getValue("receipt").jsonObject.text("commandId")
        awaitRawStatus("client-agent", command)
    }

    private fun request(clientId: String, method: String, path: String, body: String? = null,
                        contentType: String = "application/json", requestId: String? = null,
                        accept: String = "application/json"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer ${AgentTestTokens.mint(signingKeyPath, clientId, 300_000)}")
            .header("Accept", accept)
        if (requestId != null) builder.header("Idempotency-Key", requestId)
        if (body != null) builder.header("Content-Type", contentType)
        builder.method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun awaitRawStatus(clientId: String, command: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        do {
            val response = request(clientId, "GET", "/message/command/$command")
            assertThat(response.statusCode()).isEqualTo(200)
            if (complete(Json.parseToJsonElement(response.body()).jsonObject)) return
            TimeUnit.MILLISECONDS.sleep(25)
        } while (System.nanoTime() < deadline)
        error("the setup command did not complete before the deadline")
    }

    private fun awaitStatus(room: String, command: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
        do {
            val status = success(calls(room, "chat_get_command_status" to args("commandId" to command)).single())
            if (complete(status)) return
        } while (System.nanoTime() < deadline)
        error("the command did not complete before the deadline")
    }

    private fun complete(status: JsonObject): Boolean = listOf("PERSISTENCE", "INDEX").all {
        status.getValue("backends").jsonObject.getValue(it).jsonObject.text("state") == "SUCCEEDED"
    }

    private fun submit(room: String, text: String, requestId: String = "mcp:${UUID.randomUUID()}"): JsonObject =
        success(calls(room, "chat_send_message" to args("topicId" to room, "text" to text, "requestId" to requestId)).single())

    private fun readBoth(room: String, id: String, expected: String): JsonObject {
        val answers = calls(room,
            "chat_get_message" to args("messageId" to id),
            "chat_list_messages" to args("topicId" to room),
        ).map(::success)
        val message = answers[0].getValue("message").jsonObject
        assertThat(message.text("text")).isEqualTo(expected)
        assertThat(message.text("topicId")).isEqualTo(room)
        assertThat(message.getValue("messageKey").jsonObject.text("id")).isEqualTo(id)
        assertThat(answers[1].getValue("messages").jsonArray).contains(message)
        return message
    }

    private fun success(answer: JsonObject): JsonObject {
        assertThat(answer.getValue("isError")).describedAs(answer.toString()).isEqualTo(JsonPrimitive(false))
        assertThat(answer.getValue("meta")).isEqualTo(JsonNull)
        val result = answer.getValue("structuredContent").jsonObject
        assertThat(Json.parseToJsonElement(answer.text("text"))).isEqualTo(result)
        return result
    }

    private fun errorCode(answer: JsonObject): String {
        assertThat(answer.getValue("isError")).isEqualTo(JsonPrimitive(true))
        return answer.getValue("meta").jsonObject.text("code")
    }

    private fun args(vararg fields: Pair<String, String>) = buildJsonObject {
        fields.forEach { (name, value) -> put(name, value) }
    }

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content

    private fun calls(rooms: String, vararg calls: Pair<String, JsonObject>, clientId: String = "client-agent"): List<JsonObject> {
        val run = Files.createTempDirectory(directory, "adapter-")
        val token = run.resolve("credential.txt")
        Files.writeString(token, AgentTestTokens.mint(signingKeyPath, clientId, 300_000))
        val config = run.resolve("adapter.properties")
        Files.writeString(config, "backendBaseUrl=http://127.0.0.1:$port\ncredentialFile=$token\nkeyType=long\ntopicIds=$rooms\nenableSend=true\n")
        val script = run.resolve("calls.json")
        Files.writeString(script, JsonArray(calls.map { (name, arguments) -> buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        } }).toString())
        val root = generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .first { Files.isRegularFile(it.resolve("chat-mcp/src/test/client/harness.mjs")) }
        // The deployment classpath adds Logback. The adapter runtime normally has no logging provider.
        val logging = run.resolve("logback.xml")
        Files.writeString(logging, "<configuration><root level=\"OFF\"/></configuration>")
        val output = run.resolve("transcript.json")
        val errors = run.resolve("errors.log")
        val classpath = System.getProperty("surefire.test.class.path") ?: System.getProperty("java.class.path")
        val process = ProcessBuilder("node", root.resolve("chat-mcp/src/test/client/harness.mjs").toString(),
            "--calls-file", script.toString(), "--", Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-Dlogback.configurationFile=$logging", "-cp", classpath, "com.demo.chat.mcp.McpAdapterMainKt", "--config", config.toString())
            .redirectOutput(output.toFile()).redirectError(errors.toFile()).start()
        try {
            assertThat(process.waitFor(10L + 35L * calls.size, TimeUnit.SECONDS)).describedAs("the harness exit deadline").isTrue()
            assertThat(process.exitValue()).describedAs(Files.readString(errors)).isZero()
            val transcript = Json.parseToJsonElement(Files.readString(output)).jsonObject
            assertThat(transcript.getValue("connectError")).isEqualTo(JsonNull)
            assertThat(transcript.getValue("stdoutParseFailures").jsonArray).isEmpty()
            transcript.getValue("stdoutLines").jsonArray.forEach {
                assertThat(Json.parseToJsonElement(it.jsonPrimitive.content).jsonObject.text("jsonrpc")).isEqualTo("2.0")
            }
            val exit = transcript.getValue("exit").jsonObject
            assertThat(exit.getValue("withinBound")).isEqualTo(JsonPrimitive(true))
            assertThat(exit.getValue("code")).isEqualTo(JsonPrimitive(0))
            val answers = transcript.getValue("calls").jsonArray.map { it.jsonObject }
            assertThat(answers).hasSize(calls.size)
            return answers
        } finally {
            if (process.isAlive) {
                val children = process.descendants().toList()
                children.forEach { it.destroyForcibly() }
                process.destroyForcibly()
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue()
                children.forEach { child ->
                    child.onExit().get(5, TimeUnit.SECONDS)
                    assertThat(child.isAlive).isFalse()
                }
            }
            Files.deleteIfExists(token)
        }
    }

    companion object {
        private val signingKeyPath = AgentTestTokens.createKey()
        @JvmStatic @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.security.jwt.jwk-path") { signingKeyPath }
        }
    }
}
