package com.demo.chat.test

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.*
import java.util.function.Supplier

object TestUserSupplier : Supplier<User<String>> {
    override fun get(): User<String> = User.create(TestKeys.key(randomAlphaNumeric(10)), "TEST", "TEST", "TEST")
}

object TestMessageSupplier : Supplier<Message<String, String>> {
    override fun get(): Message<String, String> =
            Message.create(TestKeys.message("TEST1", "TEST2", "TEST3"), "TEST", true)
}

object TestMessageTopicSupplier : Supplier<MessageTopic<String>> {
    override fun get(): MessageTopic<String> = MessageTopic.create(
            TestKeys.key("TEST"), "TEST"
    )
}

object TestTopicMembershipSupplier : Supplier<TopicMembership<String>> {
    override fun get(): TopicMembership<String> = TopicMembership.create(
            "TEST1", "TEST2", "TEST3"
    )
}

object TestAuthMetaSupplier : Supplier<AuthMetadata<String>> {
    override fun get() = AuthMetadata.create(TestKeys.key(randomAlphaNumeric(10)),
        TestKeys.key(randomAlphaNumeric(10)),
        TestKeys.key(randomAlphaNumeric(10)), "TEST", Long.MAX_VALUE)
}

object TestKeyValuePairSupplier : Supplier<KeyValuePair<String, Any>> {
    override fun get() = KeyValuePair.create(TestKeys.key(randomAlphaNumeric(10)), "TEST")
}

object TestLongKeyValuePairSupplier : Supplier<KeyValuePair<Long, Any>> {
    override fun get() = KeyValuePair.create(TestKeys.key(1L), "TEST")
}

object TestAnySupplier : Supplier<Any> {
    override fun get() = "TEST"
}