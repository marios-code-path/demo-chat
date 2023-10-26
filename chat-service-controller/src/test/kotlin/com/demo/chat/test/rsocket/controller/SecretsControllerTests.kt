package com.demo.chat.test.rsocket.controller

import com.demo.chat.controller.core.access.SecretsStoreAccess
import com.demo.chat.controller.core.mapping.SecretsStoreMapping
import com.demo.chat.domain.Key
import com.demo.chat.secure.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketSecurityTestConfiguration
import com.demo.chat.test.rsocket.RSocketTestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.stereotype.Controller
import org.springframework.test.context.ContextConfiguration
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ContextConfiguration(
    classes = [
        TestSecretStoreController::class,
        RSocketSecurityTestConfiguration::class,
        SpringSecurityAccessBrokerService::class
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecretsControllerTests : RSocketTestBase("user", "password") {

    @MockBean
    private lateinit var secretStore: SecretsStore<Long>

    @MockBean
    private lateinit var accessBroker: AccessBroker<Long>


    @Test
    fun `no permissions, access denied on key fetch`() {
        BDDMockito
            .given(secretStore.getStoredCredentials(anyObject()))
            .willReturn(Mono.just("1234567890"))

        BDDMockito
            .given(accessBroker.getAccessFromPublisher(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))

        StepVerifier.create(
            requester
                .route("get")
                .metadata(UsernamePasswordMetadata("user", "password"), SIMPLE_AUTH)
                .data(Mono.just(Key.funKey(1L)), Key::class.java)

                .retrieveMono(String::class.java)
        ).expectError()
            .verify()
    }
}

@Controller
class TestSecretStoreController<T>(private val that: SecretsStore<T>) : SecretsStoreMapping<T>, SecretsStoreAccess<T>,
    SecretsStore<T> by that {
}