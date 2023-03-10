package com.demo.chat.shell.commands

import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.shell.ShellStateConfiguration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.shell.Availability

open class CommandsUtil<T>(
    private val typeUtil: TypeUtil<T>,
    private val rootKeys: RootKeys<T>
) {

    open fun identity(uId: String): T = when (uId) {
        "_" -> {
            ShellStateConfiguration.loggedInUser
                .map { typeUtil.assignFrom(it) }
                .orElseGet { rootKeys.getRootKey(Anon::class.java).id }
        }

        else -> typeUtil.fromString(uId)
    }

    open fun isAuthenticated(): Availability =
        when (ShellStateConfiguration.simpleAuthToken.get().name) {
            Anon::class.java.simpleName -> Availability.unavailable("not logged in")
            else -> Availability.available()
        }
}