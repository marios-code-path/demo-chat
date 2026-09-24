package com.demo.chat.security.access

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyBearer
import com.demo.chat.domain.TopicMembership

/**
 * The target key of an entity that a filter reads. See `CHAT-wkwiipgy`.
 *
 * | Entity | Target key |
 * |---|---|
 * | A `KeyBearer`: `User`, `Message`, `MessageTopic`, `KeyValuePair`, `AuthMetadata` | its `key` |
 * | A `TopicMembership` | its raw `key` id, as a `Key` |
 * | Anything else | none, so the filter denies it |
 *
 * **`TopicMembership.key` is a raw id, not a `Key`.** A filter expression
 * that passed `filterObject.key` straight to the broker failed with `EL1004E`
 * for a `Long` id.
 *
 * **The list is closed.** An unknown entity answers no target, and no target
 * denies. A fallback that guessed a key would reopen the list.
 */
object EntityTargets {

    @Suppress("UNCHECKED_CAST")
    fun <T> keyOf(entity: Any?): Key<T>? = when (entity) {
        is KeyBearer<*> -> entity.key as Key<T>
        is TopicMembership<*> -> Key.funKey(entity.key as T)
        else -> null
    }
}
