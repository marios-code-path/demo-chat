package com.demo.chat.shell.commands

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandOption
import java.util.function.Function

/**
 * The programmatic registration of the LoginCommands commands.
 *
 * **Spring Shell 4 derives nothing, so every name is written here.** The
 * declarative model took the command name from the method name and the option
 * name from the parameter name. This file states both, and
 * docs/SHELL-COMMAND-CONTRACT.md is what it must agree with.
 *
 * The programmatic form is required rather than preferred. Spring Shell 4 does
 * not support declarative commands under GraalVM native compilation.
 *
 * **No command carries an availability provider**, because none carried one
 * before. See CHAT-fxrwtvef.
 */
@Configuration
@Profile("shell")
class LoginCommandsRegistrar<T>(private val commands: LoginCommands<T>) {
    @Bean
    fun byeCommand(): Command = Command.builder()
        .name("bye")
        .description("bye")
        .group("Login")
        .execute(Function<CommandContext, String> { ctx -> commands.bye()?.toString() ?: "" })

    @Bean
    fun rootKeysCommand(): Command = Command.builder()
        .name("root-keys")
        .description("rootkeys")
        .group("Login")
        .execute(Function<CommandContext, String> { ctx -> commands.rootKeys()?.toString() ?: "" })

    @Bean
    fun whoamiCommand(): Command = Command.builder()
        .name("whoami")
        .description("whoami")
        .group("Login")
        .execute(Function<CommandContext, String> { ctx -> commands.whoami()?.toString() ?: "" })

    @Bean
    fun loginCommand(): Command = Command.builder()
        .name("login")
        .description("login")
        .group("Login")
            .options(
                CommandOption.with().longName("username").required(true).type(String::class.java).build(),
                CommandOption.with().longName("password").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.login(ctx.optionValue("username"), ctx.optionValue("password")).let { "" } })

}
