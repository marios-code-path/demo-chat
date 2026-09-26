package com.demo.chat.domain.knownkey

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil

/**
 * The root keys that a process without store access reads, from HTTP or from
 * Consul. See `CHAT-avduuqwp`.
 *
 * This is a string keyed record, not a domain key. The ids are strings.
 * `RootIds` parses them strictly. A reader never creates a root.
 *
 * [domains] maps each domain wire name to a root id. [admin] and [anon] hold
 * the ids of the two identities.
 */
data class RootKeySnapshot(
    val keyType: String,
    val domains: Map<String, String>,
    val admin: String,
    val anon: String,
) {
    /**
     * This method loads the snapshot into [into]. It refuses a snapshot whose
     * key type differs from [expectedKeyType]. It also refuses a snapshot that
     * does not name every domain, or that names an unknown domain. It refuses
     * an id that `RootIds` refuses. It loads nothing until every id parses.
     */
    fun <T> load(into: RootKeys<T>, expectedKeyType: String, typeUtil: TypeUtil<T>) {
        if (keyType != expectedKeyType) throw ChatException(
            "The root key snapshot has key type '$keyType'. This process uses '$expectedKeyType'."
        )
        val unknown = domains.keys.filter { ChatDomain.parse(it) == null }
        if (unknown.isNotEmpty()) throw ChatException("The root key snapshot names an unknown domain: $unknown")
        val missing = ChatDomain.entries.filter { it.wireName !in domains.keys }
        if (missing.isNotEmpty()) throw ChatException("The root key snapshot is incomplete. Missing: $missing")

        // Parse every id before the first load, so a bad id publishes nothing.
        val roots = domains.entries.associate { (name, id) ->
            ChatDomain.parse(name)!! to Key.funKey(RootIds.parse(typeUtil, id, name))
        }
        val adminKey = Key.funKey(RootIds.parse(typeUtil, admin, ChatIdentity.ADMIN.wireName))
        val anonKey = Key.funKey(RootIds.parse(typeUtil, anon, ChatIdentity.ANON.wireName))
        val ids = roots.values.map { it.id } + adminKey.id + anonKey.id
        if (ids.toSet().size != ids.size) throw ChatException("The root key snapshot uses one id for two roots.")
        into.loadDomains(roots)
        into.loadIdentities(adminKey, anonKey)
    }

    companion object {
        /** This method builds a snapshot from a complete `RootKeys` with both identities. */
        fun <T> of(rootKeys: RootKeys<T>, keyType: String, typeUtil: TypeUtil<T>): RootKeySnapshot =
            RootKeySnapshot(
                keyType = keyType,
                domains = ChatDomain.entries.associate { it.wireName to typeUtil.toString(rootKeys.of(it).id) },
                admin = typeUtil.toString(rootKeys.admin().id),
                anon = typeUtil.toString(rootKeys.anon().id),
            )
    }
}
