package com.demo.chat.test.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.test.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.time.Duration
import java.util.*

class MessageTests : TestBase() {

    @Test
    fun `should create`() {
        Assertions
                .assertThat(Message
                        .create(MessageKey.create("Key1", "Key2", "Key3"),
                        "TEST", true))
                .isNotNull
                .hasNoNullFieldsOrProperties()
    }

    @Test
    fun `message keys with the same id should be equal`() {
        val k1 = MessageKey.create(1001L, 1002L, 1003L)
        val k2 = MessageKey.create(1001L, 1002L, 1003L)

        Assertions
            .assertThat(k1)
            .isEqualTo(k2)

        Assertions
            .assertThat(k1.hashCode())
            .isEqualTo(k2.hashCode())
    }

    @Test
    fun `a map keyed on a message key should find an equal message key`() {
        val map = hashMapOf(MessageKey.create(1001L, 1002L, 1003L) to "found")

        Assertions
            .assertThat(map[MessageKey.create(1001L, 1002L, 1003L)])
            .isEqualTo("found")
    }

    @Test
    fun `a message key should equal a populated key with the same id in either direction`() {
        val messageKey = MessageKey.create(1001L, 1002L, 1003L)
        val key = Key.funKey(1001L)

        Assertions
            .assertThat(messageKey)
            .isEqualTo(key)

        Assertions
            .assertThat(key)
            .isEqualTo(messageKey)
    }

    @Test
    fun `the id alone decides message key equality`() {
        Assertions
            .assertThat(MessageKey.create(1001L, 1002L, 1003L))
            .isEqualTo(MessageKey.create(1001L, 2002L, 2003L))

        Assertions
            .assertThat(MessageKey.create(1001L, 1002L, 1003L))
            .isNotEqualTo(MessageKey.create(2001L, 1002L, 1003L))
    }

    @Test
    fun `should test streaming only through publisher`() {
        val messagePub = TestPublisher.create<Message<out Any, Any>>()
        val messageFlux = messagePub.flux()

        StepVerifier
                .create(messageFlux)
                .expectSubscription()
                .then {
                    messagePub.next(randomMessage())
                    messagePub.next(randomMessage())
                }
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .then {
                    messagePub.complete()
                }
                .expectComplete()
                .verify(Duration.ofSeconds(1))

    }

    private fun randomMessage(): Message<UUID, Any> {

        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        return if (counter % 2 == 0)
            TestAlert(TestAlertKey(messageId, roomId, roomId), counter)
        else
            TestTextMessage(TestMessageKey(messageId, roomId, userId), "Count: $counter")
    }
}