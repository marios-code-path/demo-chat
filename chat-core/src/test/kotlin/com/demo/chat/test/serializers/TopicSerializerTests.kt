package com.demo.chat.test.serializers

import com.demo.chat.codec.JsonKeyDecoder
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.serializers.TopicDeserializer
import com.demo.chat.test.TestBase
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

class TopicSerializerTests : TestBase() {

    @Test
    fun `Any Topic deserialize`() {
        mapper.apply {
            registerModules(SimpleModule("CustomDeser", Version.unknownVersion()).apply {
                addDeserializer(MessageTopic::class.java, TopicDeserializer(JsonKeyDecoder))
            })
        }

        val topicJsons = Flux.just(
                MessageTopic.create(
                        Key.funKey(1L),
                        "MOON"
                ),
                MessageTopic.create(
                        Key.funKey(UUID.randomUUID()),
                        "MARS"
                ),
                MessageTopic.create(
                        Key.funKey("2"),
                        "VENUS"
                )
        )
                .map(mapper::writeValueAsString).doOnNext {println(it)}
                .map<MessageTopic<out Any>>(mapper::readValue)

        StepVerifier
                .create(topicJsons)
                .expectSubscription()
                .assertNext { topic ->
                    Assertions
                            .assertThat(topic)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("data", "MOON")
                            .extracting("key").extracting("id")
                            .isInstanceOf(Number::class.java)
                }
                .assertNext { topic ->
                    Assertions
                            .assertThat(topic)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("data", "MARS")
                            .extracting("key").extracting("id")
                            .isInstanceOf(UUID::class.java)
                }
                .assertNext { topic ->
                    Assertions
                            .assertThat(topic)
                            .hasFieldOrPropertyWithValue("data", "VENUS")
                            .extracting("key").extracting("id")
                            .isInstanceOf(String::class.java)
                }
                .expectComplete()
                .verify(Duration.ofMillis(500))
    }
}