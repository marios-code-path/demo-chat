package com.demo.chat.shell.commands

import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyService
import com.demo.chat.shell.ShellStateConfiguration
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import reactor.core.publisher.Mono
import java.util.*

@ShellComponent
@Profile("shell")
class LoginCommands<T>(
    val userService: ChatUserService<T>,
    rootKeys: RootKeys<T>,
    typeUtil: TypeUtil<T>,
) : CommandsUtil<T>(typeUtil, rootKeys) {

    @ShellMethod("whoami")
    fun whoami(): String? {

        return userService
            .findByUserId(ByIdRequest(identity("_")))
            .map { user ->
                "${user.key.id} : ${user.handle} | ${user.name} | ${user.imageUri}"
            }
            .switchIfEmpty(Mono.error(NotFoundException))
            .block()
    }

    @ShellMethod("Login")
    fun login(
        @ShellOption username: String,
        @ShellOption password: String
    ) {
        try {
            val request = UsernamePasswordAuthenticationToken(username, password)
            ShellStateConfiguration.simpleAuthToken = Optional.of(request)
            ShellStateConfiguration.loggedInUser = Optional.of(
                userService
                    .findByUsername(ByStringRequest(username)).blockLast()?.key?.id as Any
            )
            // load authentication into clients
        } catch (e: AuthenticationException) {
            println("Authentication failed :" + e.message)
        } catch (e: AccessDeniedException) {
            println("Not authorized for : " + e.message)
        }
    }
}