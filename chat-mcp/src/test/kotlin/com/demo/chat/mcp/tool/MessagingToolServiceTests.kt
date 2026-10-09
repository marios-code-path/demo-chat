package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.*
import com.demo.chat.mcp.config.KeyType
import com.demo.chat.mcp.config.LongId
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MessagingToolServiceTests {
    @TempDir lateinit var directory: Path
    private val transports = mutableListOf<JdkBackendHttp>()

    @AfterEach
    fun closeTransports() = transports.forEach(JdkBackendHttp::close)

    private fun service(backend: MessagingTestBackend): MessagingToolService {
        val credential = directory.resolve("credential.txt")
        Files.writeString(credential, "test-token")
        val config = testConfig(backend.origin, listOf(LongId(12345)), credential)
        val http = JdkBackendHttp(backend.origin).also(transports::add)
        return MessagingToolService(config, MessagingClient(config, http))
    }

    @Test
    fun `history and message reads use the expected routes`() {
        MessagingTestBackend().use { backend ->
            backend.answer("/message/list/12345", 200, messagingFixture(KeyType.LONG, "history.ndjson"))
            backend.answer("/message/id/1554361326074068992", 200, messagingFixture(KeyType.LONG, "message.json"))
            val service = service(backend)
            assertEquals(2, service.listMessages("12345").getValue("messages").jsonArray.size)
            assertEquals("hello λ", service.getMessage("1554361326074068992")
                .getValue("message").jsonObject.getValue("text").jsonPrimitive.content)
            assertEquals(listOf("/message/list/12345", "/message/id/1554361326074068992"), backend.requests.map { it.rawPath })
            assertTrue(backend.requests.all { it.method == "GET" })
        }
    }

    @Test
    fun `invalid request and command identifiers never reach HTTP`() {
        MessagingTestBackend().use { backend ->
            val service = service(backend)
            for (id in listOf("", "has space", "a\nb", "x".repeat(129), "λ", "a\u007f")) {
                assertThrows(MessagingInputException::class.java) { service.sendMessage("12345", "text", id) }
                assertThrows(MessagingInputException::class.java) { service.commandStatus(id) }
            }
            assertTrue(backend.requests.isEmpty())
        }
    }

    @Test
    fun `invalid object identifiers never reach HTTP`() {
        MessagingTestBackend().use { backend ->
            val service = service(backend)
            for (id in listOf("", "0", "01", "1.5", "+7", "9223372036854775808")) {
                assertThrows(MessagingInputException::class.java) { service.listMessages(id) }
                assertThrows(MessagingInputException::class.java) { service.getMessage(id) }
                assertThrows(MessagingInputException::class.java) { service.sendMessage(id, "text", "request") }
            }
            assertTrue(backend.requests.isEmpty())
        }
    }

    @Test
    fun `blank and oversized text never reaches HTTP`() {
        MessagingTestBackend().use { backend ->
            val service = service(backend)
            for (text in listOf("", " \n\t", "x".repeat(16385), "λ".repeat(8193))) {
                assertThrows(MessagingInputException::class.java) { service.sendMessage("12345", text, "request") }
            }
            assertTrue(backend.requests.isEmpty())
        }
    }

    @Test
    fun `exact UTF8 bounds and caller request identity are preserved across clients`() {
        MessagingTestBackend().use { backend ->
            backend.answer("/message/submit/12345", 201, messagingFixture(KeyType.LONG, "send-completed.json"))
            val first = service(backend)
            val second = service(backend)
            val text = "λ".repeat(8192)
            assertEquals(first.sendMessage("12345", text, "request:1"), second.sendMessage("12345", text, "request:1"))
            assertEquals(listOf("request:1", "request:1"), backend.requests.map { it.requestId })
            assertTrue(backend.requests.all { it.text == text && it.method == "POST" })
        }
    }

    @Test
    fun `unconfigured rooms are refused before HTTP`() {
        MessagingTestBackend().use { backend ->
            val service = service(backend)
            assertThrows(MessagingUnavailableException::class.java) { service.listMessages("999") }
            assertThrows(MessagingUnavailableException::class.java) { service.sendMessage("999", "text", "request") }
            assertTrue(backend.requests.isEmpty())
        }
    }

    @Test
    fun `a readable message outside configured rooms is withheld`() {
        MessagingTestBackend().use { backend ->
            val body = alteredFixture(KeyType.LONG, "message.json", listOf("message", "key", "key", "dest"), JsonPrimitive(999))
            backend.answer("/message/id/1554361326074068992", 200, body)
            val failure = assertThrows(MessagingUnavailableException::class.java) {
                service(backend).getMessage("1554361326074068992")
            }
            assertEquals(1, backend.requests.size)
            assertFalse(failure.message.orEmpty().contains("999"))
            assertFalse(failure.message.orEmpty().contains("hello"))
        }
    }

    @Test
    fun `mixed scope history returns no prefix`() {
        MessagingTestBackend().use { backend ->
            val hidden = alteredFixture(KeyType.LONG, "message.json", listOf("message", "key", "key", "dest"), JsonPrimitive(999))
            backend.answer("/message/list/12345", 200, messagingFixture(KeyType.LONG, "message.json").replace("\n", "") + "\n" + hidden)
            assertThrows(MessagingUnavailableException::class.java) { service(backend).listMessages("12345") }
        }
    }

    @Test
    fun `command identifiers enter one encoded path segment`() {
        MessagingTestBackend().use { backend ->
            val command = "a/b?c#d%"
            var body = alteredFixture(KeyType.LONG, "status.json", listOf("commandId"), JsonPrimitive(command))
            val source = Json.parseToJsonElement(body).jsonObject
            body = JsonObject(source + ("receipt" to JsonObject(source.getValue("receipt").jsonObject + ("commandId" to JsonPrimitive(command))))).toString()
            backend.answer("/message/command/a%2Fb%3Fc%23d%25", 200, body)
            assertEquals(command, service(backend).commandStatus(command).getValue("commandId").jsonPrimitive.content)
            assertEquals("/message/command/a%2Fb%3Fc%23d%25", backend.requests.single().rawPath)
        }
    }

    @Test
    fun `the client reads its credential before each request`() {
        MessagingTestBackend().use { backend ->
            backend.answer("/message/list/12345", 200, messagingFixture(KeyType.LONG, "history.ndjson"))
            val service = service(backend)
            service.listMessages("12345")
            Files.writeString(directory.resolve("credential.txt"), "later-token")
            service.listMessages("12345")
            assertEquals(listOf("Bearer test-token", "Bearer later-token"), backend.requests.map { it.credential })
        }
    }

    @Test
    fun `a malformed submission response retains the unknown request identity`() {
        MessagingTestBackend().use { backend ->
            backend.answer("/message/submit/12345", 201, "private malformed body")
            val failure = assertThrows(SubmissionUnknownException::class.java) {
                service(backend).sendMessage("12345", "text", "request:1")
            }
            assertEquals("request:1", failure.requestId)
            assertEquals(201, failure.status)
            assertFalse(failure.message.orEmpty().contains("private"))
        }
    }
}
