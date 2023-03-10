package com.demo.chat.shell.commands

import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.shell.ShellStateConfiguration.Companion.loggedInUser
import org.springframework.shell.Availability

open class CommandsUtil<T>(
    private val typeUtil: TypeUtil<T>,
    private val rootKeys: RootKeys<T>
) {

    open fun identity(uId: String): T {
        return when (uId) {
            "_" -> {
                loggedInUser
                    .map { typeUtil.assignFrom(it) }
                    .orElseGet { rootKeys.getRootKey(Anon::class.java).id }
            }

            else -> typeUtil.fromString(uId)
        }
    }

    open fun isAuthenticated(): Availability {
        return when (
            loggedInUser
                .map { typeUtil.assignFrom(it) != rootKeys.getRootKey(Anon::class.java).id }
                .orElseGet { false }
        ) {
            true -> Availability.available()
            else -> Availability.unavailable("Not logged in")
        }
    }

}