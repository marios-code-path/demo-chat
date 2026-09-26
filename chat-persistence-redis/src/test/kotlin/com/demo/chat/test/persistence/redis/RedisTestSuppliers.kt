package com.demo.chat.test.persistence.redis

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
object TestUUIDUserSupplier : Supplier<User<UUID>> {
    override fun get(): User<UUID> =
        User.create(TestKeys.key(UUID.randomUUID()), "TEST", "TEST", "TEST")
}

object TestUUIDMessageTopicSupplier : Supplier<MessageTopic<UUID>> {
    override fun get(): MessageTopic<UUID> =
        MessageTopic.create(TestKeys.key(UUID.randomUUID()), "TEST")
}

object TestUUIDMessageSupplier : Supplier<Message<UUID, String>> {
    override fun get(): Message<UUID, String> =
        Message.create(
            TestKeys.message(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            "TEST",
            true,
        )
}

object TestUUIDTopicMembershipSupplier : Supplier<TopicMembership<UUID>> {
    override fun get(): TopicMembership<UUID> =
        TopicMembership.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
}

object TestUUIDAuthMetaSupplier : Supplier<AuthMetadata<UUID>> {
    override fun get(): AuthMetadata<UUID> =
        AuthMetadata.create(
            TestKeys.key(UUID.randomUUID()),
            TestKeys.key(UUID.randomUUID()),
            TestKeys.key(UUID.randomUUID()),
            "TEST",
            false,
            Long.MAX_VALUE,
        )
}

object TestUUIDKeyValuePairSupplier : Supplier<KeyValuePair<UUID, Any>> {
    override fun get(): KeyValuePair<UUID, Any> =
        KeyValuePair.create(TestKeys.key(UUID.randomUUID()), "TEST")
}