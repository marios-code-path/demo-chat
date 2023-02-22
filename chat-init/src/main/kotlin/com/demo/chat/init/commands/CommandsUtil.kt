package com.demo.chat.init.commands

import com.demo.chat.domain.TypeUtil
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.shell.Availability

open class CommandsUtil<T> (private var typeUtil: TypeUtil<T>) {

    open fun identity(uid: String): T = when (uid) {
        "_" -> typeUtil.fromString(SecurityContextHolder.getContext().authentication.details.toString())
        else -> typeUtil.fromString(uid)
    }

    companion object {
        fun isAuthenticated(): Availability =
            when (SecurityContextHolder.getContext().authentication.isAuthenticated) {
                true -> Availability.available()
                else -> Availability.unavailable("not logged in")
            }
    }

}