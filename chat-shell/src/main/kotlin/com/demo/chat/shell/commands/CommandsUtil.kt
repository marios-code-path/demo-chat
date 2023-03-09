package com.demo.chat.shell.commands

import com.demo.chat.domain.TypeUtil
import com.demo.chat.secure.ChatUserDetails
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.shell.Availability
import reactor.core.publisher.Mono

open class CommandsUtil<T>(private var typeUtil: TypeUtil<T>) {

    open fun identity(uid: String): T = when (uid) {
        "_" -> {
            Mono.just(SecurityContextHolder.getContext().authentication)
                .contextWrite(
                    ReactiveSecurityContextHolder
                        .withAuthentication(SecurityContextHolder.getContext().authentication)
                )
                .map { auth ->
                    auth.principal as ChatUserDetails<T>
                }
                .map { user ->
                    user.user.key.id
                }
                .block()!!
        }

        else -> typeUtil.fromString(uid)
    }

    open fun isAuthenticated(): Availability =
        when (SecurityContextHolder.getContext().authentication) {
            null -> Availability.unavailable("not logged in")
            else -> Availability.available()
        }
}