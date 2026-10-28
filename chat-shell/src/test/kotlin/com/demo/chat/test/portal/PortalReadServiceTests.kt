package com.demo.chat.test.portal

import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import com.demo.chat.shell.portal.PortalErrorCode
import com.demo.chat.shell.portal.PortalReadService
import com.demo.chat.shell.portal.PortalRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class PortalReadServiceTests {
    private val mapper = ObjectMapper()
    private val deadline = Duration.ofSeconds(30)

    private fun service(
        index: (String, Duration) -> String = { _, _ -> "" },
        topic: (String) -> String = { "" },
    ) = PortalReadService(readIndex = index, readTopic = topic, mapper = mapper)

    private fun request(topic: String? = null) = PortalRequest("read-register", "register-index", topic)

    @Test
    fun `an index read keeps the record order`() {
        val first = """{"type":"record","id":"1","topic":"register-index"}"""
        val second = """{"type":"record","id":"2","topic":"lobby"}"""

        val reply = service(index = { _, _ -> "$first\n$second" }).read(request(), deadline)

        assertThat(reply.status).isEqualTo(0)
        assertThat(reply.complete).isTrue()
        assertThat(reply.error).isNull()
        assertThat(reply.lines.map { it["id"].textValue() }).containsExactly("1", "2")
    }

    @Test
    fun `a topic read adds the record type first`() {
        val line = """{"id":"m1","topic":"lobby","sender":"a","timestamp":"2026-10-26T00:00:00Z","text":"hi"}"""

        val reply = service(topic = { line }).read(request("lobby"), deadline)

        assertThat(reply.status).isEqualTo(0)
        assertThat(reply.complete).isTrue()
        val record = reply.lines.single()
        assertThat(record["type"].textValue()).isEqualTo("record")
        assertThat(record.fieldNames().asSequence().first()).isEqualTo("type")
        assertThat(record["id"].textValue()).isEqualTo("m1")
        assertThat(record["text"].textValue()).isEqualTo("hi")
    }

    @Test
    fun `an empty read is complete and carries the remaining deadline`() {
        var remaining: Duration? = null

        val reply = service(index = { _, limit -> remaining = limit; "" }).read(request(), Duration.ofSeconds(7))

        assertThat(remaining).isEqualTo(Duration.ofSeconds(7))
        assertThat(reply.status).isEqualTo(0)
        assertThat(reply.complete).isTrue()
        assertThat(reply.lines).isEmpty()
        assertThat(reply.error).isNull()
    }

    @Test
    fun `a topic read treats empty output as a complete empty result`() {
        val reply = service(topic = { "" }).read(request("lobby"), deadline)

        assertThat(reply.status).isEqualTo(0)
        assertThat(reply.complete).isTrue()
        assertThat(reply.lines).isEmpty()
    }

    @Test
    fun `a malformed line is invalid output and returns no partial lines`() {
        val valid = """{"type":"record","id":"1"}"""

        val reply = service(index = { _, _ -> "$valid\nnot json" }).read(request(), deadline)

        assertThat(reply.status).isNull()
        assertThat(reply.complete).isFalse()
        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.INVALID_OUTPUT)
        assertThat(reply.lines).isEmpty()
    }

    @Test
    fun `a line with data after the first JSON value is invalid output`() {
        listOf(
            """{"type":"record","id":"1"} {"type":"record","id":"2"}""",
            """{"type":"record","id":"1"} trailing""",
        ).forEach { line ->
            val reply = service(index = { _, _ -> line }).read(request(), deadline)

            assertThat(reply.status).`as`(line).isNull()
            assertThat(reply.error?.code).`as`(line).isEqualTo(PortalErrorCode.INVALID_OUTPUT)
            assertThat(reply.lines).`as`(line).isEmpty()
        }
    }

    @Test
    fun `a topic line with a type is invalid output`() {
        listOf(
            """{"type":"warning","kind":"denied-topic","topic":"lobby","reason":"Access Denied"}""",
            """{"type":"record","id":"m1","topic":"lobby","sender":"a","timestamp":"t","text":"hi"}""",
        ).forEach { line ->
            val reply = service(topic = { line }).read(request("lobby"), deadline)

            assertThat(reply.status).`as`(line).isNull()
            assertThat(reply.error?.code).`as`(line).isEqualTo(PortalErrorCode.INVALID_OUTPUT)
            assertThat(reply.lines).`as`(line).isEmpty()
        }
    }

    @Test
    fun `an index line needs a known type`() {
        listOf(
            """{"id":"1"}""",
            """{"type":"other","id":"1"}""",
            """[1]""",
        ).forEach { line ->
            val reply = service(index = { _, _ -> line }).read(request(), deadline)

            assertThat(reply.status).`as`(line).isNull()
            assertThat(reply.error?.code).`as`(line).isEqualTo(PortalErrorCode.INVALID_OUTPUT)
            assertThat(reply.lines).`as`(line).isEmpty()
        }
    }

    @Test
    fun `a duplicate pointer warning keeps the read complete`() {
        val warning = """{"type":"warning","kind":"duplicate-pointer","key":"-"}"""

        val reply = service(index = { _, _ -> warning }).read(request(), deadline)

        assertThat(reply.status).isEqualTo(0)
        assertThat(reply.complete).isTrue()
        assertThat(reply.lines.single()["kind"].textValue()).isEqualTo("duplicate-pointer")
    }

    @Test
    fun `a denied index makes the read incomplete`() {
        val warning = """{"type":"warning","kind":"denied-index","topic":"register-index","reason":"Access Denied"}"""

        val reply = service(index = { _, _ -> warning }).read(request(), deadline)

        assertThat(reply.status).isEqualTo(0)
        assertThat(reply.complete).isFalse()
        assertThat(reply.lines.single()["kind"].textValue()).isEqualTo("denied-index")
    }

    @Test
    fun `a denied topic makes the read incomplete`() {
        val warning = """{"type":"warning","kind":"denied-topic","topic":"lobby","reason":"Access Denied"}"""

        val reply = service(index = { _, _ -> warning }).read(request(), deadline)

        assertThat(reply.status).isEqualTo(0)
        assertThat(reply.complete).isFalse()
        assertThat(reply.lines).hasSize(1)
    }

    @Test
    fun `an unknown warning makes the read incomplete`() {
        val service = PortalReadService(
            readIndex = { _, _ -> """{"type":"warning","kind":"new-kind"}""" },
            readTopic = { "" },
            mapper = ObjectMapper())

        val reply = service.read(request(), deadline)

        assertThat(reply.complete).isFalse()
        assertThat(reply.status).isEqualTo(0)
        assertThat(reply.lines).hasSize(1)
    }

    @Test
    fun `a shell unavailable status maps to unavailable`() {
        val reply = service(index = { _, _ -> throw ShellExit(ShellExitStatus.UNAVAILABLE, "core is down") })
            .read(request(), deadline)

        assertThat(reply.status).isEqualTo(69)
        assertThat(reply.complete).isFalse()
        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.UNAVAILABLE)
        assertThat(reply.lines).isEmpty()
    }

    @Test
    fun `a wrapped timeout maps to timeout on both read paths`() {
        val wrapped = RuntimeException("outer", ShellExit(ShellExitStatus.TIMEOUT, "the read passed its limit"))

        val indexReply = service(index = { _, _ -> throw wrapped }).read(request(), deadline)
        val topicReply = service(topic = { throw wrapped }).read(request("lobby"), deadline)

        listOf(indexReply, topicReply).forEach { reply ->
            assertThat(reply.status).isEqualTo(124)
            assertThat(reply.complete).isFalse()
            assertThat(reply.error?.code).isEqualTo(PortalErrorCode.TIMEOUT)
            assertThat(reply.lines).isEmpty()
        }
    }

    @Test
    fun `another shell failure keeps its status`() {
        val reply = service(index = { _, _ -> throw ShellExit(ShellExitStatus.LOGIN, "no login") })
            .read(request(), deadline)

        assertThat(reply.status).isEqualTo(77)
        assertThat(reply.complete).isFalse()
        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.READ_FAILED)
        assertThat(reply.lines).isEmpty()
    }

    @Test
    fun `a failure carries the deepest cause message as its detail`() {
        val wrapped = RuntimeException("outer", IllegalStateException("the index topic is missing"))

        val reply = service(index = { _, _ -> throw wrapped }).read(request(), deadline)

        assertThat(reply.error?.code).isEqualTo(PortalErrorCode.READ_FAILED)
        assertThat(reply.detail).isEqualTo("the index topic is missing")
    }
}
