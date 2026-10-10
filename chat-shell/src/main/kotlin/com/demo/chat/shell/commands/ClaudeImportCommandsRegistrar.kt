package com.demo.chat.shell.commands

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandOption
import java.util.function.Function

@Configuration
@Profile("shell")
class ClaudeImportCommandsRegistrar<T : Any>(private val commands: ClaudeImportCommands<T>) {
    @Bean
    fun importClaudeCommand(): Command = Command.builder()
        .name("import-claude")
        .description("Import one Claude conversation")
        .group("Import")
        .options(
            option("file"), option("conversation"), option("topic"),
            option("human"), option("assistant"),
        )
        .execute(Function<CommandContext, String> { ctx ->
            commands.importConversation(
                ctx.optionValue("file"), ctx.optionValue("conversation"), ctx.optionValue("topic"),
                ctx.optionValue("human"), ctx.optionValue("assistant"),
            )
        })

    private fun option(name: String): CommandOption =
        CommandOption.with().longName(name).required(true).type(String::class.java).build()
}
