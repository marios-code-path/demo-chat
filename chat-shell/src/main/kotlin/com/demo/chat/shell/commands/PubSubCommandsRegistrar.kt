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
class PubSubCommandsRegistrar<T : Any>(private val commands: PubSubCommands<T>) {
    /**
     * `send` takes the text by position, because every send carries a text.
     * The room comes from `--topic`, or a user from `--userName`.
     */
    @Bean
    fun sendCommand(): Command = Command.builder()
        .name("send")
        .description("Send a Message")
        .group("PubSub")
        .arguments(positional("messageText"))
            .options(
                CommandOption.with().longName("topic").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("userName").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("messageText").required(true).type(String::class.java).build(),
                CommandOption.with().longName("requestId").required(false).defaultValue("_").type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx ->
            commands.send(ctx.optionValue("topic"), ctx.optionValue("userName"), ctx.mainValue("messageText"), ctx.optionValue("requestId")).let { "" }
        })

    /** `command-status` reads the backends of a command that the login owns. */
    @Bean
    fun commandStatusCommand(): Command = Command.builder()
        .name("command-status")
        .description("Show the status of a message command")
        .group("PubSub")
        .options(CommandOption.with().longName("commandId").required(true).type(String::class.java).build())
        .execute(Function<CommandContext, String> { ctx -> commands.commandStatus(ctx.optionValue("commandId")).let { "" } })

    @Bean
    fun listenCommand(): Command = Command.builder()
        .name("listen")
        .description("Listen to a topic")
        .group("PubSub")
        .arguments(positional("topic"))
            .options(
                CommandOption.with().longName("topic").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.listen(ctx.mainValue("topic")).let { id -> "Listening to topic $id. Run hangup $id to stop." } })

    @Bean
    fun hangupCommand(): Command = Command.builder()
        .name("hangup")
        .description("Stop listening to a topic")
        .group("PubSub")
        .arguments(positional("topic"))
            .options(
                CommandOption.with().longName("topic").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.hangup(ctx.mainValue("topic")).let { id -> "Stopped listening to topic $id" } })

    /** This command displays the newest selected messages in time order. See `CHAT-bmmtojqm`. */
    @Bean
    fun messagesCommand(): Command = Command.builder()
        .name("messages")
        .description("List the messages of a topic")
        .group("PubSub")
        .arguments(positional("topic"))
        .options(
            CommandOption.with().longName("topic").required(true).type(String::class.java).build(),
            CommandOption.with().longName("limit").required(false).type(Int::class.javaObjectType).build(),
        )
        .execute(Function<CommandContext, String> { ctx ->
            val limit = ctx.getOptionByLongName("limit")?.let {
                it.value()?.toIntOrNull()
                    ?: throw IllegalArgumentException("messages needs a positive --limit.")
            }
            commands.messages(ctx.mainValue("topic"), limit)
        })

}
