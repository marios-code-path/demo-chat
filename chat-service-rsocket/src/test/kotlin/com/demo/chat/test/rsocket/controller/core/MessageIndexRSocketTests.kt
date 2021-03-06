package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.controller.core.IndexServiceController
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.MessageIndexService.Companion.TOPIC
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestConfigurationRSocket::class,
        MessageIndexRSocketTests.MessageIndexTestConfiguration::class)
class MessageIndexRSocketTests : RSocketTestBase() {
    @MockBean
    private lateinit var indexService: MessageIndexService<UUID, String, Map<String, String>>

    private val message = Message.create(MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), "TEST", true)

    @Test
    fun `should query for entities`() {
        BDDMockito
                .given(indexService.findBy(anyObject()))
                .willReturn(Flux.just(message.key))

        StepVerifier
                .create(
                        requester
                                .route("query")
                                .data(mapOf(
                                        Pair(TOPIC, UUID.randomUUID())
                                ))
                                .retrieveFlux(Key::class.java)
                )
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("id")
                            .hasFieldOrPropertyWithValue("id", message.key.id)
                }
                .verifyComplete()
    }

    @Test
    fun `should remove indexed entity`() {
        BDDMockito
                .given(indexService.rem(anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(
                        requester
                                .route("rem")
                                .data(message.key)
                                .retrieveMono(Void::class.java)
                )
                .verifyComplete()
    }

    @Test
    fun `should index an entity`() {
        BDDMockito
                .given(indexService.add(anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(
                        requester
                                .route("add")
                                .data(message)
                                .retrieveMono(Void::class.java)
                )
                .verifyComplete()
    }

    @TestConfiguration
    class MessageIndexTestConfiguration {
        @Controller
        class TestMessageIndexController<T, E>(that: MessageIndexService<T, E, Map<String, String>>)
            : IndexServiceController<T, Message<T, E>, Map<String, String>>(that)
    }
}