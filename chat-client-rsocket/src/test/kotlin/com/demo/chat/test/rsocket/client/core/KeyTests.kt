package com.demo.chat.test.rsocket.client.core

import com.demo.chat.client.rsocket.clients.core.KeyClient
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.UUIDKeyServiceBeans
import com.demo.chat.test.rsocket.RSocketTestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        UUIDKeyServiceBeans::class, TestKeyController::class
    ]
)
class KeyTests : RSocketTestBase() {
    @Autowired
    private lateinit var keyBeans: KeyServiceBeans<UUID>

    private val svcPrefix: String = "key."

    @Test
    fun `client should call exists`() {
        val keyService = keyBeans.keyService()

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
        val keyService = keyBeans.keyService()

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
        val keyService = keyBeans.keyService()

        BDDMockito
            .given(keyService.key<Any>(anyObject()))
            .willReturn(Mono.empty())

        val client: IKeyService<UUID> = KeyClient(svcPrefix, requester)

        StepVerifier
            .create(client.key(String::class.java))
            .verifyComplete()
    }
}

@Controller
@MessageMapping("key")
class TestKeyController<T>(keyServices: KeyServiceBeans<T>) : KeyServiceController<T>(keyServices.keyService())