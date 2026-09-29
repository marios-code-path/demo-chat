package com.demo.chat.mcp

import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.ConfigException
import com.demo.chat.mcp.config.configPathFrom
import com.demo.chat.mcp.config.loadConfigFile
import com.demo.chat.mcp.config.originOf
import com.demo.chat.mcp.config.readCredentialFile
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlin.system.exitProcess

/**
 * The shutdown bound in milliseconds.
 *
 * The transport closes when stdin reaches end of file. The bound covers the
 * close that follows. A wedged close must not hold the process open.
 */
const val SHUTDOWN_BOUND_MILLIS: Long = 5000

/**
 * The system property that silences the logging library startup message.
 *
 * `kotlin-logging` prints one line to stdout when it initializes. That line is
 * not a protocol frame, so it breaks the stdio contract of this adapter. The
 * library reads this property once, at class initialization, so the adapter
 * sets it before it touches any other class.
 */
const val KOTLIN_LOGGING_STARTUP_MESSAGE_PROPERTY: String = "kotlin-logging.logStartupMessage"

/**
 * The verbosity property of the SLF4J reporter.
 *
 * `slf4j-api` reaches the classpath through the MCP SDK, and no provider binds
 * to it. The reporter then writes three warning lines to stderr. Measured on
 * 2026-09-29: `SLF4J(W): No SLF4J providers were found.`, `SLF4J(W): Defaulting
 * to no-operation (NOP) logger implementation` and the link line that follows.
 */
const val SLF4J_VERBOSITY_PROPERTY: String = "slf4j.internal.verbosity"

/** The level that hides the three provider warnings. The adapter logs no error through SLF4J. */
const val SLF4J_VERBOSITY_ERROR: String = "ERROR"

/**
 * Stop the libraries from writing their own lines to the two streams.
 *
 * Two libraries write on their own. `kotlin-logging` writes one line to stdout,
 * which carries protocol frames alone. That line is
 * `kotlin-logging: initializing... active logger factory: Slf4jLoggerFactory`.
 * `slf4j-api` writes three warning lines to stderr, where the adapter writes
 * its diagnostics.
 *
 * Both properties must be set before their library initializes, so `main` calls
 * this first. The Task 5 purity tests are the guard. A late call leaves the
 * lines in place and a test fails.
 */
internal fun silenceLibraryStartupMessage() {
    System.setProperty(KOTLIN_LOGGING_STARTUP_MESSAGE_PROPERTY, "false")
    System.setProperty(SLF4J_VERBOSITY_PROPERTY, SLF4J_VERBOSITY_ERROR)
}

/** Write one diagnostic line to stderr. Stdout carries protocol frames alone. */
internal fun diagnostic(message: String) {
    System.err.println("chat-mcp: $message")
}

/** Run the adapter on stdio until stdin reaches end of file. */
fun runStdioAdapter(config: AdapterConfig) {
    val server = createMcpServer(config)
    val transport =
        StdioServerTransport(
            System.`in`.asSource().buffered(),
            System.out.asSink().buffered(),
        )

    // The close callback sits on the transport and not on the server. A
    // Server.onClose callback runs from Server.close() alone. An end of file
    // on stdin closes the transport, and the server callback never runs. The
    // transport callback is the one that fires on that path.
    val closed = CompletableDeferred<Unit>()
    transport.onClose { closed.complete(Unit) }

    runBlocking {
        server.createSession(transport)
        diagnostic("ready, protocol revision is chosen by the SDK")
        closed.await()
    }

    // The close runs under a bound, so a stuck final write cannot hang the
    // process. exitProcess then ends it, because a leftover thread must not
    // extend the shutdown.
    runBlocking {
        val finished = withTimeoutOrNull(SHUTDOWN_BOUND_MILLIS) { server.close() }
        if (finished == null) {
            diagnostic("close did not finish within $SHUTDOWN_BOUND_MILLIS ms")
        }
    }
    diagnostic("stdin closed, exiting")
    exitProcess(0)
}

/**
 * Load the configuration and prove that the credential is readable.
 *
 * The credential is read here so a missing or empty file fails the start,
 * before any client connects. The value is discarded. Only the client reads
 * it again, at the moment of use.
 */
internal fun loadConfigForStartup(
    args: List<String>,
    environment: Map<String, String>,
): AdapterConfig {
    val config = loadConfigFile(configPathFrom(args, environment))
    readCredentialFile(config.credentialFile)
    return config
}

fun main(args: Array<String>) {
    // This runs before every other statement, because each library reads its
    // property once, when it initializes. A reader must not move it down.
    silenceLibraryStartupMessage()
    val config =
        try {
            loadConfigForStartup(args.toList(), System.getenv())
        } catch (failure: ConfigException) {
            diagnostic("configuration refused: ${failure.message}")
            exitProcess(2)
        }
    diagnostic(
        "configured for ${originOf(config.backendBaseUrl)}, " +
            "${config.topicIds.size} topic ids, key type ${config.keyType}",
    )
    runStdioAdapter(config)
}
