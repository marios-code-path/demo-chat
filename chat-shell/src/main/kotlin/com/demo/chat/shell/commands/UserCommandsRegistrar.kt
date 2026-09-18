package com.demo.chat.shell.commands

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandOption
import java.util.function.Function

/**
 * The programmatic registration of the UserCommands commands.
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
class UserCommandsRegistrar<T : Any>(private val commands: UserCommands<T>) {
    @Bean
    fun kvCommand(): Command = Command.builder()
        .name("kv")
        .description("Create a KeyValue")
        .group("User")
            .options(
                CommandOption.with().longName("value").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.kv(ctx.optionValue("value"))?.toString() ?: "" })

    @Bean
    fun getKVCommand(): Command = Command.builder()
        .name("get-k-v")
        .description("Get a KeyValue by Key ID")
        .group("User")
            .options(
                CommandOption.with().longName("key").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.getKV(commands.keyOf(ctx.optionValue("key")))?.toString() ?: "" })

    @Bean
    fun allKVCommand(): Command = Command.builder()
        .name("all-k-v")
        .description("Get all KV")
        .group("User")
        .execute(Function<CommandContext, String> { ctx -> commands.allKV()?.toString() ?: "" })

    @Bean
    fun keyCommand(): Command = Command.builder()
        .name("key")
        .description("Create a Key")
        .group("User")
        .execute(Function<CommandContext, String> { ctx -> commands.key()?.toString() ?: "" })

    @Bean
    fun addUserCommand(): Command = Command.builder()
        .name("add-user")
        .description("Add A User")
        .group("User")
            .options(
                CommandOption.with().longName("name").required(true).type(String::class.java).build(),
                CommandOption.with().longName("handle").required(true).type(String::class.java).build(),
                CommandOption.with().longName("imageUri").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.addUser(ctx.optionValue("name"), ctx.optionValue("handle"), ctx.optionValue("imageUri"))?.toString() ?: "" })

    @Bean
    fun usersCommand(): Command = Command.builder()
        .name("users")
        .description("All Users")
        .group("User")
        .execute(Function<CommandContext, String> { ctx -> commands.users()?.toString() ?: "" })

    @Bean
    fun findUserCommand(): Command = Command.builder()
        .name("find-user")
        .description("Find a user")
        .group("User")
            .options(
                CommandOption.with().longName("handle").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.findUser(ctx.optionValue("handle"))?.toString() ?: "" })

    @Bean
    fun getUserCommand(): Command = Command.builder()
        .name("get-user")
        .description("Get a user")
        .group("User")
            .options(
                CommandOption.with().longName("handle").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.getUser(ctx.optionValue("handle"))?.toString() ?: "" })

    @Bean
    fun passwdCommand(): Command = Command.builder()
        .name("passwd")
        .description("Change User Password")
        .group("User")
            .options(
                CommandOption.with().longName("userId").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("password").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.passwd(ctx.optionValue("userId"), ctx.optionValue("password"))?.toString() ?: "" })

    @Bean
    fun getPermissionsForUserCommand(): Command = Command.builder()
        .name("get-permissions-for-user")
        .description("Gets user Permissions")
        .group("User")
            .options(
                CommandOption.with().longName("userId").required(false).defaultValue("_").type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.getPermissionsForUser(ctx.optionValue("userId"))?.toString() ?: "" })

    @Bean
    fun allPermissionsCommand(): Command = Command.builder()
        .name("all-permissions")
        .description("Get all Perms")
        .group("User")
        .execute(Function<CommandContext, String> { ctx -> commands.allPermissions()?.toString() ?: "" })

    @Bean
    fun addPermissionCommand(): Command = Command.builder()
        .name("add-permission")
        .description("Add a User Permission")
        .group("User")
            .options(
                CommandOption.with().longName("userId").required(false).defaultValue("_").type(String::class.java).build(),
                CommandOption.with().longName("targetUserId").required(true).type(String::class.java).build(),
                CommandOption.with().longName("role").required(true).type(String::class.java).build(),
                CommandOption.with().longName("expireTime").required(true).type(String::class.java).build(),
            )
        .execute(Function<CommandContext, String> { ctx -> commands.addPermission(ctx.optionValue("userId"), ctx.optionValue("targetUserId"), ctx.optionValue("role"), ctx.optionValue("expireTime")).let { "" } })

}
