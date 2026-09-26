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

    /** This function fills each unnamed domain of a `Long` set with an id from 9000 up. */
    fun ofLong(named: Map<ChatDomain, Key<Long>>, admin: Key<Long>, anon: Key<Long>): RootKeys<Long> =
        of(named, admin, anon) { Key.funKey(9000L + it.ordinal) }
}
