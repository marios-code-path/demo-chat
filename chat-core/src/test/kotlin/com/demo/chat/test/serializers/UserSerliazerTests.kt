package com.demo.chat.test.serializers

import com.demo.chat.codec.JsonKeyDecoder
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.UserDeserializer
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

class UserSerliazerTests : TestBase() {

    @Test
    fun `Any User deserialize`() {
        mapper.apply {
            registerModules(SimpleModule("CustomDeser", Version.unknownVersion()).apply {
                addDeserializer(User::class.java, UserDeserializer(JsonKeyDecoder))
            })
        }

        val userJsons = Flux.just(
                User.create(
                        Key.funKey(1L),
                        "MOON", "LUNA", "http://"
                ),
                User.create(
                        Key.funKey(UUID.randomUUID()),
                        "MARS", "WAR", "http://"
                ),
                User.create(
                        Key.funKey("2"),
                        "VENUS", "BEAUTY", "http://"
                )
        )
                .map(mapper::writeValueAsString)
                .map<User<out Any>>(mapper::readValue)

        StepVerifier
                .create(userJsons)
                .expectSubscription()
                .assertNext { topic ->
                    Assertions
                            .assertThat(topic)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("name", "MOON")
                            .hasFieldOrPropertyWithValue("handle", "LUNA")
                            .extracting("key").extracting("id")
                            .isInstanceOf(Number::class.java)
                }
                .assertNext { topic ->
                    Assertions
                            .assertThat(topic)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("name", "MARS")
                            .hasFieldOrPropertyWithValue("handle", "WAR")
                            .extracting("key").extracting("id")
                            .isInstanceOf(UUID::class.java)
                }
                .assertNext { topic ->
                    Assertions
                            .assertThat(topic)
                            .hasFieldOrPropertyWithValue("name", "VENUS")
                            .hasFieldOrPropertyWithValue("handle", "BEAUTY")
                            .extracting("key").extracting("id")
                            .isInstanceOf(String::class.java)
                }
                .expectComplete()
                .verify(Duration.ofMillis(500))
    }
}