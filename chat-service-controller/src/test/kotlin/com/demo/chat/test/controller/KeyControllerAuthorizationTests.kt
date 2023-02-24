package com.demo.chat.test.controller

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.controller.KeyControllersConfiguration
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.dummy.DummyKeyService
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.TestPropertySource
import reactor.test.StepVerifier


@SpringBootTest(classes = [TestConfig::class, KeyControllersConfiguration::class, KeyControllerAuthorizationTests.CoreKeyServices::class])
@TestPropertySource(properties = ["app.controller.key","spring.rsocket.server.port=11111"])
class KeyControllerAuthorizationTests {

    @Autowired
    private lateinit var requesterBuilder: RSocketRequester.Builder

    @Test
    @WithMockUser("testuser", roles = ["KEY"])
    fun `should KEY authorization get exists`() {

        StepVerifier.create(
            requesterBuilder.tcp("localhost", 11111)
                .route("key.key")
                .data(String::class.java)
                .retrieveMono(Key::class.java)
        ).verifyComplete()

    }

    @Test
    @WithMockUser("testuser", roles = ["NOKEY"])
    fun `should NOKEY authorization be denied exists`() {
        StepVerifier.create(
            requesterBuilder.tcp("localhost", 11111)
                .route("key.key")
                .data(String::class.java)
                .retrieveMono(Key::class.java)
        )
            .consumeErrorWith { err ->
                Assertions.assertThat(err).isInstanceOf(ApplicationErrorException::class.java)
            }
            .verify()
    }


    @TestConfiguration
    class CoreKeyServices() : KeyServiceBeans<Long> {
        @Bean
        override fun keyService(): IKeyService<Long> =
            DummyKeyService()
    }
}