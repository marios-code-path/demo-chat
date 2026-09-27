package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys

/**
 * This fixture builds a complete `RootKeys` for a test. `RootKeys` refuses a
 * partial domain set, so the fixture fills each domain that a test does not
 * name with a key from [filler]. See `CHAT-avduuqwp`.
 */
object RootKeysFixture {

    fun <T> of(
        named: Map<ChatDomain, Key<T>>,
        admin: Key<T>,
        anon: Key<T>,
        filler: (ChatDomain) -> Key<T>,
    ): RootKeys<T> = RootKeys<T>().apply {
        loadDomains(ChatDomain.entries.associateWith { named[it] ?: filler(it) })
        loadIdentities(admin, anon)
    }

    /** This function fills each unnamed domain of a `Long` set with a root key from 9000 up. */
    fun ofLong(named: Map<ChatDomain, Key<Long>>, admin: Key<Long>, anon: Key<Long>): RootKeys<Long> =
        of(named, admin, anon) { Key.root(9000L + it.ordinal) }

    /**
     * Root keys where [domain] has the fixed test root, and every other domain
     * has a distinct root. A test whose entities carry the test root then
     * writes to a store of [domain]. See `CHAT-avduuqwp`, T5.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> forTestRoot(domain: ChatDomain, type: Class<*>): RootKeys<T> {
        val testRoot: T = when (type) {
            Long::class.java, java.lang.Long::class.java -> TestRoots.LONG
            java.util.UUID::class.java -> TestRoots.UUID_ROOT
            String::class.java -> TestRoots.STRING
            else -> throw IllegalArgumentException("No test root for $type")
        } as T
        val filler = { d: ChatDomain -> Key.root<T>(TestRoots.of(type, d)) }
        val userRoot = if (domain == ChatDomain.USER) testRoot else filler(ChatDomain.USER).id
        // The identity ids differ from every root id.
        val (admin, anon) = when (type) {
            Long::class.java, java.lang.Long::class.java -> Pair(-2001L, -2002L)
            java.util.UUID::class.java -> Pair(java.util.UUID(0x7E58L, 1L), java.util.UUID(0x7E58L, 2L))
            else -> Pair("test-admin", "test-anon")
        }
        return of(
            mapOf(domain to Key.root(testRoot)),
            Key.of(admin as T, userRoot),
            Key.of(anon as T, userRoot),
            filler,
        )
    }
}
