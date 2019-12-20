package com.demo.chat.test.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestTopic
import com.demo.chat.test.randomAlphaNumeric
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.util.*
import java.util.stream.Stream
import kotlin.random.Random


class TopicTests : TestBase() {

    @Test
    fun `Should serialize deserialize JSON from to Any Topic`() {
        val topicJsons = ArrayList<String>()
        val topics = ArrayList<MessageTopic<out Any>>()

        Stream.generate { MessageTopic.create(Key.anyKey(Random.nextInt()), "Test-Topic-R") }.limit(5)
                .forEach { msg ->
                    topicJsons.add(mapper.writeValueAsString(msg))
                }
        Stream.generate { MessageTopic.create(Key.anyKey(randomAlphaNumeric(10)), "Test-Topic-Z") }.limit(5)
                .forEach { msg ->
                    topicJsons.add(mapper.writeValueAsString(msg))
                }
        Stream.generate { MessageTopic.create(Key.anyKey(UUID.randomUUID()), "Test-Topic-U") }.limit(5)
                .forEach { msg ->
                    topicJsons.add(mapper.writeValueAsString(msg))
                }

        topicJsons
                .forEach { json ->
                    val tree = mapper.readTree(json)

                    if (tree.fieldNames().hasNext()) {
                        when (tree.fieldNames().next()) {
                            "Topic" -> {
                                topics.apply {
                                    val topic = mapper.readValue<TestTopic<out Any>>(json)
                                    add(topic)
                                }
                            }
                            else -> {
                            }
                        }
                    }
                }

        Assertions.assertThat(topics)
                .`as`("A collection of topics is present")
                .isNotNull
                .isNotEmpty

        topics
                .forEach { topic ->
                    Assertions
                            .assertThat(topic)
                            .`as`("Is a Topic")
                            .isNotNull
                            .hasFieldOrProperty("key")
                            .hasFieldOrProperty("data")
                }
    }

    @Test
    fun `should test streaming only through publisher`() {
        val topicPub = TestPublisher.create<MessageTopic<out Any>>()
        val topicFlux = topicPub.flux()

        StepVerifier
                .create(topicFlux)
                .expectSubscription()
                .then {
                    topicPub.next(MessageTopic.create(Key.anyKey(3), "Test-Topic-3"))
                    topicPub.next(MessageTopic.create(Key.anyKey("foo-3"), "Test-Topic-foo"))
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