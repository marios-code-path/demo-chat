package com.demo.chat.test.portal

import com.demo.chat.shell.portal.OPEN_REGISTER_ENTRY
import com.demo.chat.shell.portal.POST_REGISTER_ENTRY
import com.demo.chat.shell.portal.PortalError
import com.demo.chat.shell.portal.PortalErrorCode
import com.demo.chat.shell.portal.PortalProtocol
import com.demo.chat.shell.portal.PortalReply
import com.demo.chat.shell.portal.PortalRequest
import com.demo.chat.shell.portal.PortalSocketServer
import com.demo.chat.shell.portal.PortalStartupException
import com.demo.chat.shell.portal.SocketIdentity
import com.demo.chat.shell.portal.lstatSocketIdentity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PortalSocketServerTests {
    private val mapper = ObjectMapper()
    private val protocol = PortalProtocol(mapper)
    private val clients = Executors.newCachedThreadPool()
    private val request = "{\"operation\":\"read-register\",\"index\":\"register-index\"}\n"
    private val okRecord = mapper.readTree("""{"type":"record","id":"1"}""")

    @TempDir
    lateinit var parent: Path

    @AfterEach
    fun stopClients() {
        clients.shutdownNow()
    }

    private fun socket(): Path = parent.resolve("portal.sock")

    private fun okReply(): PortalReply = PortalReply(0, listOf(okRecord), true)

    private fun writer(): PrintWriter = PrintWriter(OutputStream.nullOutputStream(), true)

    private fun frameOf(text: String): JsonNode {
        assertThat(text).endsWith("\n")
        return mapper.readTree(text.trimEnd('\n'))
    }

    private fun exchange(path: Path, frame: String): String =
        asyncExchange(path, frame).get(5, TimeUnit.SECONDS)

    private fun asyncExchange(path: Path, frame: String): Future<String> =
        clients.submit<String> {
            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(UnixDomainSocketAddress.of(path))
                val out = Channels.newOutputStream(channel)
                out.write(frame.toByteArray(StandardCharsets.UTF_8))
                out.flush()
                channel.shutdownOutput()
                String(Channels.newInputStream(channel).readBytes(), StandardCharsets.UTF_8)
            }
        }

    private fun awaitTrue(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            if (System.nanoTime() > end) throw AssertionError("condition not met in 5 s")
            Thread.sleep(10)
        }
    }

    private inner class Running(
        val deadline: Duration = Duration.ofSeconds(2),
        val identityOf: (Path) -> SocketIdentity? = ::lstatSocketIdentity,
        val handle: (PortalRequest, Duration) -> PortalReply = { _, _ -> okReply() },
        val diagnostics: PrintWriter = writer(),
    ) : AutoCloseable {
        val path = socket()
        val halts = CopyOnWriteArrayList<Int>()
        private val executor = Executors.newSingleThreadExecutor()
        val server = PortalSocketServer(path, protocol, handle, deadline, { halts += it }, diagnostics, identityOf)

        init {
            server.bind()
            executor.submit { server.serve() }
        }

        override fun close() {
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `bind creates a private socket and close removes it`() {
        val path = socket()
        val server = PortalSocketServer(path, protocol, { _, _ -> okReply() }, Duration.ofSeconds(2), {}, writer())

        server.bind()

        assertThat(Files.getPosixFilePermissions(path)).containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
        )
        server.close()
        assertThat(Files.exists(path)).isFalse()
    }

    @Test
    fun `bind refuses an occupied path and leaves it untouched`() {
        val path = socket()
        Files.writeString(path, "occupied")

        val server = PortalSocketServer(path, protocol, { _, _ -> okReply() }, Duration.ofSeconds(2), {}, writer())

        assertThatThrownBy { server.bind() }.isInstanceOf(PortalStartupException::class.java)
        assertThat(Files.readString(path)).isEqualTo("occupied")
    }

    @Test
    fun `bind refuses a stale socket and leaves it untouched`() {
        val path = socket()
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use {
            it.bind(UnixDomainSocketAddress.of(path))
        }
        assertThat(Files.exists(path)).isTrue()

        val server = PortalSocketServer(path, protocol, { _, _ -> okReply() }, Duration.ofSeconds(2), {}, writer())

        assertThatThrownBy { server.bind() }.isInstanceOf(PortalStartupException::class.java)
        assertThat(Files.exists(path)).isTrue()
    }

    @Test
    fun `bind refuses a parent that is not private`() {
        Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwxr-x---"))

        val server = PortalSocketServer(socket(), protocol, { _, _ -> okReply() }, Duration.ofSeconds(2), {}, writer())

        assertThatThrownBy { server.bind() }.isInstanceOf(PortalStartupException::class.java)
        assertThat(Files.exists(socket())).isFalse()
    }

    @Test
    fun `bind refuses an owner mismatch and leaves the path`() {
        val path = socket()
        val server = PortalSocketServer(
            path, protocol, { _, _ -> okReply() }, Duration.ofSeconds(2), {}, writer(),
            identityOf = { SocketIdentity("someone-else", "key") })

        assertThatThrownBy { server.bind() }.isInstanceOf(PortalStartupException::class.java)
        assertThat(Files.exists(path)).isTrue()
    }

    @Test
    fun `bind refuses an unavailable identity and leaves the path`() {
        val path = socket()
        val server = PortalSocketServer(
            path, protocol, { _, _ -> okReply() }, Duration.ofSeconds(2), {}, writer(),
            identityOf = { null })

        assertThatThrownBy { server.bind() }.isInstanceOf(PortalStartupException::class.java)
        assertThat(Files.exists(path)).isTrue()
    }

    @Test
    fun `close leaves a replaced path untouched`() {
        val running = Running()
        Files.delete(running.path)
        Files.writeString(running.path, "replacement")

        running.close()

        assertThat(Files.readString(running.path)).isEqualTo("replacement")
    }

    @Test
    fun `one request gets one typed reply and the connection closes`() {
        Running().use { running ->
            val frame = frameOf(exchange(running.path, request))

            assertThat(frame["status"].intValue()).isZero()
            assertThat(frame["lines"].size()).isEqualTo(1)
            assertThat(frame["lines"][0]["type"].textValue()).isEqualTo("record")
            assertThat(frame["complete"].booleanValue()).isTrue()
        }
    }

    @Test
    fun `a malformed frame gets invalid input`() {
        Running().use { running ->
            val frame = frameOf(exchange(running.path, "{}\n"))

            assertThat(frame["status"].isNull).isTrue()
            assertThat(frame["error"]["code"].textValue()).isEqualTo("INVALID_INPUT")
            assertThat(frame["lines"].size()).isZero()
        }
    }

    @Test
    fun `an oversized reply gets output too large`() {
        val huge = mapper.createObjectNode().put("type", "record").put("text", "x".repeat(4_194_304))

        Running(handle = { _, _ -> PortalReply(0, listOf(huge), true) }).use { running ->
            val frame = frameOf(exchange(running.path, request))

            assertThat(frame["status"].isNull).isTrue()
            assertThat(frame["error"]["code"].textValue()).isEqualTo("OUTPUT_TOO_LARGE")
            assertThat(frame["lines"].size()).isZero()
        }
    }

    @Test
    fun `a second concurrent request gets busy without another read`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()

        Running(handle = { _, _ ->
            reads.incrementAndGet()
            started.countDown()
            release.await()
            okReply()
        }).use { running ->
            val first = asyncExchange(running.path, request)
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()

            val second = frameOf(exchange(running.path, request))
            assertThat(second["status"].isNull).isTrue()
            assertThat(second["error"]["code"].textValue()).isEqualTo("BUSY")
            assertThat(reads.get()).isEqualTo(1)

            release.countDown()
            assertThat(frameOf(first.get(5, TimeUnit.SECONDS))["status"].intValue()).isZero()
        }
    }

    @Test
    fun `sequential requests each receive a reply`() {
        val reads = AtomicInteger()

        Running(handle = { _, _ ->
            reads.incrementAndGet()
            okReply()
        }).use { running ->
            val first = frameOf(exchange(running.path, request))
            val second = frameOf(exchange(running.path, request))

            assertThat(first["status"].intValue()).isZero()
            assertThat(second["status"].intValue()).isZero()
            assertThat(reads.get()).isEqualTo(2)
        }
    }

    @Test
    fun `a protocol failure does not suppress the next reply`() {
        val reads = AtomicInteger()

        Running(handle = { _, _ ->
            reads.incrementAndGet()
            okReply()
        }).use { running ->
            val failed = frameOf(exchange(running.path, "{\"operation\":\"invalid\",\"index\":\"register-index\"}\n"))
            val next = frameOf(exchange(running.path, request))

            assertThat(failed["error"]["code"].textValue()).isEqualTo(PortalErrorCode.INVALID_INPUT.name)
            assertThat(next["status"].intValue()).isZero()
            assertThat(reads.get()).isEqualTo(1)
        }
    }

    @Test
    fun `a read past the deadline calls halt and refuses later reads`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        val halts = CopyOnWriteArrayList<Int>()
        val path = socket()
        val server = PortalSocketServer(path, protocol, { _, _ ->
            reads.incrementAndGet()
            started.countDown()
            release.await()
            okReply()
        }, Duration.ofMillis(150), { halts += it }, writer())
        val executor = Executors.newSingleThreadExecutor()
        server.bind()
        executor.submit { server.serve() }

        try {
            val client = asyncExchange(path, request)
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
            awaitTrue { halts.isNotEmpty() }
            assertThat(halts).containsExactly(124)

            runCatching { exchange(path, request) }
            assertThat(reads.get()).isEqualTo(1)
            assertThat(frameOf(client.get(5, TimeUnit.SECONDS))["error"]["code"].textValue())
                .isEqualTo("TIMEOUT")
        } finally {
            release.countDown()
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `a timeout reply calls halt and refuses later reads`() {
        val reads = AtomicInteger()
        val halts = CopyOnWriteArrayList<Int>()
        val path = socket()
        val server = PortalSocketServer(path, protocol, { _, _ ->
            reads.incrementAndGet()
            PortalReply(124, emptyList(), false, PortalError(PortalErrorCode.TIMEOUT))
        }, Duration.ofSeconds(2), { halts += it }, writer())
        val executor = Executors.newSingleThreadExecutor()
        server.bind()
        executor.submit { server.serve() }

        try {
            val frame = frameOf(exchange(path, request))
            assertThat(frame["status"].intValue()).isEqualTo(124)
            assertThat(frame["error"]["code"].textValue()).isEqualTo("TIMEOUT")
            awaitTrue { halts.isNotEmpty() }
            assertThat(halts).containsExactly(124)

            runCatching { exchange(path, request) }
            assertThat(reads.get()).isEqualTo(1)
        } finally {
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `a blocked reply write halts at the deadline`() {
        val reads = AtomicInteger()
        val halts = CopyOnWriteArrayList<Int>()
        val path = socket()
        val huge = mapper.createObjectNode().put("type", "record").put("text", "x".repeat(4_000_000))
        val server = PortalSocketServer(path, protocol, { _, _ ->
            reads.incrementAndGet()
            PortalReply(0, listOf(huge), true)
        }, Duration.ofMillis(200), { halts += it }, writer())
        val executor = Executors.newSingleThreadExecutor()
        server.bind()
        executor.submit { server.serve() }
        val client = SocketChannel.open(StandardProtocolFamily.UNIX)

        try {
            runCatching { client.setOption(StandardSocketOptions.SO_RCVBUF, 4096) }
            client.connect(UnixDomainSocketAddress.of(path))
            val out = Channels.newOutputStream(client)
            out.write(request.toByteArray(StandardCharsets.UTF_8))
            out.flush()
            client.shutdownOutput()

            awaitTrue { halts.isNotEmpty() }

            assertThat(halts).containsExactly(124)
            assertThat(reads.get()).isEqualTo(1)
        } finally {
            client.close()
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `a failed request writes one diagnostic without request text`() {
        val captured = ByteArrayOutputStream()
        val diagnostics = PrintWriter(captured, true, StandardCharsets.UTF_8)

        Running(diagnostics = diagnostics).use { running ->
            exchange(running.path, "{\"operation\":\"read-register\",\"index\":\"register-index\",\"secret-marker\":\"x\"}\n")
        }

        val text = captured.toString(StandardCharsets.UTF_8)
        assertThat(text).contains("INVALID_INPUT")
        assertThat(text).doesNotContain("secret-marker")
    }

    @Test
    fun `a failed read names its cause on the one diagnostic line`() {
        val captured = ByteArrayOutputStream()
        val diagnostics = PrintWriter(captured, true, StandardCharsets.UTF_8)
        val failed = PortalReply(
            status = 1,
            lines = emptyList(),
            complete = false,
            error = PortalError(PortalErrorCode.READ_FAILED),
            detail = "the index topic is missing\nsecond line",
        )

        Running(handle = { _, _ -> failed }, diagnostics = diagnostics).use { running ->
            exchange(running.path, request)
        }

        val lines = captured.toString(StandardCharsets.UTF_8).lines().filter { it.isNotBlank() }
        assertThat(lines).containsExactly("portal: READ_FAILED status=1 detail=the index topic is missing second line")
    }

    @Test
    fun `the server serves an open request`() {
        val open = mapper.readTree("""{"type":"open","state":"created","key":"CHAT-a","topic":"register-index-fr-snap","pointer_id":"m1"}""")

        Running(handle = { request, _ ->
            if (request.operation == OPEN_REGISTER_ENTRY) PortalReply(0, listOf(open), true)
            else PortalReply(2, emptyList(), false, PortalError(PortalErrorCode.INVALID_INPUT))
        }).use { running ->
            val frame = frameOf(
                exchange(
                    running.path,
                    """{"operation":"open-register-entry","index":"register-index","key":"CHAT-a","short":"snap","title":"Snapshot"}""" + "\n",
                ),
            )

            assertThat(frame["status"].intValue()).isZero()
            assertThat(frame["lines"][0]["type"].textValue()).isEqualTo("open")
        }
    }

    @Test
    fun `the server serves a post request`() {
        val post = mapper.readTree("""{"type":"post","key":"CHAT-a","topic":"register-index-fr-snap","message_id":"m2"}""")

        Running(handle = { request, _ ->
            if (request.operation == POST_REGISTER_ENTRY) PortalReply(0, listOf(post), true)
            else PortalReply(2, emptyList(), false, PortalError(PortalErrorCode.INVALID_INPUT))
        }).use { running ->
            val frame = frameOf(
                exchange(
                    running.path,
                    """{"operation":"post-register-entry","index":"register-index","key":"CHAT-a","text":"hello","requestId":"r-1"}""" + "\n",
                ),
            )

            assertThat(frame["status"].intValue()).isZero()
            assertThat(frame["lines"][0]["type"].textValue()).isEqualTo("post")
        }
    }
}
