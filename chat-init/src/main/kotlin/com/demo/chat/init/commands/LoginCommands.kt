package com.demo.chat.init.commands

import com.demo.chat.domain.ByHandleRequest
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.SecretsStore
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.shell.Availability
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
) : CommandsUtil<T>(typeUtil){

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
            val user =
                userService.findByUsername(ByHandleRequest(username))
                    .switchIfEmpty(Mono.error(Exception("NO USER FOUND")))
                    .blockLast()!!

            val userKey = user.key

            val request = UsernamePasswordAuthenticationToken(username, password)
                .apply { details = userKey.id }
            val result = authenticationManager.authenticate(request)
            SecurityContextHolder.getContext().authentication = result
        } catch (e: AuthenticationException) {
            println("Authentication failed :" + e.message)
        } catch (e: AccessDeniedException) {
            println("Not authorized for : " + e.message)
        }
    }
}