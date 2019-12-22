package com.demo.chat.test.serializers

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.serializers.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.KeyDeserializer
import com.demo.chat.domain.serializers.MessageKeyDeserializer
import com.demo.chat.test.TestBase
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.time.Duration
import java.util.*

class KeySerializerTests : TestBase() {
    @Test
    fun `Subclass Key deserialize`() {
        mapper.apply {
            registerModules(SimpleModule("CustomDeser", Version.unknownVersion()).apply {
                addDeserializer(MessageKey::class.java, MessageKeyDeserializer(JsonNodeAnyCodec))
                addDeserializer(Key::class.java, KeyDeserializer(JsonNodeAnyCodec))
            })
        }

        val publisher = TestPublisher.create<Key<out Any>>()
        val keyJsons = publisher.flux()

        val messages = listOf(
                Key.anyKey(1L),
                MessageKey.create("a", "1")
        ).map(mapper::writeValueAsString)

        StepVerifier
                .create(keyJsons)
                .expectSubscription()
                .then {
                    publisher.next(mapper.readValue(messages.first()))
                    publisher.next(mapper.readValue(messages.last()))
                }
                .assertNext { key ->
                    Assertions
                            .assertThat(key)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("id", 1L)
                            .extracting("id")
                            .isInstanceOf(Number::class.java)
                }
                .assertNext { key ->
                    Assertions
                            .assertThat(key)
                            .isNotNull
                            .hasFieldOrProperty("dest")
                            .hasFieldOrPropertyWithValue("id", "a")
                            .extracting("id")
                            .isInstanceOf(String::class.java)
                }
                .then {
                    publisher.complete()
                }
                .expectComplete()
                .verify(Duration.ofMillis(500))

    }

    @Test
    fun `Any Key deserialize`() {
        mapper.apply {
            registerModules(SimpleModule("CustomDeser", Version.unknownVersion()).apply {
                addDeserializer(Key::class.java, KeyDeserializer(JsonNodeAnyCodec))
            })
        }

        val keyJsons = Flux.just(
                Key.anyKey(1L),
                Key.anyKey("a"),
                Key.anyKey(UUID.randomUUID())
        )
                .map(mapper::writeValueAsString)
                .map<Key<out Any>>(mapper::readValue)

        StepVerifier
                .create(keyJsons)
                .expectSubscription()
                .assertNext { key ->
                    Assertions
                            .assertThat(key)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("id", 1L)
                            .extracting("id")
                            .isInstanceOf(Number::class.java)
                }
                .assertNext { key ->
                    Assertions
                            .assertThat(key)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("id", "a")
                            .extracting("id")
                            .isInstanceOf(String::class.java)
                }
                .assertNext { key ->
                    Assertions
                            .assertThat(key)
                            .isNotNull
                            .extracting("id")
                            .isInstanceOf(UUID::class.java)
                }
                .expectComplete()
                .verify(Duration.ofMillis(500))
    }
}