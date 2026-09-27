package com.demo.chat.security.access

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.domain.KeyBearer
import com.demo.chat.domain.TopicMembership

/**
 * The target key of an entity that a filter reads. See `CHAT-wkwiipgy`.
 *
 * | Entity | Target key |
 * |---|---|
 * | A `KeyBearer`: `User`, `Message`, `MessageTopic`, `KeyValuePair`, `AuthMetadata` | its `key` |
 * | A `TopicMembership` | its raw `key` id, as a `Key` under the TOPIC_MEMBERSHIP root |
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

    /**
     * The entity came from a typed store, so a membership takes the root of
     * TOPIC_MEMBERSHIP. See `CHAT-avduuqwp`, C78.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> keyOf(entity: Any?, rootKeys: RootKeys<T>): Key<T>? = when (entity) {
        is KeyBearer<*> -> entity.key as Key<T>
        is TopicMembership<*> -> Key.of(entity.key as T, rootKeys.of(ChatDomain.TOPIC_MEMBERSHIP).id)
        else -> null
    }

    /**
     * The domain of an entity type. The list is closed, as [keyOf] is.
     * `MessageTopic` extends `KeyValuePair`, so it is tested first.
     */
    fun domainOf(entity: Any?): ChatDomain? = when (entity) {
        is User<*> -> ChatDomain.USER
        is Message<*, *> -> ChatDomain.MESSAGE
        is MessageTopic<*> -> ChatDomain.MESSAGE_TOPIC
        is AuthMetadata<*> -> ChatDomain.AUTH_METADATA
        is KeyValuePair<*, *> -> ChatDomain.KEY_VALUE_PAIR
        is TopicMembership<*> -> ChatDomain.TOPIC_MEMBERSHIP
        else -> null
    }
}
