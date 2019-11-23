package com.demo.chat.test.service

import com.demo.chat.TestEventKey
import com.demo.chat.controller.rsocket.RSocketKeyPersistence
import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.domain.EventKey
import com.demo.chat.service.KeyPersistence
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestConfigurationRSocket::class, KeyPersistenceRSocketTests.KeyPersistenceTestConfiguration::class)
class KeyPersistenceRSocketTests : ServiceTestBase() {

    @Autowired
    lateinit var keyPersistence: KeyPersistence

    @Test
    fun `should get a key`() {
        BDDMockito.given(keyPersistence.key())
                .willReturn(Mono.just(
                        TestEventKey(UUID.randomUUID())
                ))

        StepVerifier
                .create(
                        requestor
                                .route("key")
                                .retrieveMono(TestEventKey::class.java)
                )
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                }
                .verifyComplete()
    }

    @Test
    fun `should save one`() {
        val randomEventKey = TestEventKey(UUID.randomUUID())

        BDDMockito
                .given(keyPersistence.add(anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(
                        requestor
                                .route("add")
                                .data(Mono.just(randomEventKey), EventKey::class.java)
                                .retrieveMono(Void::class.java)
                )
                .verifyComplete()
    }

    @Configuration
    class KeyPersistenceTestConfiguration {
        @Bean
        fun eventKeyModule() = com.demo.chat.module("EVENTKEY", EventKey::class.java, TestEventKey::class.java)

        @MockBean
        lateinit var keyPersistence: KeyPersistence

        @Bean
        fun keyPersistenceRSocket() = RSocketKeyPersistence(keyPersistence)
    }


}
