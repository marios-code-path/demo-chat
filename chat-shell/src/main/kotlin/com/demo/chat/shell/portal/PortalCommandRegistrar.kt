package com.demo.chat.shell.portal

import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import com.demo.chat.shell.commands.optionValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandOption
import java.nio.file.Path
import java.util.function.Function

/**
 * Registers the interactive `portal` command. It serves the socket in the
 * foreground until a stop signal. It checks the shell login before bind.
 * See CHAT-hmbigvmf.
 */
@Configuration(proxyBeanMethods = false)
@Profile("shell")
class PortalCommandRegistrar(
    private val factory: PortalServerFactory,
    private val hooks: ShutdownHooks,
) {

    @Bean
    fun portalCommand(): Command = Command.builder()
        .name("portal")
        .description("Serve the register read portal on a Unix socket")
        .group("Portal")
        .options(
            CommandOption.with()
                .longName("socket")
                .required(true)
                .type(String::class.java)
                .build(),
        )
        .execute(Function<CommandContext, String> { context ->
            if (ShellStateConfiguration.loggedInUser.isEmpty || ShellStateConfiguration.loginMetadata.isEmpty)
                throw ShellExit(ShellExitStatus.LOGIN, "portal needs a login")
            val socket = Path.of(context.optionValue("socket"))
            if (!socket.isAbsolute)
                throw ShellExit(ShellExitStatus.USAGE, "the socket path must be absolute: $socket")
            val server = factory.create(socket)
            val hook = Thread({ server.close() }, "portal-shutdown")
            hooks.add(hook)
            try {
                server.bind()
                server.serve()
            } finally {
                hooks.remove(hook)
                server.close()
            }
            "portal closed"
        })
}
