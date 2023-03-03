package com.demo.chat.init.commands

import com.demo.chat.domain.TypeUtil
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.shell.Availability

open class CommandsUtil<T> (private var typeUtil: TypeUtil<T>) {

    //        "_" -> typeUtil.fromString(SecurityContextHolder.getContext().authentication.details.toString())
    open fun identity(uid: String): T = when (uid) {
        "_" -> ReactiveSecurityContextHolder
            .getContext()
            .map {ctx ->
                println(ctx.authentication?.principal.toString())
                typeUtil.fromString(ctx.authentication.details.toString())
            }
            .block()!!
        else -> typeUtil.fromString(uid)
    }

    open fun isAuthenticated(): Availability =
        when (SecurityContextHolder.getContext().authentication) {
            null -> Availability.unavailable("not logged in")
            else -> Availability.available()
        }
}