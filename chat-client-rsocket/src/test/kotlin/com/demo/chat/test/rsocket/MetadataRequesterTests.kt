package com.demo.chat.test.rsocket

import io.rsocket.exceptions.ApplicationErrorException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.security.rsocket.RSocketSecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.test.StepVerifier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        MetadataRequesterTests.TestControllerConfiguration::class,
        RSocketSecurityTestConfiguration::class,
        RSocketSecurityAutoConfiguration::class,
    ]
)
class MetadataRequesterTests : RSocketTestBase() {

    @Test
    fun `metadata setup request should echo`() {
        val echoRequest = metadataRequester
            .route("echo")
            .data("test123")
            .retrieveMono(String::class.java)

        StepVerifier.create(echoRequest)
            .assertNext {
                assert(it == "test123")
            }
            .verifyComplete()
    }

    @Test
    fun `non-metadata setup fails to authenticate`() {
        val echoRequest = requester
            .route("echo")
            .data("test123")
            .retrieveMono(String::class.java)

        StepVerifier.create(echoRequest)
            .verifyError(ApplicationErrorException::class.java)
    }

    @TestConfiguration
    class TestControllerConfiguration {
        @Controller
        class TestController() {
            @MessageMapping("echo")
            fun echo(msg: String): String = msg
        }

    }
}