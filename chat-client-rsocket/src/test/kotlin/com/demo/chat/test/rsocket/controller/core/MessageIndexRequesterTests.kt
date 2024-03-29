package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.controller.core.IndexServiceController
import com.demo.chat.domain.*
import com.demo.chat.service.core.KeyValueIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessageIndexService.Companion.TOPIC
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketTestBase
import org.assertj.core.api.Assertions
import org.assertj.core.data.Index
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    MessageIndexRequesterTests.MessageIndexTestConfiguration::class
)
open class MessageIndexRequesterTests : RSocketTestBase() {

    @MockBean
    private lateinit var indexService: MessageIndexService<UUID, String, IndexSearchRequest>

    private val message =
        Message.create(MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), "TEST", true)

    @Test
    fun `should query for entities`() {
        BDDMockito
            .given(indexService.findBy(anyObject()))
            .willReturn(Flux.just(message.key))

        StepVerifier
            .create(
                requester
                    .route("query")
                    .data(IndexSearchRequest(TOPIC, UUID.randomUUID().toString(), 100))
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
        class TestMessageIndexController<T, E>(that: MessageIndexService<T, E, IndexSearchRequest>) :
            IndexServiceController<T, Message<T, E>, IndexSearchRequest>(that)
    }
}
