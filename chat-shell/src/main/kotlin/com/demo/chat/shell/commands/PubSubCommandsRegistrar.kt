package com.demo.chat.shell.commands

import com.demo.chat.shell.cli.HardLimits
import com.demo.chat.shell.cli.LaunchDeadline
import com.demo.chat.shell.cli.StreamInput
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandOption
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.function.Consumer
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
            commands.send(ctx.optionValue("topic"), ctx.optionValue("userName"), ctx.mainValue("messageText"), ctx.optionValue("requestId"))
        })

    /**
     * `post` takes the entry text from the command input, so its newlines need
     * no quotes. Only the shell modes give such an input. The interactive
     * shell does not, and it refuses the command.
     */
    @Bean
    fun postCommand(): Command = Command.builder()
        .name("post")
        .description("Post the text of standard input to a topic")
        .group("PubSub")
        .arguments(positional("topic"))
            .options(
                CommandOption.with().longName("topic").required(true).type(String::class.java).build(),
                CommandOption.with().longName("requestId").required(false).defaultValue("_").type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx ->
            val input = ctx.inputReader() as? StreamInput
                ?: throw IllegalArgumentException("post reads its text from standard input. Use it in one-shot or resident mode.")
            val text = input.readAll()
            if (text.isEmpty()) throw IllegalArgumentException("post needs the entry text on standard input.")
            commands.post(ctx.mainValue("topic"), text, ctx.optionValue("requestId"))
        })

    /**
     * `register-read` is the session-start read of the topic register, in one
     * process. See `PubSubCommands.registerRead`.
     *
     * **The 30-second limit runs from the launch of the process in the shell
     * modes**, as owner decision 20 says, so the JVM start counts. `main` also
     * arms a watchdog before the application starts, so a startup that stalls
     * ends with status 124 too. A spent budget refuses the read, and a budget
     * that runs out during the read refuses the output. See [LaunchDeadline].
     * In the interactive shell the process is old, so the limit runs from the
     * command. An empty register prints nothing, and not even a blank line.
     */
    @Bean
    fun registerReadCommand(): Command = Command.builder()
        .name("register-read")
        .description("Read the register: its index and the newest recent entry of each topic")
        .group("PubSub")
        .options(
            CommandOption.with().longName("index").required(false).defaultValue("register-index").type(String::class.java).build(),
            CommandOption.with().longName("maxAgeHours").required(false).defaultValue("24").type(String::class.java).build(),
        )
        .execute(Consumer<CommandContext> { ctx ->
            val hours = ctx.optionValue("maxAgeHours").toLongOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("register-read needs a positive --maxAgeHours.")
            // The shell modes count the limit from the launch of the process, so the JVM start counts.
            // The interactive process is old, so there the limit counts from the command.
            val deadline = if (ctx.inputReader() is StreamInput)
                LaunchDeadline.forProcess(HardLimits.REGISTER_READ, HardLimits.REGISTER_READ_LIMIT)
            else LaunchDeadline(HardLimits.REGISTER_READ, Instant.now(), HardLimits.REGISTER_READ_LIMIT)
            deadline.requireBudget()
            val text = commands.registerRead(ctx.optionValue("index"), Duration.ofHours(hours), deadline.remaining())
            deadline.finish {
                if (text.isNotEmpty()) ctx.outputWriter().println(text)
                ctx.outputWriter().flush()
            }
        })

    /** `command-status` reads the backends of a command that the login owns. */
    @Bean
    fun commandStatusCommand(): Command = Command.builder()
        .name("command-status")
        .description("Show the status of a message command")
        .group("PubSub")
        .options(CommandOption.with().longName("commandId").required(true).type(String::class.java).build())
        .execute(Function<CommandContext, String> { ctx -> commands.commandStatus(ctx.optionValue("commandId")) })

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
            CommandOption.with().longName("format").required(false).defaultValue("human").type(String::class.java).build(),
            CommandOption.with().longName("since").required(false).type(String::class.java).build(),
        )
        // A Consumer, because an empty result in the records format prints nothing. A Function always prints a line.
        .execute(Consumer<CommandContext> { ctx ->
            val limit = ctx.getOptionByLongName("limit")?.let {
                it.value()?.toIntOrNull()
                    ?: throw IllegalArgumentException("messages needs a positive --limit.")
            }
            val since = ctx.getOptionByLongName("since")?.let {
                try {
                    Instant.parse(it.value())
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException(
                        "messages needs --since as an ISO-8601 instant, such as 2026-10-17T23:21:19Z.", e)
                }
            }
            val text = commands.messages(ctx.mainValue("topic"), limit, ctx.optionValue("format"), since)
            if (text.isNotEmpty()) ctx.outputWriter().println(text)
            ctx.outputWriter().flush()
        })
}
