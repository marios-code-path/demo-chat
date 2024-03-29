package com.demo.chat.test.rsocket.client.core

import com.demo.chat.client.rsocket.clients.core.config.MessageIndexClient
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.rsocket.controller.core.MessageIndexRequesterTests
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        MessageIndexRequesterTests.MessageIndexTestConfiguration::class
    ]
)
open class IndexTests : RSocketTestBase() {
    @MockBean
    private lateinit var indexService: MessageIndexService<UUID, String, IndexSearchRequest>

    private val svcPrefix = ""

    private val message =
        Message.create(MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), "TEST", true)

    @Test
    fun `should query for entities`() {
        BDDMockito
            .given(indexService.findBy(anyObject()))
            .willReturn(Flux.just(message.key))

        val client = MessageIndexClient<UUID, String, IndexSearchRequest>(svcPrefix, requester)

        StepVerifier
            .create(
                client.findBy(
                    IndexSearchRequest(MessageIndexService.TOPIC, UUID.randomUUID().toString(), 100)
                )
            )
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()
                    .hasFieldOrProperty("id")
            }
            .verifyComplete()
    }

    @Test
    fun `should remove indexed entity`() {
        BDDMockito
            .given(indexService.rem(anyObject()))
            .willReturn(Mono.empty())

        val client = MessageIndexClient<UUID, String, IndexSearchRequest>(svcPrefix, requester)

        StepVerifier
            .create(client.rem(message.key))
            .verifyComplete()
    }

    @Test
    fun `should index an entity`() {
        BDDMockito
            .given(indexService.add(anyObject()))
            .willReturn(Mono.empty())

        val client = MessageIndexClient<UUID, String, IndexSearchRequest>(svcPrefix, requester)

        StepVerifier
            .create(client.add(message))
            .verifyComplete()
    }

    @Test
    fun `should find unique`() {
        BDDMockito
            .given(indexService.findUnique(anyObject()))
            .willReturn(Mono.empty())

        val client = MessageIndexClient<UUID, String, IndexSearchRequest>(svcPrefix, requester)

        StepVerifier
            .create(client.findUnique(IndexSearchRequest(MessageIndexService.TOPIC, UUID.randomUUID().toString(), 100)))
            .verifyComplete()
    }
}