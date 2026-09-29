package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.ConfigException
import com.demo.chat.mcp.config.requireSameOrigin
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** The limits that every backend call obeys. */
object BackendLimits {
    /** The connect timeout of one call. */
    @JvmField val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)

    /** The deadline of one call, measured from its start. */
    @JvmField val CALL_DEADLINE: Duration = Duration.ofSeconds(30)

    /** The greatest number of concurrent backend requests. */
    const val MAX_CONCURRENT_REQUESTS: Int = 4

    /** The greatest response body, in bytes. One MiB of UTF-8 text. */
    const val MAX_RESPONSE_BYTES: Int = 1024 * 1024

    /** The greatest number of redirects that one call follows. */
    const val MAX_REDIRECTS: Int = 4
}

/** One backend transport. A test replaces it with a fake. */
interface BackendHttp {
    /**
     * Read one resource.
     *
     * The caller reads the credential at the moment of the call. This type
     * never holds one.
     *
     * The call obeys [BackendLimits]. It follows a redirect on the configured
     * origin alone.
     */
    fun get(target: URI, credential: String): String
}

/**
 * The transport that reaches a deployment over HTTP.
 *
 * The client never follows a redirect itself. Every hop is checked against the
 * configured origin before a request goes out, so no credential reaches
 * another origin.
 */
class JdkBackendHttp(
    /** The one origin the adapter may reach. */
    private val configuredOrigin: URI,
    private val connectTimeout: Duration = BackendLimits.CONNECT_TIMEOUT,
    private val callDeadline: Duration = BackendLimits.CALL_DEADLINE,
    private val maxConcurrentRequests: Int = BackendLimits.MAX_CONCURRENT_REQUESTS,
    private val maxResponseBytes: Int = BackendLimits.MAX_RESPONSE_BYTES,
    private val maxRedirects: Int = BackendLimits.MAX_REDIRECTS,
) : BackendHttp,
    AutoCloseable {
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    private val permits: Semaphore = Semaphore(maxConcurrentRequests)

    /** One daemon thread that closes a stalled response body. */
    private val watchdog: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "mcp-backend-deadline").apply { isDaemon = true }
        }

    override fun get(target: URI, credential: String): String {
        val deadline = System.nanoTime() + callDeadline.toNanos()
        waitForAPermit(deadline)
        try {
            var current = target
            var hops = 0
            while (true) {
                requireConfiguredOrigin(current)
                val response = send(current, credential, deadline)
                val status = response.statusCode()
                if (status in 300..399) {
                    val location =
                        response.headers().firstValue("location").orElse(null)
                            ?: throw ClientException("the backend answered $status with no location")
                    response.body().close()
                    hops += 1
                    if (hops > maxRedirects) {
                        throw ClientException("the backend redirects more than $maxRedirects times")
                    }
                    current = resolveRedirect(current, location)
                    continue
                }
                if (status == 200) {
                    return readBody(response.body(), deadline)
                }
                response.body().close()
                throw ClientException("the backend answered $status")
            }
        } finally {
            permits.release()
        }
    }

    override fun close() {
        // The JDK client holds no resource that a close must release. The
        // watchdog does.
        watchdog.shutdownNow()
    }

    /** Wait for a concurrency permit under the call deadline. */
    private fun waitForAPermit(deadline: Long) {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) {
            throw ClientException("the backend call passed its deadline")
        }
        val waited =
            try {
                permits.tryAcquire(remaining, TimeUnit.NANOSECONDS)
            } catch (failure: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ClientException("the backend call was interrupted")
            }
        if (!waited) {
            throw ClientException("the adapter holds $maxConcurrentRequests backend requests already")
        }
    }

    /** Refuse a target on any origin but the configured one. */
    private fun requireConfiguredOrigin(target: URI) {
        try {
            requireSameOrigin(configuredOrigin, target)
        } catch (failure: ConfigException) {
            throw ClientException("a response named another origin")
        }
    }

    /** Send one request under the remaining time of the deadline. */
    private fun send(target: URI, credential: String, deadline: Long): HttpResponse<InputStream> {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) {
            throw ClientException("the backend call passed its deadline")
        }
        val request =
            HttpRequest.newBuilder(target)
                .timeout(Duration.ofNanos(remaining))
                .header("Authorization", "Bearer $credential")
                .header("Accept", "application/json")
                .GET()
                .build()
        return try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (failure: IOException) {
            throw ClientException("the backend call failed: ${failure.javaClass.simpleName}")
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ClientException("the backend call was interrupted")
        }
    }

    /**
     * Read a body, and refuse one above the limit.
     *
     * The HTTP request timeout covers the connect step and the response
     * headers. It does not cover a body that arrives slowly. The watchdog
     * closes the stream when the deadline passes, so a stalled body cannot
     * hold the call open.
     */
    private fun readBody(body: InputStream, deadline: Long): String {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) {
            body.close()
            throw ClientException("the backend call passed its deadline")
        }
        val watchdogTask = watchdog.schedule({ body.close() }, remaining, TimeUnit.NANOSECONDS)
        try {
            return body.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) {
                        break
                    }
                    if (out.size() + read > maxResponseBytes) {
                        throw ClientException("the backend response is above $maxResponseBytes bytes")
                    }
                    out.write(buffer, 0, read)
                }
                out.toString(StandardCharsets.UTF_8)
            }
        } catch (failure: IOException) {
            if (System.nanoTime() >= deadline) {
                throw ClientException("the backend call passed its deadline")
            }
            throw ClientException("the backend response could not be read: ${failure.javaClass.simpleName}")
        } finally {
            watchdogTask.cancel(false)
        }
    }
}

/**
 * Resolve one location header against the request that named it.
 *
 * The result is not checked here. The caller checks it before the next request
 * goes out, so a refusal happens before a credential leaves the process.
 */
internal fun resolveRedirect(current: URI, location: String): URI =
    try {
        val resolved = current.resolve(location)
        if (!resolved.isAbsolute) {
            throw ClientException("the backend named a redirect with no origin")
        }
        resolved
    } catch (failure: IllegalArgumentException) {
        throw ClientException("the backend named a redirect the adapter cannot read")
    }
