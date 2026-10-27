package com.demo.chat.shell.portal

import java.io.IOException
import java.io.PrintWriter
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.Channels
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/** The owner and file key of one bound socket path, read with `lstat`. */
data class SocketIdentity(val owner: String, val fileKey: String)

/** Read the owner and file key of [path] without following a link. A missing value answers null. */
fun lstatSocketIdentity(path: Path): SocketIdentity? =
    try {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val key = attributes.fileKey() ?: return null
        SocketIdentity(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).name, key.toString())
    } catch (_: Exception) {
        null
    }

/** A startup failure. The message names the rule, not a socket request. */
class PortalStartupException(message: String) : RuntimeException(message)

/**
 * Serves one read-only portal request per Unix socket connection.
 *
 * The parent directory must be private to the process account.
 * The portal refuses every existing socket path, including a stale socket.
 * It records the owner and file key after bind and removes the path only when both still match.
 * One request runs at a time. A second concurrent request receives `BUSY` with no backend read.
 * The [deadline] starts at accept and covers parsing, the backend read, and the reply write.
 * A timed-out attempt enters one terminal state. Only that attempt cleans up and calls [halt].
 * Failed requests write one bounded diagnostic with a code and a status to [diagnostics].
 */
class PortalSocketServer(
    private val socket: Path,
    private val protocol: PortalProtocol,
    private val read: (PortalRequest, Duration) -> PortalReply,
    private val deadline: Duration,
    private val halt: (Int) -> Unit,
    private val diagnostics: PrintWriter,
    private val identityOf: (Path) -> SocketIdentity? = ::lstatSocketIdentity,
) : AutoCloseable {
    @Volatile
    private var channel: ServerSocketChannel? = null

    @Volatile
    private var recorded: SocketIdentity? = null

    @Volatile
    private var closed = false

    private val terminal = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private val halted = AtomicBoolean(false)
    private val replyLock = ReentrantLock()

    private val workers = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "portal-worker").apply { isDaemon = true }
    }
    private val timers = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "portal-timer").apply { isDaemon = true }
    }
    private val hardStops = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "portal-hard-stop").apply { isDaemon = true }
    }

    /** Check the path, bind, restrict access, and record the socket identity. */
    fun bind() {
        if (channel != null) failStartup("the portal is already bound")
        if (!socket.isAbsolute) failStartup("the socket path must be absolute: $socket")
        val parent = socket.parent ?: failStartup("the socket path needs a parent: $socket")
        requirePrivateParent(parent)
        if (Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) failStartup("the socket path exists: $socket")

        val opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            opened.bind(UnixDomainSocketAddress.of(socket))
            Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-------"))
            val identity = identityOf(socket) ?: failStartup("the socket identity is unavailable: $socket")
            if (identity.owner != currentUser()) failStartup("the socket owner is not ${currentUser()}: $socket")
            recorded = identity
            channel = opened
        } catch (error: Exception) {
            runCatching { opened.close() }
            throw error
        }
    }

    /** Accept requests until [close] or a timeout ends the server. */
    fun serve() {
        val server = channel ?: failStartup("the portal must bind first")
        while (!closed && !terminal.get()) {
            val client = try {
                server.accept()
            } catch (_: ClosedChannelException) {
                break
            } catch (_: AsynchronousCloseException) {
                break
            } catch (_: ClosedByInterruptException) {
                break
            } catch (_: IOException) {
                if (closed || terminal.get()) break else continue
            }
            if (closed || terminal.get()) {
                closeQuietly(client)
                break
            }
            dispatch(client)
        }
    }

    override fun close() {
        cleanup()
        workers.shutdownNow()
        timers.shutdownNow()
        hardStops.shutdownNow()
    }

    private fun dispatch(client: SocketChannel) {
        if (!busy.compareAndSet(false, true)) {
            diagnostic(PortalErrorCode.BUSY, null)
            writeFailure(client, PortalErrorCode.BUSY)
            drainQuietly(client)
            closeQuietly(client)
            return
        }
        val acceptedAt = System.nanoTime()
        val replied = AtomicBoolean(false)
        val timer = timers.schedule({ terminate(client, timeoutReply(), replied) }, deadline.toMillis().coerceAtLeast(1), TimeUnit.MILLISECONDS)
        workers.execute {
            try {
                handle(client, acceptedAt, replied)
            } finally {
                timer.cancel(false)
                if (!terminal.get()) busy.set(false)
                closeQuietly(client)
            }
        }
    }

    private fun handle(client: SocketChannel, acceptedAt: Long, replied: AtomicBoolean) {
        val request = try {
            protocol.readRequest(Channels.newInputStream(client))
        } catch (error: PortalProtocolException) {
            failing(client, error.code, null, replied)
            return
        } catch (_: Exception) {
            if (!terminal.get()) failing(client, PortalErrorCode.UNAVAILABLE, null, replied)
            return
        }
        if (terminal.get()) return

        val reply = try {
            read(request, remainingSince(acceptedAt))
        } catch (_: Exception) {
            if (!terminal.get()) failing(client, PortalErrorCode.UNAVAILABLE, null, replied)
            return
        }
        if (reply.error?.code == PortalErrorCode.TIMEOUT) {
            terminate(client, reply, replied)
            return
        }
        if (terminal.get()) return

        val failure = writeOnce(client, reply, replied)
        if (failure != null) {
            failing(client, failure, null, replied)
        } else if (reply.error != null) {
            diagnostic(reply.error.code, reply.status)
        }
    }

    /** Write one reply. Answer the protocol code when the reply is too large. Answer null when done or skipped. */
    private fun writeOnce(client: SocketChannel, reply: PortalReply, replied: AtomicBoolean): PortalErrorCode? {
        replyLock.lock()
        try {
            if (terminal.get() || replied.get()) return null
            return try {
                protocol.writeReply(Channels.newOutputStream(client), reply)
                replied.set(true)
                null
            } catch (error: PortalProtocolException) {
                error.code
            } catch (_: Exception) {
                null
            }
        } finally {
            replyLock.unlock()
        }
    }

    /**
     * Enter the timeout state and stop the process.
     *
     * A reply write owns [replyLock] while it runs. A timed-out server cannot wait for that write.
     * This method closes the client first when the lock is busy, so the write ends.
     * A complete reply wins when [replied] is true for this connection.
     */
    private fun terminate(client: SocketChannel, reply: PortalReply, replied: AtomicBoolean) {
        if (!replyLock.tryLock()) {
            closeQuietly(client)
            replyLock.lock()
        }
        try {
            if (replied.get()) return
            if (!terminal.compareAndSet(false, true)) return
            replied.set(true)
            val hardStop = runCatching {
                hardStops.schedule({ endProcess() }, HARD_STOP_MILLIS, TimeUnit.MILLISECONDS)
            }.getOrNull()
            try {
                writeQuietly(client, reply)
            } finally {
                hardStop?.cancel(false)
            }
            diagnostic(PortalErrorCode.TIMEOUT, reply.status)
        } finally {
            replyLock.unlock()
        }
        closeQuietly(client)
        cleanup()
        endProcess()
    }

    private fun endProcess() {
        if (!halted.compareAndSet(false, true)) return
        halt(124)
    }

    private fun cleanup() {
        closed = true
        closeQuietly(channel)
        unlinkIfOwned()
    }

    private fun unlinkIfOwned() {
        val identity = recorded ?: return
        if (identityOf(socket) != identity) return
        runCatching { Files.deleteIfExists(socket) }
    }

    private fun requirePrivateParent(parent: Path) {
        if (Files.isSymbolicLink(parent)) failStartup("the socket parent is a link: $parent")
        val attributes = try {
            Files.readAttributes(parent, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (_: Exception) {
            failStartup("the socket parent is not readable: $parent")
        }
        if (attributes.owner().name != currentUser()) failStartup("the socket parent is not owned by ${currentUser()}")
        if (attributes.permissions().any { it in SHARED_PERMISSIONS }) failStartup("the socket parent is not private: $parent")
    }

    private fun remainingSince(acceptedAt: Long): Duration {
        val remaining = deadline.minus(Duration.ofNanos(System.nanoTime() - acceptedAt))
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    private fun failing(client: SocketChannel, code: PortalErrorCode, status: Int?, replied: AtomicBoolean) {
        replyLock.lock()
        try {
            if (terminal.get() || replied.get()) return
            replied.set(true)
            writeFailure(client, code, status)
        } finally {
            replyLock.unlock()
        }
        diagnostic(code, status)
    }

    private fun writeFailure(client: SocketChannel, code: PortalErrorCode, status: Int? = null) {
        writeQuietly(client, PortalReply(status, emptyList(), false, PortalError(code)))
    }

    private fun writeQuietly(client: SocketChannel, reply: PortalReply) {
        runCatching { protocol.writeReply(Channels.newOutputStream(client), reply) }
    }

    private fun diagnostic(code: PortalErrorCode, status: Int?) {
        diagnostics.println("portal: ${code.name} status=${status ?: "none"}")
        diagnostics.flush()
    }

    private fun closeQuietly(target: AutoCloseable?) {
        runCatching { target?.close() }
    }

    /**
     * Read and discard the client input before the close.
     *
     * Linux sends a reset when a socket closes with unread input.
     * A reset discards the BUSY reply. The read is non-blocking and bounded.
     */
    private fun drainQuietly(client: SocketChannel) {
        runCatching {
            client.configureBlocking(false)
            val buffer = ByteBuffer.allocate(1024)
            val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DRAIN_MILLIS)
            var total = 0
            while (total <= DRAIN_LIMIT && System.nanoTime() < end) {
                buffer.clear()
                val count = client.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    Thread.sleep(1)
                } else {
                    total += count
                }
            }
        }
    }

    private fun currentUser(): String = System.getProperty("user.name")

    private fun failStartup(message: String): Nothing = throw PortalStartupException(message)

    private fun timeoutReply(): PortalReply =
        PortalReply(status = null, lines = emptyList(), complete = false, error = PortalError(PortalErrorCode.TIMEOUT))

    private companion object {
        const val HARD_STOP_MILLIS = 1000L
        const val DRAIN_MILLIS = 200L
        const val DRAIN_LIMIT = 4096
        val SHARED_PERMISSIONS = setOf(
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE,
        )
    }
}
