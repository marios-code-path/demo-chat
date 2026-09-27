package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.knownkey.ChatDomain
import java.util.UUID

/**
 * Fixed roots for test keys. See `CHAT-avduuqwp`.
 *
 * A key needs a root, and a test that builds a value by hand has no key
 * service. [of] answers one fixed root for each id type. [of] with a domain
 * answers one fixed root for each type and domain, for mocks that mint.
 * No root equals an id that a test generator makes.
 */
object TestRoots {
    const val LONG: Long = -9L
    const val INT: Int = -9
    const val STRING: String = "test-root"
    val UUID_ROOT: UUID = UUID.fromString("00000000-0000-4000-8000-00000000c0de")

    @Suppress("UNCHECKED_CAST")
    fun <T> of(sample: T): T = when (sample) {
        is Long -> LONG
        is Int -> INT
        is Double -> -9.0
        is String -> STRING
        is UUID -> UUID_ROOT
        else -> throw IllegalArgumentException("No test root for ${sample?.let { it::class }}")
    } as T

    @Suppress("UNCHECKED_CAST")
    fun <T> of(type: Class<*>, domain: ChatDomain): T = when (type) {
        Long::class.java, java.lang.Long::class.java, Number::class.java -> (-1000L - domain.ordinal)
        UUID::class.java -> UUID(0x7E57L, domain.ordinal + 1L)
        String::class.java -> "test-root-${domain.wireName}"
        else -> throw IllegalArgumentException("No test root for $type")
    } as T
}

/**
 * Test keys under the fixed roots of [TestRoots]. A test that needs a verified
 * key uses a key service instead.
 */
object TestKeys {
    fun <T> key(id: T): Key<T> = Key.of(id, TestRoots.of(id))

    fun <T> empty(id: T): Key<T> = Key.empty(id, TestRoots.of(id))

    fun <T> message(id: T, from: T, dest: T): MessageKey<T> = MessageKey.of(id, TestRoots.of(id), from, dest)
}
