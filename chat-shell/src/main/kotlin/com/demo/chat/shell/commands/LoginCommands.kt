package com.demo.chat.shell.commands

import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.init.RootKeyService
import com.demo.chat.shell.ShellStateConfiguration.Companion.loggedInUser
import com.demo.chat.shell.ShellStateConfiguration.Companion.loginMetadata
import org.springframework.context.annotation.Profile
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import reactor.core.publisher.Mono
import java.util.*

@ShellComponent
@Profile("shell")
class LoginCommands<T>(
    val userService: ChatUserService<T>,
    val rootKeys: RootKeys<T>,
    typeUtil: TypeUtil<T>,
) : CommandsUtil<T>(typeUtil, rootKeys) {

    @ShellMethod("rootkeys")
    fun rootKeys(): String {
        return RootKeyService.rootKeySummary(rootKeys)
    }

    @ShellMethod("whoami")
    fun whoami(): String? {

        return userService
            .findByUserId(ByIdRequest(identity("_")))
            .map { user -> "${user.key.id} : ${user.handle} | ${user.name} | ${user.imageUri}" }
            .switchIfEmpty(Mono.error(NotFoundException))
            .block()
    }

    @ShellMethod("login")
    fun login(
        @ShellOption username: String,
        @ShellOption password: String
    ) {
        try {
            loginMetadata = Optional.of(UsernamePasswordMetadata(username, password))
            val user = userService.findByUsername(ByStringRequest(username)).blockLast()!!

            loggedInUser = Optional.of(user.key.id as Any)
            // load authentication into clients
        } catch (e: Exception) {
            println("Authentication failed :" + e.message)

            loginMetadata = Optional.empty()
            loggedInUser = Optional.empty()

            throw e
        }
    }
}