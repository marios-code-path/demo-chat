package com.demo.chat.test.rsocket.client.core

import com.demo.chat.client.rsocket.clients.core.KeyClient
import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.rsocket.TestConfigurationRSocket
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
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    TestConfigurationRSocket::class,
    KeyTests.KeyPersistenceTestConfiguration::class
)
class KeyTests : RSocketTestBase() {
    @MockBean
    private lateinit var keyService: IKeyService<UUID>

    private val svcPrefix: String = "key."

    @Test
    fun `client should call exists`() {
        BDDMockito
            .given(keyService.exists(anyObject()))
            .willReturn(Mono.just(true))

        val client: IKeyService<UUID> = KeyClient(svcPrefix, requester)

        StepVerifier
            .create(client.exists(Key.funKey(UUID.randomUUID())))
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isTrue()
            }
            .verifyComplete()
    }

    @Test
    fun `Client should remove a key`() {
        BDDMockito
            .given(keyService.rem(anyObject()))
            .willReturn(Mono.empty())

        val client: IKeyService<UUID> = KeyClient(svcPrefix, requester)

        StepVerifier
            .create(client.rem(Key.funKey(UUID.randomUUID())))
            .verifyComplete()
    }

    @Test
    fun `Client should create a key`() {
        BDDMockito
            .given(keyService.key<Any>(anyObject()))
            .willReturn(Mono.empty())

        val client: IKeyService<UUID> = KeyClient(svcPrefix, requester)

        StepVerifier
            .create(client.key(String::class.java))
            .verifyComplete()
    }

    @TestConfiguration
    class KeyPersistenceTestConfiguration {
        @Controller
        @MessageMapping("key")
        class TestKeyController<T>(keyService: IKeyService<T>) : KeyServiceController<T>(keyService)
    }
}