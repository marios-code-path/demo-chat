package com.demo.chat.domain.knownkey

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import java.util.concurrent.ConcurrentHashMap

/**
 * The root key of each domain, and the two user identities.
 *
 * A domain root and an identity are different things. A domain root names a
 * domain. An identity names one user, so it is an object of the `User`
 * domain. Each has its own typed accessor. See `CHAT-avduuqwp`.
 *
 * **A partial domain set is refused.** [loadDomains] requires every
 * [ChatDomain]. So a node never serves with a partial set.
 */
class RootKeys<T> {

    private val domains: MutableMap<ChatDomain, Key<T>> = ConcurrentHashMap()
    private val identities: MutableMap<ChatIdentity, Key<T>> = ConcurrentHashMap()

    fun of(domain: ChatDomain): Key<T> = domains[domain] ?: throw ChatException(
        if (domains.isEmpty()) "The root key '${domain.wireName}' is missing. The root keys are not loaded."
        else "The root key '${domain.wireName}' is missing. Loaded: ${domains.keys.sorted().joinToString { it.wireName }}"
    )

    fun identity(identity: ChatIdentity): Key<T> = identities[identity]
        ?: throw ChatException("The ${identity.wireName} identity is not loaded.")

    fun admin(): Key<T> = identity(ChatIdentity.ADMIN)

    fun anon(): Key<T> = identity(ChatIdentity.ANON)

    /** This method returns the domain whose root has [id]. It returns null for any other id. */
    fun domainOfRoot(id: T): ChatDomain? = domains.entries.firstOrNull { it.value.id == id }?.key

    /**
     * This method returns the domain root or the identity that a configuration
     * name names. It returns null for any other name. The name is parsed into a
     * [ChatDomain] or a [ChatIdentity] first, so free text never reaches a map.
     */
    fun byName(name: String): Key<T>? =
        ChatDomain.parse(name)?.let { domains[it] }
            ?: ChatIdentity.parse(name)?.let { identities[it] }

    fun loadDomains(roots: Map<ChatDomain, Key<T>>) {
        val missing = ChatDomain.entries - roots.keys
        require(missing.isEmpty()) { "A root key set must name every domain. Missing: $missing" }
        domains.putAll(roots)
    }

    fun loadIdentities(admin: Key<T>, anon: Key<T>) {
        identities[ChatIdentity.ADMIN] = admin
        identities[ChatIdentity.ANON] = anon
    }

    fun domains(): Map<ChatDomain, Key<T>> = domains.toMap()

    fun identities(): Map<ChatIdentity, Key<T>> = identities.toMap()

    /**
     * Every loaded root and identity, keyed by its wire name.
     *
     * The actuator endpoint and the kv publish path use this form until T2
     * replaces it with the root key snapshot.
     */
    fun byWireName(): Map<String, Key<T>> =
        domains.mapKeys { it.key.wireName } + identities.mapKeys { it.key.wireName }

    /**
     * This method loads a map that [byWireName] wrote. Each name must parse to
     * a domain or an identity. The domain set must be complete.
     */
    fun loadByWireName(named: Map<String, Key<T>>) {
        val unknown = named.keys.filter { ChatDomain.parse(it) == null && ChatIdentity.parse(it) == null }
        require(unknown.isEmpty()) { "A root key map names an unknown domain or identity: $unknown" }
        loadDomains(named.mapNotNull { (k, v) -> ChatDomain.parse(k)?.let { it to v } }.toMap())
        val admin = named[ChatIdentity.ADMIN.wireName]
        val anon = named[ChatIdentity.ANON.wireName]
        if (admin != null && anon != null) loadIdentities(admin, anon)
    }

    companion object {
        fun <T> rootKeySummary(rootKeys: RootKeys<T>): String =
            "Root Keys: \n" + rootKeys.byWireName().entries.joinToString("") { "${it.key}=${it.value}\n" }
    }
}
