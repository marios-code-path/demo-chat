package com.demo.chat.shell.portal

import com.demo.chat.shell.cli.EnvironmentLogin
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import com.demo.chat.shell.commands.PubSubCommands
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.io.PrintWriter
import java.nio.file.Path
import java.time.Duration

/** Creates one portal server for a socket path. A test replaces this bean. */
fun interface PortalServerFactory {
    fun create(socket: Path): PortalSocketServer
}

/** Adds and removes the one shutdown hook of a portal launch. */
interface ShutdownHooks {
    fun add(hook: Thread)
    fun remove(hook: Thread)
}

/** The process shutdown hooks. */
object RuntimeShutdownHooks : ShutdownHooks {
    override fun add(hook: Thread) {
        Runtime.getRuntime().addShutdownHook(hook)
    }

    override fun remove(hook: Thread) {
        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
    }
}

/**
 * The beans of the portal launch. The portal serves one read-only register
 * request per connection. See CHAT-hmbigvmf.
 */
@Configuration(proxyBeanMethods = false)
@Profile("shell")
class PortalConfiguration {

    @Bean
    fun portalProtocol(mapper: ObjectMapper): PortalProtocol = PortalProtocol(mapper)

    @Bean
    fun portalReadService(commands: PubSubCommands<*>, mapper: ObjectMapper): PortalReadService =
        PortalReadService(
            readIndex = { index, deadline -> commands.registerRead(index, REGISTER_MAX_AGE, deadline) },
            readTopic = { topic -> commands.messages(topic, format = "records") },
            mapper = mapper,
        )

    @Bean
    fun portalServerFactory(
        protocol: PortalProtocol,
        read: PortalReadService,
        diagnostics: PrintWriter,
    ): PortalServerFactory =
        PortalServerFactory { socket ->
            PortalSocketServer(
                socket = socket,
                protocol = protocol,
                read = read::read,
                deadline = PORTAL_DEADLINE,
                halt = { Runtime.getRuntime().halt(it) },
                diagnostics = diagnostics,
            )
        }

    @Bean
    fun shutdownHooks(): ShutdownHooks = RuntimeShutdownHooks

    companion object {
        /** The register read shows entries younger than one day. */
        val REGISTER_MAX_AGE: Duration = Duration.ofHours(24)

        /** One request has 30 seconds from accept. */
        val PORTAL_DEADLINE: Duration = Duration.ofSeconds(30)
    }
}

/**
 * Runs the portal in portal mode. It logs in from the environment before it
 * binds the socket. A stop signal runs the one shutdown hook. See CHAT-hmbigvmf.
 */
class PortalRunner(
    private val login: EnvironmentLogin,
    private val factory: PortalServerFactory,
    private val socket: String,
    private val hooks: ShutdownHooks,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        login.loginRequired()
        if (socket.isBlank()) throw ShellExit(ShellExitStatus.USAGE, "set chat.shell.portal.socket")
        val path = Path.of(socket)
        val server = factory.create(path)
        val hook = Thread({ server.close() }, "portal-shutdown")
        hooks.add(hook)
        try {
            server.bind()
            server.serve()
        } finally {
            hooks.remove(hook)
            server.close()
        }
    }
}
