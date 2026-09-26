package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.Objects

/**
 * The one equality rule of every `Key`. See `CHAT-avduuqwp`.
 *
 * Two keys are equal when [Key.id], [Key.root] and [Key.empty] are equal. The
 * implementing class does not take part, so equality is symmetric. The `from`,
 * `dest` and `timestamp` of a message key do not take part either.
 * **Equality does not verify a root.** `KeyVerifier` does that.
 */
object KeyEquality {
    fun equals(a: Key<*>, other: Any?): Boolean =
        other is Key<*> && other.empty == a.empty && other.id == a.id && other.root == a.root

    fun hash(k: Key<*>): Int = Objects.hash(k.id, k.root, k.empty)

    /**
     * A key needs a non-null id and a non-null root. The generic type admits a
     * nullable argument, so every canonical class checks at construction.
     */
    fun requireMembers(id: Any?, root: Any?) {
        requireNotNull(id) { "A key needs an id. The id is null." }
        requireNotNull(root) { "A key needs a root. The root is null." }
    }
}

@JsonTypeName("key")
class SimpleKey<T>(override val id: T, override val root: T) : Key<T> {
    init {
        KeyEquality.requireMembers(id, root)
    }

    override val empty: Boolean get() = false
    override fun equals(other: Any?) = KeyEquality.equals(this, other)
    override fun hashCode() = KeyEquality.hash(this)
    override fun toString() = id.toString()
}

@JsonTypeName("key")
class EmptyKey<T>(override val id: T, override val root: T) : NoKey<T> {
    init {
        KeyEquality.requireMembers(id, root)
    }

    override val empty: Boolean get() = true
    override fun equals(other: Any?) = KeyEquality.equals(this, other)
    override fun hashCode() = KeyEquality.hash(this)
    override fun toString() = id.toString()
}

/**
 * The key of one message. [from] and [dest] are payload, and they do not take
 * part in equality. [timestamp] records when this object was built.
 */
@JsonTypeName("key")
class SimpleMessageKey<T>(
    override val id: T,
    override val root: T,
    override val from: T,
    override val dest: T,
    override val timestamp: Instant = Instant.now(),
) : MessageKey<T> {
    init {
        KeyEquality.requireMembers(id, root)
    }

    override val empty: Boolean get() = false
    override fun equals(other: Any?) = KeyEquality.equals(this, other)
    override fun hashCode() = KeyEquality.hash(this)
    override fun toString() = id.toString()
}
