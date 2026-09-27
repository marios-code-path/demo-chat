package com.demo.chat.config.deploy.init

import com.demo.chat.domain.ChatException
import org.springframework.core.env.Environment

/**
 * The one source of root keys for a process. See `CHAT-avduuqwp`.
 *
 * - [STORE]: the process reaches its store. It loads each root and creates a
 *   missing root with a conditional write. `app.rootkeys.consume.scheme` is
 *   unset.
 * - [KV]: the process reads a snapshot from the key-value store that
 *   `app.kv.rootkeys` names.
 * - [HTTP]: the process reads a snapshot from the actuator that
 *   `app.rootkeys.consume.source` names.
 * - [NONE]: the process holds no root keys. Only a role whose supported
 *   operations read no root may declare it, with `app.rootkeys.required=false`.
 *   The authorization server is that role.
 *
 * Any other combination fails the start with a named error. Each value must
 * be canonical text. A value with a space or another case is refused, because
 * the listener conditions compare the raw text.
 */
enum class RootKeySource {
    STORE, KV, HTTP, NONE;

    companion object {
        const val REQUIRED = "app.rootkeys.required"
        const val SCHEME = "app.rootkeys.consume.scheme"

        fun of(env: Environment): RootKeySource {
            val required = when (val text = env.getProperty(REQUIRED)) {
                null, "true" -> true
                "false" -> false
                else -> throw ChatException("$REQUIRED is '$text'. The supported values are 'true' and 'false'.")
            }
            // The listener conditions compare the raw text. So this check reads
            // the raw text too, and it refuses any text that is not canonical.
            val scheme = env.getProperty(SCHEME) ?: ""
            if (!required) {
                if (scheme.isNotEmpty()) throw ChatException(
                    "$REQUIRED=false declares a process with no root keys, but $SCHEME is '$scheme'. Set one or the other."
                )
                return NONE
            }
            return when (scheme) {
                "" -> STORE
                "kv" -> KV.also { requireProperty(env, "app.kv.rootkeys") }
                "http" -> HTTP.also { requireProperty(env, "app.rootkeys.consume.source") }
                else -> throw ChatException(
                    "$SCHEME is '$scheme'. The supported values are 'kv' and 'http', with no spaces. " +
                        "Leave it unset to load from the store."
                )
            }
        }

        private fun requireProperty(env: Environment, name: String) {
            if (env.getProperty(name).isNullOrBlank()) throw ChatException("$SCHEME needs $name, and $name is not set.")
        }
    }
}
