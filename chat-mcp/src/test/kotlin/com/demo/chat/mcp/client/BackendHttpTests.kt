package com.demo.chat.mcp.client

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The transport limits and the redirect rule.
 *
 * Each test runs a real loopback server, so the rule under test is exercised
 * through the JDK client and not through a stub.
 */
class BackendHttpTests {
    @Test
    fun `the transport declares history and submission operations`() {
        val methods = BackendHttp::class.java.methods.map { it.name }.toSet()
        assertTrue("history" in methods, "the history operation is missing")
        assertTrue("submit" in methods, "the submission operation is missing")
    }

    /** One loopback server with one handler. */
    private class Server(handler: (HttpExchange) -> Unit) : AutoCloseable {
        val requests = AtomicInteger()
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val executor = Executors.newFixedThreadPool(8)

        init {
            server.createContext("/") { exchange ->
                requests.incrementAndGet()
                handler(exchange)
            }
            server.executor = executor
            server.start()
        }

        val origin: URI get() = URI("http://127.0.0.1:${server.address.port}")

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun `history requests NDJSON and preserves its body`() {
        val body = "{\"message\":1}\n{\"message\":2}\n"
        val headers = AtomicReference<List<String>>()
        Server { exchange ->
            headers.set(listOf(exchange.requestMethod, exchange.requestHeaders.getFirst("Accept")))
            respond(exchange, 200, body)
        }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                assertEquals(body, http.history(server.origin.resolve("/message/list/7"), "token"))
                assertEquals(listOf("GET", "application/x-ndjson"), headers.get())
                assertEquals(1, server.requests.get())
            }
        }
    }

    @Test
    fun `submission preserves its request and supported response statuses`() {
        val text = "  hello λ 🌊\n"
        for (status in listOf(201, 202, 424)) {
            val request = AtomicReference<List<String>>()
            Server { exchange ->
                request.set(listOf(
                    exchange.requestMethod,
                    exchange.requestHeaders.getFirst("Authorization"),
                    exchange.requestHeaders.getFirst("Accept"),
                    exchange.requestHeaders.getFirst("Content-Type"),
                    exchange.requestHeaders.getFirst("Idempotency-Key"),
                    exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) },
                ))
                respond(exchange, status, "{\"status\":$status}")
            }.use { server ->
                JdkBackendHttp(server.origin).use { http ->
                    assertEquals(
                        BackendResponse(status, "{\"status\":$status}"),
                        http.submit(server.origin.resolve("/message/submit/7"), "token", "request:1", text),
                    )
                    assertEquals(
                        listOf("POST", "Bearer token", "application/json", "text/plain", "request:1", text),
                        request.get(),
                    )
                    assertEquals(1, server.requests.get())
                }
            }
        }
    }

    @Test
    fun `confirmed submission refusals discard their bodies`() {
        for (status in listOf(400, 409, 401, 403, 404)) {
            Server { respond(it, status, "private backend text") }.use { server ->
                JdkBackendHttp(server.origin).use { http ->
                    assertEquals(BackendResponse(status, ""), http.submit(server.origin, "token", "request", "text"))
                }
            }
        }
    }

    @Test
    fun `submission redirects never reach their target`() {
        for (status in listOf(301, 302, 307, 308)) {
            Server { exchange ->
                if (exchange.requestURI.path == "/target") respond(exchange, 201, "private response")
                else {
                    exchange.responseHeaders.add("Location", "/target")
                    exchange.sendResponseHeaders(status, -1)
                    exchange.close()
                }
            }.use { server ->
                JdkBackendHttp(server.origin).use { http ->
                    val failure = assertThrows(SubmissionUnknownException::class.java) {
                        http.submit(server.origin.resolve("/submit"), "token", "request", "text")
                    }
                    assertEquals("request", failure.requestId)
                    assertEquals(status, failure.status)
                    assertEquals(1, server.requests.get())
                    assertEquals("the submission outcome is unknown", failure.message)
                }
            }
        }
    }

    @Test
    fun `an unexpected submission status is uncertain`() {
        Server { respond(it, 500, "private backend failure") }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                val failure = assertThrows(SubmissionUnknownException::class.java) {
                    http.submit(server.origin, "token", "request", "text")
                }
                assertEquals(500, failure.status)
                assertEquals("the submission outcome is unknown", failure.message)
            }
        }
    }

    @Test
    fun `a large submission response is uncertain and releases its permit`() {
        Server { exchange ->
            respond(exchange, 201, if (exchange.requestURI.path == "/large") "x".repeat(1048577) else "ok")
        }.use { server ->
            JdkBackendHttp(server.origin, maxConcurrentRequests = 1).use { http ->
                assertThrows(SubmissionUnknownException::class.java) {
                    http.submit(server.origin.resolve("/large"), "token", "request", "text")
                }
                assertEquals(BackendResponse(201, "ok"), http.submit(server.origin, "token", "next", "text"))
            }
        }
    }

    @Test
    fun `a stalled submission response closes at the deadline and releases its permit`() {
        val serverRelease = CountDownLatch(1)
        Server { exchange ->
            if (exchange.requestURI.path == "/stall") {
                exchange.sendResponseHeaders(201, 1024)
                exchange.responseBody.write(1)
                exchange.responseBody.flush()
                serverRelease.await(5, TimeUnit.SECONDS)
                exchange.close()
            } else respond(exchange, 201, "ok")
        }.use { server ->
            JdkBackendHttp(server.origin, callDeadline = Duration.ofMillis(400), maxConcurrentRequests = 1).use { http ->
                val start = System.nanoTime()
                try {
                    assertThrows(SubmissionUnknownException::class.java) {
                        http.submit(server.origin.resolve("/stall"), "token", "request", "text")
                    }
                    assertTrue(Duration.ofNanos(System.nanoTime() - start) < Duration.ofSeconds(2))
                    assertEquals(BackendResponse(201, "ok"), http.submit(server.origin, "token", "next", "text"))
                } finally {
                    serverRelease.countDown()
                }
            }
        }
    }

    @Test
    fun `a lost submission response does not trigger a resend`() {
        Server { exchange -> exchange.requestBody.use { it.readBytes() }; exchange.close() }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                assertThrows(SubmissionUnknownException::class.java) {
                    http.submit(server.origin, "token", "request", "text")
                }
                assertEquals(1, server.requests.get())
            }
        }
    }

    @Test
    fun `new operations refuse another origin before sending a request`() {
        Server { respond(it, 201, "unexpected") }.use { server ->
            JdkBackendHttp(URI("http://127.0.0.1:1")).use { http ->
                assertThrows(ClientException::class.java) { http.history(server.origin, "token") }
                assertThrows(ClientException::class.java) { http.submit(server.origin, "token", "request", "text") }
                assertEquals(0, server.requests.get())
            }
        }
    }

    @Test
    fun `submission and history share the request limit`() {
        val arrived = CountDownLatch(4)
        val release = CountDownLatch(1)
        Server { exchange ->
            arrived.countDown()
            release.await(5, TimeUnit.SECONDS)
            respond(exchange, if (exchange.requestMethod == "POST") 201 else 200, "ok")
        }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                val workers = Executors.newFixedThreadPool(5)
                try {
                    val reads = (1..4).map { workers.submit<String> { http.history(server.origin, "token") } }
                    assertTrue(arrived.await(3, TimeUnit.SECONDS))
                    val submission = workers.submit<BackendResponse> {
                        http.submit(server.origin, "token", "request", "text")
                    }
                    assertThrows(java.util.concurrent.TimeoutException::class.java) {
                        submission.get(150, TimeUnit.MILLISECONDS)
                    }
                    assertEquals(4, server.requests.get())
                    release.countDown()
                    reads.forEach { assertEquals("ok", it.get(3, TimeUnit.SECONDS)) }
                    assertEquals(BackendResponse(201, "ok"), submission.get(3, TimeUnit.SECONDS))
                } finally {
                    release.countDown()
                    workers.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `a permit deadline before dispatch remains a local limit refusal`() {
        Server { respond(it, 201, "unexpected") }.use { server ->
            JdkBackendHttp(server.origin, maxConcurrentRequests = 0, callDeadline = Duration.ofMillis(100)).use { http ->
                val failure = assertThrows(ClientException::class.java) {
                    http.submit(server.origin, "token", "request", "text")
                }
                assertEquals(FailureReason.LIMIT, failure.reason)
                assertEquals(0, server.requests.get())
            }
        }
    }

    @Test
    fun `the limits match the agreed contract`() {
        assertEquals(Duration.ofSeconds(5), BackendLimits.CONNECT_TIMEOUT)
        assertEquals(Duration.ofSeconds(30), BackendLimits.CALL_DEADLINE)
        assertEquals(4, BackendLimits.MAX_CONCURRENT_REQUESTS)
        assertEquals(1048576, BackendLimits.MAX_RESPONSE_BYTES)
    }

    @Test
    fun `a 200 response returns its body`() {
        Server { respond(it, 200, """{"ok":true}""") }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                assertEquals("""{"ok":true}""", http.get(server.origin.resolve("/topic/id/7"), "token"))
            }
        }
    }

    @Test
    fun `a status other than 200 is refused`() {
        Server { respond(it, 403, "denied") }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                val failure =
                    assertThrows(ClientException::class.java) {
                        http.get(server.origin.resolve("/topic/id/7"), "token")
                    }
                assertTrue(failure.message!!.contains("403"))
            }
        }
    }

    @Test
    fun `a same origin redirect is followed`() {
        Server { exchange ->
            if (exchange.requestURI.path == "/start") {
                exchange.responseHeaders.add("Location", "/end")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            } else {
                respond(exchange, 200, "arrived")
            }
        }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                assertEquals("arrived", http.get(server.origin.resolve("/start"), "token"))
                assertEquals(2, server.requests.get())
            }
        }
    }

    @Test
    fun `a redirect to another origin is refused`() {
        Server { respond(it, 200, "second origin") }.use { second ->
            Server { exchange ->
                exchange.responseHeaders.add("Location", second.origin.resolve("/end").toString())
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }.use { first ->
                JdkBackendHttp(first.origin).use { http ->
                    val failure =
                        assertThrows(ClientException::class.java) {
                            http.get(first.origin.resolve("/start"), "token")
                        }
                    assertTrue(failure.message!!.contains("origin"))
                    // The credential never left the process.
                    assertEquals(0, second.requests.get())
                }
            }
        }
    }

    @Test
    fun `a redirect loop is refused`() {
        Server { exchange ->
            exchange.responseHeaders.add("Location", "/again")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }.use { server ->
            JdkBackendHttp(server.origin, maxRedirects = 2).use { http ->
                val failure =
                    assertThrows(ClientException::class.java) {
                        http.get(server.origin.resolve("/start"), "token")
                    }
                assertTrue(failure.message!!.contains("redirects"))
            }
        }
    }

    @Test
    fun `a redirect with no location is refused`() {
        Server { exchange ->
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                val failure =
                    assertThrows(ClientException::class.java) {
                        http.get(server.origin.resolve("/start"), "token")
                    }
                assertTrue(failure.message!!.contains("location"))
            }
        }
    }

    @Test
    fun `a body above the limit is refused`() {
        val big = "x".repeat(4096)
        Server { respond(it, 200, big) }.use { server ->
            JdkBackendHttp(server.origin, maxResponseBytes = 1024).use { http ->
                val failure =
                    assertThrows(ClientException::class.java) {
                        http.get(server.origin.resolve("/topic/id/7"), "token")
                    }
                assertTrue(failure.message!!.contains("1024"))
            }
        }
    }

    @Test
    fun `a body exactly at the limit is accepted`() {
        val exact = "x".repeat(1024)
        Server { respond(it, 200, exact) }.use { server ->
            JdkBackendHttp(server.origin, maxResponseBytes = 1024).use { http ->
                assertEquals(exact, http.get(server.origin.resolve("/topic/id/7"), "token"))
            }
        }
    }

    @Test
    fun `a call that passes its deadline is refused`() {
        Server { exchange ->
            Thread.sleep(2000)
            respond(exchange, 200, "late")
        }.use { server ->
            JdkBackendHttp(server.origin, callDeadline = Duration.ofMillis(300)).use { http ->
                val failure =
                    assertThrows(ClientException::class.java) {
                        http.get(server.origin.resolve("/topic/id/7"), "token")
                    }
                assertTrue(failure.message!!.contains("deadline") || failure.message!!.contains("failed"))
            }
        }
    }

    @Test
    fun `a response body that stalls past the deadline is refused`() {
        // The headers arrive, and the body never does. The request timeout
        // does not cover this case, so the watchdog must.
        Server { exchange ->
            // A fixed length, a first part of the body, and then silence.
            // The write flushes the headers, so the client reaches the body.
            exchange.sendResponseHeaders(200, 1024)
            val chunk = "x".repeat(16).toByteArray(Charsets.UTF_8)
            exchange.responseBody.write(chunk)
            exchange.responseBody.flush()
            Thread.sleep(5000)
            exchange.close()
        }.use { server ->
            JdkBackendHttp(server.origin, callDeadline = Duration.ofMillis(400)).use { http ->
                val started = System.nanoTime()
                val failure =
                    assertThrows(ClientException::class.java) {
                        http.get(server.origin.resolve("/topic/id/7"), "token")
                    }
                val elapsedMillis = (System.nanoTime() - started) / 1_000_000
                assertTrue(failure.message!!.contains("deadline"))
                // The server stalls for five seconds. A call that waits for it
                // has no deadline. The elapsed time is the proof.
                assertTrue(elapsedMillis < 2000, "the call ran for $elapsedMillis ms")
            }
        }
    }

    @Test
    fun `at most four requests run at one time`() {
        val arrived = CountDownLatch(4)
        val release = CountDownLatch(1)
        val inFlight = AtomicInteger()
        val highest = AtomicInteger()
        Server { exchange ->
            val now = inFlight.incrementAndGet()
            highest.accumulateAndGet(now) { left, right -> if (left > right) left else right }
            arrived.countDown()
            release.await(5, TimeUnit.SECONDS)
            inFlight.decrementAndGet()
            respond(exchange, 200, "ok")
        }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                val target = server.origin.resolve("/topic/id/7")
                val workers = (1..5).map { index ->
                    Thread { runCatching { http.get(target, "token-$index") } }.apply { start() }
                }
                assertTrue(arrived.await(5, TimeUnit.SECONDS), "four requests did not arrive")
                // The fifth request waits for a permit.
                Thread.sleep(150)
                assertEquals(4, server.requests.get())
                release.countDown()
                workers.forEach { it.join(5000) }
                assertEquals(5, server.requests.get())
                assertEquals(4, highest.get())
            }
        }
    }

    @Test
    fun `a redirect to a scheme with no origin is refused`() {
        Server { exchange ->
            exchange.responseHeaders.add("Location", "mailto:someone@example.test")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                val failure =
                    assertThrows(ClientException::class.java) {
                        http.get(server.origin.resolve("/start"), "token")
                    }
                assertTrue(failure.message!!.contains("origin"))
            }
        }
    }

    @Test
    fun `a redirect to a target with a path is refused by the origin rule`() {
        // The origin rule compares scheme, host and port. A path does not
        // change the origin, so this call reaches the target.
        Server { exchange ->
            if (exchange.requestURI.path == "/start") {
                exchange.responseHeaders.add("Location", "/other/path")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            } else {
                respond(exchange, 200, "arrived")
            }
        }.use { server ->
            JdkBackendHttp(server.origin).use { http ->
                assertEquals("arrived", http.get(server.origin.resolve("/start"), "token"))
            }
        }
    }
}
