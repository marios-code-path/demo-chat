package com.demo.chat.test.serializers

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.serializers.JacksonModules
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
            registerModules(JacksonModules(JsonNodeAnyDecoder, JsonNodeAnyDecoder).membershipModule())
        }

        val membershipJsons = Flux.just(
                TopicMembership.create(1L, 2L, 3L),
                TopicMembership.create("A", "B", "C"),
                TopicMembership.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        )
                .map(mapper::writeValueAsString)
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
                            .contains(1L, 2L, 3L)

                }
                .assertNext { membership ->
                    Assertions
                            .assertThat(membership)
                            .isNotNull
                            .hasFieldOrProperty("memberOf")
                            .hasFieldOrProperty("member")
                            .extracting("member", "memberOf", "key")
                            .contains("A", "B", "C")
                }
                .assertNext { membership ->
                    Assertions
                            .assertThat(membership)
                            .isNotNull
                            .hasFieldOrProperty("memberOf")
                            .hasFieldOrProperty("member")
                            .extracting("member", "memberOf", "key")
                            .hasOnlyElementsOfType(UUID::class.java)

                }
                .expectComplete()
                .verify(Duration.ofMillis(500))
    }
}