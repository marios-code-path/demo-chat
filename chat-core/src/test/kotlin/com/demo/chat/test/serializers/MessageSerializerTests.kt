package com.demo.chat.test.serializers

import com.demo.chat.codec.Codec
import com.demo.chat.domain.*
import com.demo.chat.domain.serializers.JsonNodeCodec
import com.demo.chat.domain.serializers.KeyDeserializer
import com.demo.chat.domain.serializers.MessageDeserializer
import com.demo.chat.domain.serializers.TextMessageDeserializer
import com.demo.chat.test.TestBase
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

class MessageSerializerTests : TestBase() {

    @Test
    fun `Any Message deserialize`() {
        mapper.apply {
            registerModules(SimpleModule("CustomDeser", Version.unknownVersion()).apply {
                addDeserializer(Message::class.java, MessageDeserializer(JsonNodeCodec))
                addDeserializer(TextMessage::class.java, TextMessageDeserializer(JsonNodeCodec))
            })
        }

        val messageJsons = Flux.just(
                Message.create(Key.anyKey(1L), "foo", true),
                Message.create(MessageKey.create("a", "b"), "Foo", true),
                TextMessage.create(UserMessageKey.create(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
                ), "foo", true)
        )
                .map(mapper::writeValueAsString).doOnNext(System.out::println)
                .map<Message<out Any, out Any>>(mapper::readValue)

        StepVerifier
                .create(messageJsons)
                .expectSubscription()
                .assertNext { message ->
                    Assertions
                            .assertThat(message.key)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("id", 1L)
                            .extracting("id")
                            .isInstanceOf(Number::class.java)
                }
                .assertNext { message ->
                    Assertions
                            .assertThat(message.key)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("id", "a")
                            .extracting("id")
                            .isInstanceOf(String::class.java)
                }
                .assertNext { message ->
                    Assertions
                            .assertThat(message.key)
                            .isNotNull
                            .extracting("id")
                            .isInstanceOf(UUID::class.java)
                }
                .expectComplete()
                .verify(Duration.ofMillis(500))
    }
}