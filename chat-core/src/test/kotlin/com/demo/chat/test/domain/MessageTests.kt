package com.demo.chat.test.domain

import com.demo.chat.test.key.TestKeys

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
                        .create(TestKeys.message("Key1", "Key2", "Key3"),
                        "TEST", true))
                .isNotNull
                .hasNoNullFieldsOrProperties()
    }

    @Test
    fun `message keys with the same id should be equal`() {
        val k1 = TestKeys.message(1001L, 1002L, 1003L)
        val k2 = TestKeys.message(1001L, 1002L, 1003L)

        Assertions
            .assertThat(k1)
            .isEqualTo(k2)

        Assertions
            .assertThat(k1.hashCode())
            .isEqualTo(k2.hashCode())
    }

    @Test
    fun `a map keyed on a message key should find an equal message key`() {
        val map = hashMapOf(TestKeys.message(1001L, 1002L, 1003L) to "found")

        Assertions
            .assertThat(map[TestKeys.message(1001L, 1002L, 1003L)])
            .isEqualTo("found")
    }

    @Test
    fun `a message key should equal a populated key with the same id in either direction`() {
        val messageKey = TestKeys.message(1001L, 1002L, 1003L)
        val key = TestKeys.key(1001L)

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
            .assertThat(TestKeys.message(1001L, 1002L, 1003L))
            .isEqualTo(TestKeys.message(1001L, 2002L, 2003L))

        Assertions
            .assertThat(TestKeys.message(1001L, 1002L, 1003L))
            .isNotEqualTo(TestKeys.message(2001L, 1002L, 1003L))
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
            TestAlert(TestKeys.message(messageId, roomId, roomId), counter)
        else
            TestTextMessage(TestKeys.message(messageId, userId, roomId), "Count: $counter")
    }
}