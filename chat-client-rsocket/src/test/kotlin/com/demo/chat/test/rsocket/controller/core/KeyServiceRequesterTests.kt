package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RequesterTestBase
import com.demo.chat.test.rsocket.TestConfigurationRSocketServer
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
    TestConfigurationRSocketServer::class,
    KeyServiceRequesterTests.KeyControllerTestConfiguration::class
)
class KeyServiceRequesterTests : RequesterTestBase() {

    @MockBean
    private lateinit var keyService: IKeyService<UUID>

    @Test
    fun `Controller should provide existence`() {
        BDDMockito
            .given(keyService.exists(anyObject()))
            .willReturn(Mono.just(true))

        StepVerifier
            .create(
                requester
                    .route("key.exists")
                    .data(Key.funKey(0))
                    .retrieveMono(Boolean::class.java)
            )
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isTrue()
            }
            .verifyComplete()
    }

    @Test
    fun `Controller should remove a key`() {
        BDDMockito
            .given(keyService.rem(anyObject()))
            .willReturn(Mono.empty())

        StepVerifier
            .create(
                requester
                    .route("key.rem")
                    .data(Key.funKey(0))
                    .retrieveMono(Void::class.java)
            )
            .verifyComplete()
    }

    @Test
    fun `Controller Should create Key`() {
        val randomID = UUID.randomUUID()

        BDDMockito
            .given(keyService.key<Any>(anyObject()))
            .willReturn(Mono.just(Key.funKey(randomID)))

        StepVerifier
            .create(
                requester
                    .route("key.key")
                    .data(Any::class.java)
                    .retrieveMono(Key::class.java)
            )
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("id", randomID)
            }
            .verifyComplete()
    }

    @TestConfiguration
    class KeyControllerTestConfiguration {
        @Controller
        @MessageMapping("key")
        class TestKeyController<T>(keyService: IKeyService<T>) : KeyServiceController<T>(keyService)
    }
}