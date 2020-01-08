package com.demo.chat.test.serializers

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.ChatModules
import com.demo.chat.test.TestBase
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

class TopicMembershipSerializerTests : TestBase() {

    @Test
    fun `Any Membership deserialize`() {
        mapper.apply {
            registerModules(ChatModules(JsonNodeAnyCodec, JsonNodeAnyCodec).membershipModule())
        }

        val membershipJsons = Flux.just(
                TopicMembership.create(
                        Key.anyKey(1L),
                        Key.anyKey(2L),
                        Key.anyKey(3L)
                ),
                TopicMembership.create(
                        Key.anyKey("A"),
                        Key.anyKey("B"),
                        Key.anyKey("C")
                ),
                TopicMembership.create(
                        Key.anyKey(UUID.randomUUID()),
                        Key.anyKey(UUID.randomUUID()),
                        Key.anyKey(UUID.randomUUID())
                )
        )
                .map(mapper::writeValueAsString).doOnNext(System.out::println)
                .map<TopicMembership<out Any>>(mapper::readValue)

        StepVerifier
                .create(membershipJsons)
                .expectSubscription()
                .assertNext { membership ->
                    Assertions
                            .assertThat(membership)
                            .isNotNull
                            .hasFieldOrProperty("memberOf")
                            .hasFieldOrProperty("member")
                            .extracting("member", "memberOf", "key")
                            .extracting("id")
                            .contains(1L,2L,3L)

                }
                .assertNext { membership ->
                    Assertions
                            .assertThat(membership)
                            .isNotNull
                            .hasFieldOrProperty("memberOf")
                            .hasFieldOrProperty("member")
                            .extracting("member", "memberOf", "key")
                            .extracting("id")
                            .contains("A","B","C")
                }
                .assertNext { membership ->
                    Assertions
                            .assertThat(membership)
                            .isNotNull
                            .hasFieldOrProperty("memberOf")
                            .hasFieldOrProperty("member")
                            .extracting("member", "memberOf", "key")
                            .extracting("id")
                            .hasOnlyElementsOfType(UUID::class.java)

                }
                .expectComplete()
                .verify(Duration.ofMillis(500))
    }
}