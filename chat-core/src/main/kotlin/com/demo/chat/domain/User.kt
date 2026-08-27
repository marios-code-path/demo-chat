package com.demo.chat.domain

import java.time.Instant
import java.util.*

interface User<T> : KeyBearer<T> {
    val name: String
    val handle: String
    val imageUri: String
    val timestamp: Instant

    companion object Factory {
        @JvmStatic
        fun<T> create(key: Key<T>, name: String, handle: String, imageUri: String): User<T> = object : User<T> {
            override val key: Key<T>
                get() = key
            override val name: String
                get() = name
            override val handle: String
                get() = handle
            override val imageUri: String
                get() = imageUri
            override val timestamp: Instant
                get() = Instant.now()

            override fun equals(k2: Any?): Boolean =
                (k2 != null && k2::class == this::class) &&
                        (k2 is User<*> &&
                                k2.key == this.key &&
                                k2.name == this.name &&
                                k2.handle == this.handle &&
                                k2.imageUri == this.imageUri)

        }
    }
}