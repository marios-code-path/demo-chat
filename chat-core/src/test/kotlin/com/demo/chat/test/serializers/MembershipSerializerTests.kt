package com.demo.chat.test.serializers

import com.demo.chat.domain.Key
import com.demo.chat.domain.Membership
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.MembershipDeserializer
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

class MembershipSerializerTests : TestBase() {

    @Test
    fun `Any Membership deserialize`() {
        mapper.apply {
            registerModules(SimpleModule("CustomDeser", Version.unknownVersion()).apply {
                addDeserializer(Membership::class.java, MembershipDeserializer(JsonNodeAnyCodec))
            })
        }

        val membershipJsons = Flux.just(
                Membership.create(
                        Key.anyKey(1L),
                        Key.anyKey(2L),
                        Key.anyKey(3L)
                ),
                Membership.create(
                        Key.anyKey("A"),
                        Key.anyKey("B"),
                        Key.anyKey("C")
                ),
                Membership.create(
                        Key.anyKey(UUID.randomUUID()),
                        Key.anyKey(UUID.randomUUID()),
                        Key.anyKey(UUID.randomUUID())
                )
        )
                .map(mapper::writeValueAsString).doOnNext(System.out::println)
                .map<Membership<out Any>>(mapper::readValue)

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