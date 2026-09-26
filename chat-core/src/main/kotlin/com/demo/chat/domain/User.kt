package com.demo.chat.domain

import java.time.Instant
import java.util.*

interface User<T> : KeyBearer<T> {
    val name: String
    val handle: String
    val imageUri: String
    val timestamp: Instant

    companion object Factory {
        /** This method builds a user whose timestamp reads the current time on each read. */
        @JvmStatic
        fun<T> create(key: Key<T>, name: String, handle: String, imageUri: String): User<T> =
            SimpleUser(key, name, handle, imageUri) { Instant.now() }

        /** This method builds a user with a stored [timestamp]. A store read uses it. See `CHAT-avduuqwp`. */
        @JvmStatic
        fun<T> create(key: Key<T>, name: String, handle: String, imageUri: String, timestamp: Instant): User<T> =
            SimpleUser(key, name, handle, imageUri) { timestamp }
    }
}

/** Equality reads the key, name, handle and image. It does not read the timestamp. */
private class SimpleUser<T>(
    override val key: Key<T>,
    override val name: String,
    override val handle: String,
    override val imageUri: String,
    private val clock: () -> Instant,
) : User<T> {
    override val timestamp: Instant
        get() = clock()

    override fun equals(other: Any?): Boolean =
        other is SimpleUser<*> &&
                other.key == key &&
                other.name == name &&
                other.handle == handle &&
                other.imageUri == imageUri

    override fun hashCode(): Int = Objects.hash(key, name, handle, imageUri)
}
