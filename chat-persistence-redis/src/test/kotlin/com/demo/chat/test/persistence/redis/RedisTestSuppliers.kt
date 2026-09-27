package com.demo.chat.test.persistence.redis

import com.demo.chat.service.core.PersistenceStore

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import java.util.UUID
import java.util.function.Supplier

/**
 * UUID-keyed entity suppliers for the Redis persistence tests.
 * (chat-core's TestSupplier.kt only provides String-keyed suppliers.)
 */
/** Each entity takes a key that its store minted, so the key carries the store root. See `CHAT-avduuqwp`, T5. */
class TestUUIDUserSupplier(private val store: PersistenceStore<UUID, User<UUID>>) : Supplier<User<UUID>> {
    private fun minted() = store.key().block()!!

    override fun get(): User<UUID> =
        User.create(minted(), "TEST", "TEST-${UUID.randomUUID()}", "TEST")
}

/** Each entity takes a key that its store minted, so the key carries the store root. See `CHAT-avduuqwp`, T5. */
class TestUUIDMessageTopicSupplier(private val store: PersistenceStore<UUID, MessageTopic<UUID>>) : Supplier<MessageTopic<UUID>> {
    private fun minted() = store.key().block()!!

    override fun get(): MessageTopic<UUID> =
        MessageTopic.create(minted(), "TEST")
}

/** Each entity takes a key that its store minted, so the key carries the store root. See `CHAT-avduuqwp`, T5. */
class TestUUIDMessageSupplier(private val store: PersistenceStore<UUID, Message<UUID, String>>) : Supplier<Message<UUID, String>> {
    private fun minted() = store.key().block()!!

    override fun get(): Message<UUID, String> =
        Message.create(
            minted().let { MessageKey.of(it.id, it.root, UUID.randomUUID(), UUID.randomUUID()) },
            "TEST",
            true,
        )
}

/** Each entity takes a key that its store minted, so the key carries the store root. See `CHAT-avduuqwp`, T5. */
class TestUUIDTopicMembershipSupplier(private val store: PersistenceStore<UUID, TopicMembership<UUID>>) : Supplier<TopicMembership<UUID>> {
    private fun minted() = store.key().block()!!

    override fun get(): TopicMembership<UUID> =
        TopicMembership.create(minted().id, UUID.randomUUID(), UUID.randomUUID())
}

/** Each entity takes a key that its store minted, so the key carries the store root. See `CHAT-avduuqwp`, T5. */
class TestUUIDAuthMetaSupplier(private val store: PersistenceStore<UUID, AuthMetadata<UUID>>) : Supplier<AuthMetadata<UUID>> {
    private fun minted() = store.key().block()!!

    override fun get(): AuthMetadata<UUID> =
        AuthMetadata.create(
            minted(),
            TestKeys.key(UUID.randomUUID()),
            TestKeys.key(UUID.randomUUID()),
            "TEST",
            false,
            Long.MAX_VALUE,
        )
}

/** Each entity takes a key that its store minted, so the key carries the store root. See `CHAT-avduuqwp`, T5. */
class TestUUIDKeyValuePairSupplier(private val store: PersistenceStore<UUID, KeyValuePair<UUID, Any>>) : Supplier<KeyValuePair<UUID, Any>> {
    private fun minted() = store.key().block()!!

    override fun get(): KeyValuePair<UUID, Any> =
        KeyValuePair.create(minted(), "TEST")
}