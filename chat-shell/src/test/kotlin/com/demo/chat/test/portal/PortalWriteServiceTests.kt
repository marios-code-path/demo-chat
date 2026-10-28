package com.demo.chat.test.portal

import com.demo.chat.shell.portal.OPEN_REGISTER_ENTRY
import com.demo.chat.shell.portal.POST_REGISTER_ENTRY
import com.demo.chat.shell.portal.PortalErrorCode
import com.demo.chat.shell.portal.PortalRequest
import com.demo.chat.shell.portal.PortalWriteService
import com.demo.chat.shell.portal.RegisterGateway
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class PortalWriteServiceTests {
    private val mapper = ObjectMapper()
    private val deadline: Duration = Duration.ofSeconds(30)

    @Test
    fun `open creates a topic joins it and posts one pointer`() {
        val gateway = FakeGateway()

        val reply = service(gateway).open(openRequest(), deadline)

        assertThat(reply.error).isNull()
        assertThat(reply.status).isZero()
        assertThat(reply.lines.single()["type"].textValue()).isEqualTo("open")
        assertThat(reply.lines.single()["state"].textValue()).isEqualTo("created")
        assertThat(reply.lines.single()["topic"].textValue()).isEqualTo("register-index-fr-snap")
        assertThat(reply.lines.single()["pointer_id"].textValue()).isEqualTo("m2")
        assertThat(gateway.calls).containsExactly(
            "addRoom:register-index-fr-snap",
            "join:register-index-fr-snap",
            "post:register-index",
        )
    }

    @Test
    fun `open writes the pointer line with an empty metadata field`() {
        val gateway = FakeGateway()

        service(gateway).open(openRequest(), deadline)

        assertThat(gateway.postedTexts).containsExactly("CHAT-a | register-index-fr-snap | | Snapshot")
    }

    @Test
    fun `open answers exists when the key already has a pointer`() {
        val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-old", "t1", "1")) })

        val reply = service(gateway).open(openRequest(), deadline)

        assertThat(reply.error).isNull()
        assertThat(reply.lines.single()["state"].textValue()).isEqualTo("exists")
        assertThat(reply.lines.single()["topic"].textValue()).isEqualTo("register-index-fr-old")
        assertThat(gateway.calls).containsExactly("join:register-index-fr-old")
    }

    @Test
    fun `open answers exists when a concurrent open wins the pointer post`() {
        val gateway = FakeGateway(failAddRoom = true) { read ->
            if (read == 1) emptyList() else listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1"))
        }

        val reply = service(gateway).open(openRequest(), deadline)

        assertThat(reply.error).isNull()
        assertThat(reply.lines.single()["state"].textValue()).isEqualTo("exists")
        assertThat(gateway.calls).first().isEqualTo("addRoom:register-index-fr-snap")
    }

    @Test
    fun `open refuses a topic that another key owns`() {
        val gateway = FakeGateway(index = { listOf(pointer("CHAT-b", "register-index-fr-snap", "t1", "1")) })

        val reply = service(gateway).open(openRequest(), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.WRITE_FAILED)
        assertThat(reply.status).isEqualTo(1)
        assertThat(reply.lines).isEmpty()
        assertThat(gateway.calls).isEmpty()
    }

    @Test
    fun `open joins the register once when the index is denied`() {
        val gateway = FakeGateway(denied = true, index = { listOf(warning("denied-index")) })

        val reply = service(gateway).open(openRequest(), deadline)

        assertThat(gateway.calls.first()).isEqualTo("join:register-index")
        assertThat(reply.lines.single()["state"].textValue()).isEqualTo("created")
    }

    @Test
    fun `open answers write failed when the index stays denied`() {
        val gateway = FakeGateway(denied = true, denyAfterJoin = true, index = { listOf(warning("denied-index")) })

        val reply = service(gateway).open(openRequest(), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.WRITE_FAILED)
        assertThat(reply.lines).isEmpty()
        assertThat(gateway.calls).containsExactly("join:register-index")
    }

    @Test
    fun `open refuses a key that cannot stand in a pointer line`() {
        val gateway = FakeGateway()

        val reply = service(gateway).open(openRequest(key = "a|b"), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.INVALID_INPUT)
        assertThat(reply.status).isEqualTo(2)
        assertThat(gateway.calls).isEmpty()
    }

    @Test
    fun `open refuses a pointer line that does not hold four fields`() {
        val gateway = FakeGateway(
            index = { listOf(mapper.readTree("""{"type":"record","topic":"register-index","id":"t1","timestamp":"1","text":"broken"}""")) },
        )

        val reply = service(gateway).open(openRequest(), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.WRITE_FAILED)
        assertThat(gateway.calls).isEmpty()
    }

    @Test
    fun `open answers write failed when the readback never shows the pointer`() {
        val gateway = FakeGateway(hidePost = true)

        val reply = service(gateway).open(openRequest(), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.WRITE_FAILED)
        assertThat(reply.status).isEqualTo(75)
        assertThat(reply.lines).isEmpty()
    }

    @Test
    fun `open refuses a topic name past 64 characters`() {
        val gateway = FakeGateway()
        val longShort = "s".repeat(60)

        val reply = service(gateway).open(openRequest(short = longShort), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.INVALID_INPUT)
        assertThat(gateway.calls).isEmpty()
    }

    @Test
    fun `post joins the pointer topic and posts the text`() {
        val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1")) })
        gateway.messages = { listOf(record("m2", "register-index-fr-snap")) }

        val reply = service(gateway).post(postRequest(), deadline)

        assertThat(reply.error).isNull()
        assertThat(reply.status).isZero()
        assertThat(reply.lines.single()["type"].textValue()).isEqualTo("post")
        assertThat(reply.lines.single()["message_id"].textValue()).isEqualTo("m2")
        assertThat(reply.lines.single()["topic"].textValue()).isEqualTo("register-index-fr-snap")
        assertThat(gateway.calls).containsExactly("join:register-index-fr-snap", "post:register-index-fr-snap")
        assertThat(gateway.postedTexts).containsExactly("hello")
    }

    @Test
    fun `post refuses a key with no pointer`() {
        val gateway = FakeGateway()

        val reply = service(gateway).post(postRequest(), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.NOT_OPEN)
        assertThat(reply.status).isEqualTo(1)
        assertThat(reply.lines).isEmpty()
        assertThat(gateway.calls).isEmpty()
    }

    @Test
    fun `post reuses a given request id so a retry adds no entry`() {
        val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1")) })
        gateway.messages = { listOf(record("m2", "register-index-fr-snap")) }

        val reply = service(gateway).post(postRequest(requestId = "r-1"), deadline)

        assertThat(gateway.postedRequestIds).containsExactly("r-1")
        assertThat(reply.lines.single()["request_id"].textValue()).isEqualTo("r-1")
    }

    @Test
    fun `post generates a request id when the request holds none`() {
        val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1")) })
        gateway.messages = { listOf(record("m2", "register-index-fr-snap")) }

        val reply = service(gateway).post(postRequest(), deadline)

        assertThat(gateway.postedRequestIds).hasSize(1)
        assertThat(gateway.postedRequestIds.single()).isNotEmpty()
        assertThat(reply.lines.single()["request_id"].textValue()).isEqualTo(gateway.postedRequestIds.single())
    }

    @Test
    fun `post answers unconfirmed when the read never shows the message`() {
        val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1")) })
        gateway.messages = { emptyList() }

        val reply = service(gateway).post(postRequest(), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.UNCONFIRMED)
        assertThat(reply.status).isEqualTo(75)
        assertThat(reply.lines).isEmpty()
    }

    @Test
    fun `post refuses a text past the byte limit`() {
        val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1")) })

        val reply = service(gateway).post(postRequest(text = "a".repeat(3073)), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.INVALID_INPUT)
        assertThat(reply.status).isEqualTo(2)
        assertThat(gateway.calls).isEmpty()
    }

    private fun openRequest(key: String = "CHAT-a", short: String = "snap"): PortalRequest =
        PortalRequest(
            operation = OPEN_REGISTER_ENTRY,
            index = "register-index",
            key = key,
            short = short,
            title = "Snapshot",
        )

    private fun postRequest(
        key: String = "CHAT-a",
        text: String = "hello",
        requestId: String? = null,
    ): PortalRequest =
        PortalRequest(
            operation = POST_REGISTER_ENTRY,
            index = "register-index",
            key = key,
            text = text,
            requestId = requestId,
        )

    private fun service(gateway: FakeGateway): PortalWriteService =
        PortalWriteService(
            register = "register-index",
            gateway = gateway,
            mapper = mapper,
            pause = { gateway.advance(it) },
            now = { gateway.now },
        )

    private fun pointer(key: String, topic: String, id: String, timestamp: String): JsonNode =
        mapper.readTree(
            """{"type":"record","topic":"register-index","id":"$id","timestamp":"$timestamp",""" +
                """"text":"$key | $topic | | Snapshot"}""",
        )

    private fun warning(kind: String): JsonNode =
        mapper.readTree("""{"type":"warning","kind":"$kind","reason":"Access Denied"}""")

    private fun record(id: String, topic: String): JsonNode =
        mapper.readTree("""{"id":"$id","topic":"$topic","text":"hello"}""")

    private inner class FakeGateway(
        private val denied: Boolean = false,
        private val denyAfterJoin: Boolean = false,
        private val failAddRoom: Boolean = false,
        private val hidePost: Boolean = false,
        private val index: (Int) -> List<JsonNode> = { emptyList() },
    ) : RegisterGateway {
        val calls = mutableListOf<String>()
        val postedTexts = mutableListOf<String>()
        val postedRequestIds = mutableListOf<String>()
        var now: Long = 0
        var receipt: JsonNode = mapper.readTree("""{"messageId":"m2","commandId":"c1","requestId":"r-1"}""")
        var messages: (String) -> List<JsonNode> = { emptyList() }
        private var reads = 0
        private var joined = false
        private var posted: JsonNode? = null

        fun advance(millis: Long) {
            now += millis
        }

        override fun readIndex(deadline: Duration): List<JsonNode> {
            reads += 1
            val readable = !(denied && joined && !denyAfterJoin)
            val lines = if (readable) index(reads).toMutableList() else mutableListOf()
            if (posted != null && !hidePost) lines.add(posted!!)
            return lines
        }

        override fun join(topic: String) {
            calls += "join:$topic"
            if (topic == "register-index") joined = true
        }

        override fun addRoom(name: String) {
            calls += "addRoom:$name"
            if (failAddRoom) throw IllegalStateException("the room exists")
        }

        override fun post(topic: String, text: String, requestId: String): JsonNode {
            calls += "post:$topic"
            postedTexts += text
            postedRequestIds += requestId
            posted = pointer("CHAT-a", "register-index-fr-snap", receipt["messageId"].textValue(), "2")
            return mapper.createObjectNode().apply {
                receipt.fields().forEachRemaining { (name, value) -> set<JsonNode>(name, value) }
                put("requestId", requestId)
            }
        }

        override fun readMessages(topic: String): List<JsonNode> = messages(topic)
    }
}
