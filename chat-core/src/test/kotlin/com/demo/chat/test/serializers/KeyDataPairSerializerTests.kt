package com.demo.chat.test.serializers

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyDataPair
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

class KeyDataPairSerializerTests : TestBase() {
    // keyData:{"key":{"id":1}
    // This tests that KeyDataPair(Key, Key) will deserialize as KeyDataPair(Key, Key)
    // and not KeyDataPair(Key, Object | String)
    //@Test
    fun `should chain deserialize`() {
        mapper.apply {
            registerModules(
                JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter).keyDataPairModule()
            )
        }

        val pairJsons = Flux.just(
            KeyDataPair.create(Key.funKey(1L), Key.funKey(2L)),
        )
            .map(mapper::writeValueAsString)
            .doOnNext { println(it) }
            .map<KeyDataPair<out Any, Any>>(mapper::readValue)

        StepVerifier
            .create(pairJsons)
            .expectSubscription()
            .assertNext { pair ->
                Assertions
                    .assertThat(pair.data)
                    .isNotNull
                    .isInstanceOf(Key::class.java)
            }
            .expectComplete()
            .verify(Duration.ofMillis(500))
    }

    @Test
    fun `Any Message deserialize`() {
        Hooks.onOperatorDebug()
        mapper.apply {
            registerModules(
                JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter).keyDataPairModule()
            )
        }

        val msgUUID = UUID.randomUUID()
        val destUUID = UUID.randomUUID()
        val pairJsons = Flux.just(
            KeyDataPair.create(MessageKey.create(1L, 2L, 3L), "Foo"),
            KeyDataPair.create(MessageKey.create(10L, 2L, 3L), "Foo"),
            KeyDataPair.create(MessageKey.create(msgUUID, destUUID, destUUID), 2345)
        )
            .map(mapper::writeValueAsString)
            .map<KeyDataPair<out Any, Any>>(mapper::readValue)

        StepVerifier
            .create(pairJsons)
            .expectSubscription()
            .assertNext { pair ->
                Assertions
                    .assertThat(pair.key)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("id", 1L)
                    .extracting("id")
                    .isInstanceOf(Number::class.java)
            }
            .assertNext { pair ->
                Assertions
                    .assertThat(pair.key)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("id", 10L)
                    .extracting("id")
                    .isInstanceOf(Number::class.java)
            }
            .assertNext { pair ->
                Assertions
                    .assertThat(pair.key)
                    .isNotNull
                    .extracting("id")
                    .isInstanceOf(UUID::class.java)
                    .isEqualTo(msgUUID)

                Assertions
                    .assertThat(pair.key)
                    .isNotNull
                    .extracting("dest")
                    .isInstanceOf(UUID::class.java)
                    .isEqualTo(destUUID)

                Assertions
                    .assertThat(pair)
                    .isNotNull
                    .extracting("data")
                    .isInstanceOf(Number::class.java)

            }
            .expectComplete()
            .verify(Duration.ofMillis(500))
    }
}