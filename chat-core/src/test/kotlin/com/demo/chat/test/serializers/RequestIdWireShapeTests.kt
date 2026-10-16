package com.demo.chat.test.serializers

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.domain.MessagePointerRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.DatabindException
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/**
 * The request ids that the RSocket server decodes. See `CHAT-oeyxthsy`.
 *
 * The server decodes with a Jackson 3 mapper built from the domain module, as
 * `RSocketServerConfiguration` builds it. A request type is generic in its key
 * type, and the handler cannot resolve that type. **So the module reads each
 * id by the rule that a `Key` id uses.** Without it, a UUID id decodes as a
 * `String`, and the registry lookup misses.
 */
class RequestIdWireShapeTests {

    private val jackson3: JsonMapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    private val room = UUID.fromString("97c138e2-fe87-380b-a18a-39dc56d5b48b")
    private val sender = UUID.fromString("8232ac69-1da4-3fa6-b962-0e3f88ca6ca7")

    private fun byId(json: String): ByIdRequest<*> = jackson3.readValue(json, ByIdRequest::class.java)

    private fun import(json: String): MessageImportRequest<*, *> = jackson3.readValue(json, MessageImportRequest::class.java)

    @Test
    fun `a UUID id decodes as a UUID`() {
        val read = byId("""{"type":"ByIdRequest","id":"$room"}""")

        assertThat(read.id).isEqualTo(room)
    }

    @Test
    fun `a long id decodes as a Long`() {
        val read = byId("""{"type":"ByIdRequest","id":1234567890123}""")

        assertThat(read.id).isEqualTo(1234567890123L)
    }

    // A small id decoded as an Integer before, and a Long key never equals an Integer key.
    @Test
    fun `a small long id decodes as a Long`() {
        val read = byId("""{"type":"ByIdRequest","id":7}""")

        assertThat(read.id).isEqualTo(7L)
    }

    @Test
    fun `the id request keeps its wire shape`() {
        val json = jackson3.writeValueAsString(ByIdRequest(room))

        assertThat(json).isEqualTo("""{"type":"ByIdRequest","id":"$room"}""")
        assertThat(byId(json)).isEqualTo(ByIdRequest(room))
    }

    @Test
    fun `an import request keeps every field and reads its ids as UUIDs`() {
        val time = Instant.parse("2026-10-08T10:00:00.001234Z")
        val sent = MessageImportRequest("imported", sender, room, time, "$room:one", publish = true)

        val read = import(jackson3.writeValueAsString(sent))

        assertThat(read).isEqualTo(sent)
        assertThat(read.from).isInstanceOf(UUID::class.java)
        assertThat(read.dest).isInstanceOf(UUID::class.java)
    }

    @Test
    fun `an import request keeps its wire shape`() {
        val time = Instant.parse("2026-10-08T10:00:00.001234Z")
        val json = jackson3.writeValueAsString(MessageImportRequest("imported", sender, room, time, "$room:one"))

        assertThat(json).isEqualTo(
            """{"type":"MessageImportRequest","msg":"imported","from":"$sender","dest":"$room",""" +
                """"timestamp":"2026-10-08T10:00:00.001234Z","messageId":"$room:one","publish":false}"""
        )
    }

    @Test
    fun `an import request with no publish field does not publish`() {
        val read = import(
            """{"type":"MessageImportRequest","msg":"imported","from":"$sender","dest":"$room",""" +
                """"timestamp":"2026-10-08T10:00:00Z","messageId":"$room:one"}"""
        )

        assertThat(read.publish).isFalse()
    }

    @Test
    fun `an import request with no message id fails and names the field`() {
        assertThatThrownBy {
            import(
                """{"type":"MessageImportRequest","msg":"imported","from":"$sender","dest":"$room",""" +
                    """"timestamp":"2026-10-08T10:00:00Z"}"""
            )
        }.isInstanceOf(DatabindException::class.java).hasMessageContaining("The request has no messageId.")
    }

    // The message text is a value, not an id, so a text that parses as a UUID stays text.
    @Test
    fun `an import text that looks like a UUID stays text`() {
        val time = Instant.parse("2026-10-08T10:00:00Z")
        val read = import(jackson3.writeValueAsString(MessageImportRequest(room.toString(), sender, room, time, "$room:one")))

        assertThat(read.msg).isEqualTo(room.toString())
    }

