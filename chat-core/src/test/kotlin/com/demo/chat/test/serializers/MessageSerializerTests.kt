package com.demo.chat.test.serializers

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.test.TestBase
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

class MessageSerializerTests : TestBase() {

    @Test
    fun `Any Message deserialize`() {
        Hooks.onOperatorDebug()
        mapper.apply {
            registerModules(
                JacksonModules(JsonNodeAnyDecoder, JsonNodeAnyDecoder).messageModule()
            )
        }

        val msgUUID = UUID.randomUUID()
        val destUUID = UUID.randomUUID()
        val messageJsons = Flux.just(
                Message.create(MessageKey.create(1L, 2L), "foo", true),
                Message.create(MessageKey.create("a", "b"), "Foo", true),
                Message.create(MessageKey.create(msgUUID, destUUID), 2345, false)
        )
                .map(mapper::writeValueAsString)
                .map<Message<out Any, Any>>(mapper::readValue)

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
                            .isEqualTo(msgUUID)

                    Assertions
                            .assertThat(message.key)
                            .isNotNull
                            .extracting("dest")
                            .isInstanceOf(UUID::class.java)
                            .isEqualTo(destUUID)

                    Assertions
                            .assertThat(message)
                            .isNotNull
                            .extracting("data")
                            .isInstanceOf(Number::class.java)

                }
                .expectComplete()
                .verify(Duration.ofMillis(500))
    }
}