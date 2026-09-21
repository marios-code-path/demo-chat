package com.demo.chat.shell.commands

import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.config.shell.deploy.ShellStateConfiguration.Companion.loggedInUser

open class CommandsUtil<T>(
    private val typeUtil: TypeUtil<T>,
    private val rootKeys: RootKeys<T>
) {

    open fun identity(uId: String): T {
        return when (uId) {
            "_" -> {
                loggedInUser
                    .map { typeUtil.assignFrom(it) }
                    .orElseGet { rootKeys.getRootKey(Anon::class.java.simpleName).id }
            }

            else -> typeUtil.fromString(uId)
        }
    }

    /**
     * The key conversion that a typed option needs.
     *
     * **Spring Shell 4 hands every option to the command as text.** The
     * declarative model converted a `T` parameter through a registered
     * converter, and the programmatic model does not. So a command that takes
     * a key converts it here, through the same TypeUtil that `identity` uses
     * for a value that is not the `_` placeholder.
     */
    open fun keyOf(value: String): T = typeUtil.fromString(value)

}