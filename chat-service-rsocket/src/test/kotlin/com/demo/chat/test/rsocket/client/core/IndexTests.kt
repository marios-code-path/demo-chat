package com.demo.chat.test.rsocket.client.core

import com.demo.chat.client.rsocket.core.MessageIndexClient
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.MessageIndexService
import com.demo.chat.test.rsocket.controller.core.MessageIndexRSocketTests
import com.demo.chat.test.rsocket.controller.core.RSocketTestBase
import com.demo.chat.test.rsocket.controller.core.TestConfigurationRSocket
import com.demo.chat.test.rsocket.controller.core.anyObject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestConfigurationRSocket::class,
        MessageIndexRSocketTests.MessageIndexTestConfiguration::class)
class IndexTests : RSocketTestBase() {
    @MockBean
    private lateinit var indexService: MessageIndexService<UUID, String, Map<String, String>>

    private val message = Message.create(MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), "TEST", true)

    @Test
    fun `should query for entities`() {
        BDDMockito
                .given(indexService.findBy(anyObject()))
                .willReturn(Flux.just(message.key))

        val client = MessageIndexClient<UUID, String, Map<String, String>>(requester)

        StepVerifier
                .create(client.findBy(mapOf(Pair(MessageIndexService.TOPIC, UUID.randomUUID().toString()))))
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

        val client = MessageIndexClient<UUID, String, Map<String, String>>(requester)

        StepVerifier
                .create(client.rem(message.key))
                .verifyComplete()
    }

    @Test
    fun `should index an entity`() {
        BDDMockito
                .given(indexService.add(anyObject()))
                .willReturn(Mono.empty())

        val client = MessageIndexClient<UUID, String, Map<String, String>>(requester)

        StepVerifier
                .create(client.add(message))
                .verifyComplete()
    }
}