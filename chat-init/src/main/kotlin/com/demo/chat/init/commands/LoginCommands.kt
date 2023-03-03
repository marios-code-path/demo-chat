package com.demo.chat.init.commands

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatUserService
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellMethodAvailability
import org.springframework.shell.standard.ShellOption
import reactor.core.publisher.Mono

@ShellComponent
@Profile("shell")
class LoginCommands<T>(
    val userService: ChatUserService<T>,
    val authenticationManager: AuthenticationManager,
    val typeUtil: TypeUtil<T>
) : CommandsUtil<T>(typeUtil) {

    @ShellMethod("whoami")
    @ShellMethodAvailability("isAuthenticated")
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
            val result = authenticationManager.authenticate(request)
            SecurityContextHolder.getContext().authentication = result
        } catch (e: AuthenticationException) {
            println("Authentication failed :" + e.message)
        } catch (e: AccessDeniedException) {
            println("Not authorized for : " + e.message)
        }
    }
}