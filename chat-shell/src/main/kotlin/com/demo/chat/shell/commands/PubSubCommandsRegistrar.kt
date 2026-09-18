package com.demo.chat.shell.commands

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandOption
import java.util.function.Function

/**
 * The programmatic registration of the PubSubCommands commands.
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
class PubSubCommandsRegistrar<T>(private val commands: PubSubCommands<T>) {
    @Bean
    fun sendCommand(): Command = Command.builder()
        .name("send")
        .description("Send a Message")
        .group("PubSub")
            .options(
                CommandOption.with().longName("topicName").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("topicId").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("userName").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("messageText").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.send(ctx.optionValue("topicName"), ctx.optionValue("topicId"), ctx.optionValue("userName"), ctx.optionValue("messageText")).let { "" } })

    @Bean
    fun listenCommand(): Command = Command.builder()
        .name("listen")
        .description("Listen to a topic")
        .group("PubSub")
            .options(
                CommandOption.with().longName("topicId").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.listen(ctx.optionValue("topicId")).let { "" } })

    @Bean
    fun hangupCommand(): Command = Command.builder()
        .name("hangup")
        .description("Stop listening to a topic")
        .group("PubSub")
            .options(
                CommandOption.with().longName("topicId").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.hangup(ctx.optionValue("topicId")).let { "" } })

}
