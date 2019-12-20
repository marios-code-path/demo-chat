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
    fun `Should serialize deserialize JSON from Any Message`() {
        val messageJsons = ArrayList<String>()
        val messages = ArrayList<Message<out Any, out Any>>()

        Stream.generate { randomMessage() }.limit(5)
                .forEach { msg ->
                    messageJsons.add(mapper.writeValueAsString(msg))
                }

        messageJsons
                .forEach { json ->
                    val tree = mapper.readTree(json)

                    if (tree.fieldNames().hasNext()) {
                        when (tree.fieldNames().next()) {
                            "Text" -> messages.add(mapper.readValue<TestTextMessage<out Any>>(json))
                            "Alert" -> messages.add(mapper.readValue<TestAlert<out Any>>(json))
                        }
                    }
                }

        Assertions.assertThat(messages)
                .`as`("A collection of messages is present")
                .isNotNull
                .isNotEmpty

        messages
                .forEach { msg ->
                    when (msg) {
                        is TextMessage -> Assertions.assertThat(msg.key).`as`("Has expected message state")
                                .isNotNull
                                .hasFieldOrProperty("id")
                        is TestAlert -> Assertions.assertThat(msg.key).`as`("Has expected alert state")
                                .isNotNull
                                .hasFieldOrProperty("dest")
                        else -> {
                            Assertions.assertThat(msg).`as`("Is a message")
                                    .isNotNull
                                    .hasFieldOrProperty("key")
                                    .hasFieldOrProperty("value")
                            Assertions.assertThat(msg).`as`("is a message key too")
                                    .isNotNull
                                    .hasFieldOrProperty("id")
                        }
                    }
                }
    }

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