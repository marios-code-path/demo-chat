package com.demo.chat.test.domain

import com.demo.chat.domain.Message
import com.demo.chat.domain.TextMessage
import com.demo.chat.test.*
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.time.Duration
import java.util.*
import java.util.stream.Stream

class MessageTests : TestBase() {

    @Test
    fun `should test streaming only through publisher`() {
        val messagePub = TestPublisher.create<Message<out Any, out Any>>()
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

    private fun randomMessage(): Message<UUID, out Any> {

        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        return if (counter % 2 == 0)
            TestAlert(TestAlertKey(messageId, roomId), counter)
        else
            TestTextMessage(TestMessageKey(messageId, roomId, userId), "Count: $counter")
    }
}