    @Test
    fun `an import request with long ids reads them as Longs`() {
        val read = import(
            """{"type":"MessageImportRequest","msg":"imported","from":1234567890123,"dest":1234567890124,""" +
                """"timestamp":"2026-10-08T10:00:00Z","messageId":"one","publish":false}"""
        )

        assertThat(read.from).isEqualTo(1234567890123L)
        assertThat(read.dest).isEqualTo(1234567890124L)
    }

    @Test
    fun `a membership request keeps its wire shape and reads its ids as UUIDs`() {
        val sent = MembershipRequest(sender, room)
        val json = jackson3.writeValueAsString(sent)

        assertThat(json).isEqualTo("""{"type":"MembershipRequest","uid":"$sender","roomId":"$room"}""")
        assertThat(jackson3.readValue(json, MembershipRequest::class.java)).isEqualTo(sent)
    }

    @Test
    fun `a send request keeps its wire shape and reads its ids as UUIDs`() {
        val sent = MessageSendRequest("sent", sender, room)
        val json = jackson3.writeValueAsString(sent)

        assertThat(json).isEqualTo("""{"type":"MessageSendRequest","msg":"sent","from":"$sender","dest":"$room"}""")
        assertThat(jackson3.readValue(json, MessageSendRequest::class.java)).isEqualTo(sent)
    }

    @Test
    fun `a submit request keeps its wire shape and reads its id as a UUID`() {
        val sent = MessageSubmitRequest("submitted", room, "rs-1")
        val json = jackson3.writeValueAsString(sent)

        assertThat(json).isEqualTo("""{"type":"MessageSubmitRequest","msg":"submitted","dest":"$room","requestId":"rs-1"}""")
        assertThat(jackson3.readValue(json, MessageSubmitRequest::class.java)).isEqualTo(sent)
    }

    @Test
    fun `a pointer request keeps its wire shape and reads its ids as UUIDs`() {
        val sent = MessagePointerRequest(room, sender, "Message", "ptr-1")
        val json = jackson3.writeValueAsString(sent)

        assertThat(json).isEqualTo(
            """{"type":"MessagePointerRequest","dest":"$room","targetId":"$sender","targetDomain":"Message","requestId":"ptr-1"}"""
        )
        assertThat(jackson3.readValue(json, MessagePointerRequest::class.java)).isEqualTo(sent)
    }

    // A send or submit text is a value, not an id, so a text that parses as a UUID stays text.
    @Test
    fun `a send or submit text that looks like a UUID stays text`() {
        val send = jackson3.readValue(jackson3.writeValueAsString(MessageSendRequest(room.toString(), sender, room)), MessageSendRequest::class.java)
        val submit = jackson3.readValue(jackson3.writeValueAsString(MessageSubmitRequest(room.toString(), room, "rs-1")), MessageSubmitRequest::class.java)

        assertThat(send.msg).isEqualTo(room.toString())
        assertThat(submit.msg).isEqualTo(room.toString())
    }

    @Test
    fun `the requests with long ids read them as Longs`() {
        val membership = jackson3.readValue("""{"type":"MembershipRequest","uid":7,"roomId":1234567890124}""", MembershipRequest::class.java)
        val send = jackson3.readValue("""{"type":"MessageSendRequest","msg":"sent","from":7,"dest":1234567890124}""", MessageSendRequest::class.java)
        val submit = jackson3.readValue("""{"type":"MessageSubmitRequest","msg":"submitted","dest":7,"requestId":"rs-1"}""", MessageSubmitRequest::class.java)
        val pointer = jackson3.readValue(
            """{"type":"MessagePointerRequest","dest":7,"targetId":1234567890124,"targetDomain":"Message","requestId":"ptr-1"}""",
            MessagePointerRequest::class.java,
        )

        assertThat(membership).isEqualTo(MembershipRequest(7L, 1234567890124L))
        assertThat(send).isEqualTo(MessageSendRequest("sent", 7L, 1234567890124L))
        assertThat(submit).isEqualTo(MessageSubmitRequest("submitted", 7L, "rs-1"))
        assertThat(pointer).isEqualTo(MessagePointerRequest(7L, 1234567890124L, "Message", "ptr-1"))
    }
}
