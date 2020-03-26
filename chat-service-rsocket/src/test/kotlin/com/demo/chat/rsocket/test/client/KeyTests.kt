package com.demo.chat.rsocket.test.client

import com.demo.chat.client.rsocket.KeyClient
import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import com.demo.chat.test.service.KeyServiceRSocketTests
import com.demo.chat.test.service.ServiceTestBase
import com.demo.chat.test.service.TestConfigurationRSocket
import com.demo.chat.test.service.anyObject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestConfigurationRSocket::class,
        KeyServiceRSocketTests.KeyPersistenceTestConfiguration::class)
class KeyTests : ServiceTestBase() {
    @MockBean
    private lateinit var keyService: IKeyService<UUID>

    @Test
    fun `client should call persistence`() {
        BDDMockito
                .given(keyService.exists(anyObject()))
                .willReturn(Mono.just(true))

        val client: IKeyService<UUID> = KeyClient(requestor)

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

        val client: IKeyService<UUID> = KeyClient(requestor)

        StepVerifier
                .create(client.rem(Key.funKey(UUID.randomUUID())))
                .verifyComplete()
    }

    @Test
    fun `Client should create a key`() {
        BDDMockito
                .given(keyService.key<Any>(anyObject()))
                .willReturn(Mono.empty())

        val client: IKeyService<UUID> = KeyClient(requestor)

        StepVerifier
                .create(client.key(String::class.java))
                .verifyComplete()
    }
}