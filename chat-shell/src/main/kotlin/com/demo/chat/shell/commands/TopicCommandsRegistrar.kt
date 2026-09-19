package com.demo.chat.shell.commands

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandOption
import java.util.function.Function

/**
 * The programmatic registration of the TopicCommands commands.
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
class TopicCommandsRegistrar<T : Any>(private val commands: TopicCommands<T>) {
    @Bean
    fun showTopicsCommand(): Command = Command.builder()
        .name("show-topics")
        .description("show topics")
        .group("Topic")
        .execute(Function<CommandContext, String> { ctx -> commands.showTopics()?.toString() ?: "" })

    @Bean
    fun addTopicCommand(): Command = Command.builder()
        .name("add-topic")
        .description("Create a topic")
        .group("Topic")
            .options(
                CommandOption.with().longName("userId").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("name").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.addTopic(ctx.optionValue("userId"), ctx.optionValue("name")).let { "" } })

    @Bean
    fun topicByNameCommand(): Command = Command.builder()
        .name("topic-by-name")
        .description("Topic by Name")
        .group("Topic")
            .options(
                CommandOption.with().longName("userId").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("name").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.topicByName(ctx.optionValue("userId"), ctx.optionValue("name"))?.toString() ?: "" })

    @Bean
    fun joinCommand(): Command = Command.builder()
        .name("join")
        .description("Subscribe to a topic")
        .group("Topic")
            .options(
                CommandOption.with().longName("userId").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("topicName").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.join(ctx.optionValue("userId"), ctx.optionValue("topicName")).let { "" } })

    @Bean
    fun leaveCommand(): Command = Command.builder()
        .name("leave")
        .description("unSubscribe to a topic")
        .group("Topic")
            .options(
                CommandOption.with().longName("userId").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("topicName").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.leave(ctx.optionValue("userId"), ctx.optionValue("topicName")).let { "" } })

    @Bean
    fun memberOfCommand(): Command = Command.builder()
        .name("member-of")
        .description("Show what topics user is subscribed to")
        .group("Topic")
            .options(
                CommandOption.with().longName("userId").required(false).defaultValue("_").type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.memberOf(ctx.optionValue("userId"))?.toString() ?: "" })

    @Bean
    fun listMembersCommand(): Command = Command.builder()
        .name("list-members")
        .description("Show Subscribers on a topic")
        .group("Topic")
            .options(
                CommandOption.with().longName("topicName").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.listMembers(ctx.optionValue("topicName"))?.toString() ?: "" })

}
