package com.demo.chat.test

import com.demo.chat.domain.*
import java.util.function.Supplier

object TestUserSupplier : Supplier<User<String>> {
    override fun get(): User<String> = User.create(Key.anyKey(randomAlphaNumeric(10)), "TEST", "TEST", "TEST")
}

object TestMessageSupplier : Supplier<Message<String, String>> {
    override fun get(): Message<String, String> =
            Message.create(MessageKey.create("TEST1", "TEST2", "TEST3"), "TEST", true)
}

object TestMessageTopicSupplier : Supplier<MessageTopic<String>> {
    override fun get(): MessageTopic<String> = MessageTopic.create(
            Key.funKey("TEST"), "TEST"
    )
}

object TestTopicMembershipSupplier : Supplier<TopicMembership<String>> {
    override fun get(): TopicMembership<String> = TopicMembership.create(
            "TEST1", "TEST2", "TEST3"
    )
}

object TestAnySupplier : Supplier<Any> {
    override fun get() = "TEST"
}