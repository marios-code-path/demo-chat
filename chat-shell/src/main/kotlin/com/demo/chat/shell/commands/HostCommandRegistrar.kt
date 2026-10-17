package com.demo.chat.shell.commands

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import java.util.function.Consumer

/**
 * The programmatic registration of the `!` command.
 *
 * **The command declares no argument and no option.** [BangCommandParser]
 * builds its one argument from the raw line, so a declared argument would add
 * nothing. A declared option would invite `--help`, and a `!` line never
 * reaches the option parser. docs/SHELL-COMMAND-CONTRACT.md lists the command.
 *
 * The command writes to the output writer as the host command runs, so it is a
 * `Consumer` and returns no text. See CHAT-jrlctimq.
 */
@Configuration
@Profile("shell")
class HostCommandRegistrar(private val host: HostCommand) {
    @Bean
    fun bangCommand(): Command = Command.builder()
        .name(BangCommandParser.BANG)
        .description("Run a host command with bash")
        .group("Host")
        .execute(Consumer<CommandContext> { ctx ->
            host.run(ctx.parsedInput().arguments().firstOrNull()?.value(), ctx.outputWriter())
        })
}
