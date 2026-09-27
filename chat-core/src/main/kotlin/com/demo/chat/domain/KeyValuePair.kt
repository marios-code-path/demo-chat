package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

interface KeyBearer<T> {
    val key: Key<T>
}

interface NoKey<T>: Key<T>

object Empty

/**
 * A key names one object. [id] names the object, and [root] names the domain
 * or identity root that minted it. See `CHAT-avduuqwp`.
 *
 * The root is required. A key service mints a key with the root of its domain.
 * A root key is its own root. Equality reads [id], [root] and [empty]. It does
 * not verify the root. `KeyVerifier` does that against the registry.
 *
 * Three classes implement this interface: `SimpleKey`, `EmptyKey` and
 * `SimpleMessageKey`. Each uses `KeyEquality`, so equality is symmetric.
 */
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("key")
@JsonSubTypes(JsonSubTypes.Type(MessageKey::class))
interface Key<T> {
    val id: T
    val root: T
    val empty: Boolean

    companion object Factory {
        /** This method builds the key of object [id], minted under [root]. */
        @JvmStatic
        fun <T> of(id: T, root: T): Key<T> = SimpleKey(id, root)

        /** This method builds a root key. A root key is its own root. */
        @JvmStatic
        fun <T> root(id: T): Key<T> = SimpleKey(id, id)

        /** This method builds an empty key. It never equals a populated key. */
        @JvmStatic
        fun <T> empty(placeholder: T, root: T): Key<T> = EmptyKey(placeholder, root)
    }
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("keyValue")
@JsonSubTypes(
    JsonSubTypes.Type(value = Message::class, name = "Message")
)
interface KeyValuePair<T, out E> : KeyBearer<T> {
    val data: E

    companion object Factory {
        @JvmStatic
        fun <T, E> create(key: Key<T>, data: E): KeyValuePair<T, E> =
            @com.fasterxml.jackson.annotation.JsonTypeName("keyValue") object : KeyValuePair<T, E> {
                override val key: Key<T>
                    get() = key
                override val data: E
                    get() = data

                override fun equals(k2: Any?): Boolean =
                    (k2 != null && k2::class == this::class) &&
                            (k2 is KeyValuePair<*, *> &&
                                    k2.data == this.data &&
                                    k2.key == this.key)
            }
    }

}