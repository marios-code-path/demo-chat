package com.demo.chat.test.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.test.TestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher


class TopicTests : TestBase() {
    @Test
    fun `should create`() {
        Assertions
                .assertThat(MessageTopic.create(Key.funKey("Key1"), "TEST"))
                .isNotNull
                .hasNoNullFieldsOrProperties()
    }

    @Test
    fun `should test streaming only through publisher`() {
        val topicPub = TestPublisher.create<MessageTopic<out Any>>()
        val topicFlux = topicPub.flux()

        StepVerifier
                .create(topicFlux)
                .expectSubscription()
                .then {
                    topicPub.next(MessageTopic.create(Key.funKey(3), "Test-Topic-3"))
                    topicPub.next(MessageTopic.create(Key.funKey("foo-3"), "Test-Topic-foo"))
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
                    topicPub.complete()
                }
                .verifyComplete()
    }
}