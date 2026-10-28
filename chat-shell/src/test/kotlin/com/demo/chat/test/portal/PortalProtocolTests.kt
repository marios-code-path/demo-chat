package com.demo.chat.test.portal

import com.demo.chat.shell.portal.PortalError
import com.demo.chat.shell.portal.PortalErrorCode
import com.demo.chat.shell.portal.PortalProtocol
import com.demo.chat.shell.portal.PortalProtocolException
import com.demo.chat.shell.portal.PortalReply
import com.demo.chat.shell.portal.PortalRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class PortalProtocolTests {
    private val mapper = ObjectMapper()
    private val protocol = PortalProtocol(mapper)

    @Test
    fun `a topic request keeps its selector and topic`() {
        val input = """{"operation":"read-register","index":"register-index","topic":"CHAT-123"}
"""

        assertThat(protocol.readRequest(input.byteInputStream()))
            .isEqualTo(PortalRequest("read-register", "register-index", "CHAT-123"))
    }

    @Test
    fun `a request without a topic selects the index`() {
        val input = """{"operation":"read-register","index":"123e4567-e89b-12d3-a456-426614174000"}
"""

        assertThat(protocol.readRequest(input.byteInputStream()).topic).isNull()
    }

    @Test
    fun `empty malformed and nonterminated frames are refused`() {
        refuse(byteArrayOf())
        refuse("{}".toByteArray())
        refuse("{\n".toByteArray())
        refuse("[]\n".toByteArray())
    }

    @Test
    fun `invalid UTF-8 is refused`() {
        refuse(byteArrayOf(0xC3.toByte(), 0x28, 0x0A))
    }

    @Test
    fun `unknown and repeated fields are refused`() {
        refuse("""{"operation":"read-register","index":"index","command":"whoami"}
""".toByteArray())
        refuse("""{"operation":"read-register","index":"index","index":"other"}
""".toByteArray())
    }

    @Test
    fun `non-string and invalid names are refused`() {
        refuse("""{"operation":"read-register","index":12}
""".toByteArray())
        refuse("""{"operation":"read-register","index":"-index"}
""".toByteArray())
        refuse("""{"operation":"read-register","index":"index","topic":null}
""".toByteArray())
        refuse("""{"operation":"read-register","index":"index","topic":"a_b"}
""".toByteArray())
        refuse("""{"operation":"other","index":"index"}
""".toByteArray())
    }

    @Test
    fun `a second frame or bytes after the frame are refused`() {
        val request = """{"operation":"read-register","index":"index"}
"""
        refuse((request + request).toByteArray())
        refuse((request + "x").toByteArray())
        refuse("""{"operation":"read-register","index":"index"} {}
""".toByteArray())
    }

    @Test
    fun `the request limit counts UTF-8 bytes and the newline`() {
        val largestName = "a".repeat(64)
        val body = """{"operation":"read-register","index":"$largestName"}"""
        val atLimit = body + " ".repeat(4096 - body.toByteArray().size - 1) + "\n"

        assertThat(atLimit.toByteArray()).hasSize(4096)
        assertThat(protocol.readRequest(atLimit.byteInputStream()).index).isEqualTo(largestName)
        refuse((atLimit.dropLast(1) + " \n").toByteArray())
        refuse("""{"operation":"read-register","index":"${"a".repeat(65)}"}
""".toByteArray())
    }

    @Test
    fun `an open request carries key short and title`() {
        val frame = """{"operation":"open-register-entry","index":"register-index","key":"CHAT-a","short":"snap","title":"Snapshot"}""" + "\n"

        val request = protocol.readRequest(frame.byteInputStream())

        assertThat(request.key).isEqualTo("CHAT-a")
        assertThat(request.short).isEqualTo("snap")
        assertThat(request.title).isEqualTo("Snapshot")
        assertThat(request.text).isNull()
    }

    @Test
    fun `a post request carries key text and request id`() {
        val frame = """{"operation":"post-register-entry","index":"register-index","key":"CHAT-a","text":"hello","requestId":"r-1"}""" + "\n"

        val request = protocol.readRequest(frame.byteInputStream())

        assertThat(request.key).isEqualTo("CHAT-a")
        assertThat(request.text).isEqualTo("hello")
        assertThat(request.requestId).isEqualTo("r-1")
    }

    @Test
    fun `a post request can omit the request id`() {
        val frame = """{"operation":"post-register-entry","index":"register-index","key":"CHAT-a","text":"hello"}""" + "\n"

        val request = protocol.readRequest(frame.byteInputStream())

        assertThat(request.requestId).isNull()
    }

    @Test
    fun `a read request refuses write fields`() {
        refuse("""{"operation":"read-register","index":"index","key":"CHAT-a"}
""".toByteArray())
    }

    @Test
    fun `an open request needs exactly its three fields`() {
        refuse("""{"operation":"open-register-entry","index":"index","key":"CHAT-a","short":"snap"}
""".toByteArray())
        refuse("""{"operation":"open-register-entry","index":"index","key":"CHAT-a","short":"snap","title":"S","text":"hello"}
""".toByteArray())
    }

    @Test
    fun `a post request refuses a non-string text`() {
        refuse("""{"operation":"post-register-entry","index":"index","key":"CHAT-a","text":5,"requestId":"r-1"}
""".toByteArray())
    }

    @Test
    fun `a success reply is one typed JSON frame`() {
        val output = ByteArrayOutputStream()
        val record = mapper.readTree("""{"type":"record","id":"m1"}""")

        protocol.writeReply(output, PortalReply(0, listOf(record), true))

        val bytes = output.toByteArray()
        assertThat(bytes.last()).isEqualTo('\n'.code.toByte())
        val frame = mapper.readTree(bytes.copyOf(bytes.size - 1))
        assertThat(frame["status"].intValue()).isZero()
        assertThat(frame["lines"][0]).isEqualTo(record)
        assertThat(frame["complete"].booleanValue()).isTrue()
        assertThat(frame.has("error")).isFalse()
    }

    @Test
    fun `a failure reply carries code and status`() {
        val output = ByteArrayOutputStream()

        protocol.writeReply(output, PortalReply(124, emptyList(), false, PortalError(PortalErrorCode.TIMEOUT)))

        val frame = mapper.readTree(output.toByteArray())
        assertThat(frame["status"].intValue()).isEqualTo(124)
        assertThat(frame["complete"].booleanValue()).isFalse()
        assertThat(frame["error"]["code"].textValue()).isEqualTo("TIMEOUT")
    }

    @Test
    fun `a portal failure keeps an explicit null status`() {
        val output = ByteArrayOutputStream()

        protocol.writeReply(output, PortalReply(null, emptyList(), false, PortalError(PortalErrorCode.UNAVAILABLE)))

        val frame = mapper.readTree(output.toByteArray())
        assertThat(frame.has("status")).isTrue()
        assertThat(frame["status"].isNull).isTrue()
        assertThat(frame["complete"].booleanValue()).isFalse()
        assertThat(frame["error"]["code"].textValue()).isEqualTo("UNAVAILABLE")
    }

    @Test
    fun `a reply at the exact byte limit is accepted`() {
        val seed = ByteArrayOutputStream()
        val emptyRecord = mapper.createObjectNode().put("type", "record").put("text", "")
        protocol.writeReply(seed, PortalReply(0, listOf(emptyRecord), true))
        val text = "x".repeat(4_194_304 - seed.size())
        val record = mapper.createObjectNode().put("type", "record").put("text", text)
        val output = ByteArrayOutputStream()

        protocol.writeReply(output, PortalReply(0, listOf(record), true))

        assertThat(output.size()).isEqualTo(4_194_304)
        assertThat(output.toByteArray().last()).isEqualTo('\n'.code.toByte())
    }

    @Test
    fun `an oversized reply writes no partial frame`() {
        val output = ByteArrayOutputStream()
        val record = mapper.createObjectNode().put("type", "record").put("text", "x".repeat(4_194_304))

        assertThatThrownBy { protocol.writeReply(output, PortalReply(0, listOf(record), true)) }
            .isInstanceOfSatisfying(PortalProtocolException::class.java) {
                assertThat(it.code).isEqualTo(PortalErrorCode.OUTPUT_TOO_LARGE)
            }
        assertThat(output.size()).isZero()
    }

    private fun refuse(input: ByteArray) {
        assertThatThrownBy { protocol.readRequest(input.inputStream()) }
            .isInstanceOfSatisfying(PortalProtocolException::class.java) {
                assertThat(it.code).isEqualTo(PortalErrorCode.INVALID_INPUT)
            }
    }
}
