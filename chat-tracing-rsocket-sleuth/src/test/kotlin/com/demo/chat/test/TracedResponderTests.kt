package com.demo.chat.test

import com.demo.chat.client.rsocket.core.KeyClient
import com.demo.chat.service.IKeyService
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.rsocket.TestConfigurationRSocket
import com.demo.chat.test.rsocket.controller.core.KeyServiceRSocketTests
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    TestConfigurationRSocket::class,
    KeyServiceRSocketTests.KeyPersistenceTestConfiguration::class
)
@TestPropertySource(
    properties = [
        "app.service.core.key=long", "server.port=0", "management.endpoints.enabled-by-default=false",
        "app.client.rsocket.core.persistence", "spring.rsocket.server.port=7890",
        "spring.cloud.service-registry.auto-registration.enabled=false", "app.rsocket.client.requester.factory=test",
        "spring.shell.interactive.enabled=false"]
)
class TracedResponderTests : RSocketTestBase() {

    private val log: Logger = LoggerFactory.getLogger(TracedResponderTests::class.java)

    @MockBean
    private lateinit var keyService: IKeyService<UUID>
    private val svcPrefix: String = "key."


    @Test
    fun `test context loads`() {
        log.info("TEST CONTEXT")
    }

    @Test
    fun `test connection traced in KeyService Call`() {
        BDDMockito
            .given(keyService.key<Any>(anyObject()))
            .willReturn(Mono.empty())

        val client: IKeyService<UUID> = KeyClient(svcPrefix, requester)

        log.info("TEST STEPVERIFIER")

        StepVerifier
            .create(client.key(String::class.java).log())
            .verifyComplete()
    }
}