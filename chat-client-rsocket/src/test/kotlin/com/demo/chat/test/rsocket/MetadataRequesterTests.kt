package com.demo.chat.test.rsocket

import io.rsocket.exceptions.ApplicationErrorException
import io.rsocket.metadata.WellKnownMimeType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import reactor.test.StepVerifier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        TestController::class,
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
    fun `incorrect creds setup fails to authenticate`() {
        val echoRequest = requester
            .route("echo")
            .metadata(UsernamePasswordMetadata(username, "nopassword"),
                MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string))
            .data("test123")
            .retrieveMono(String::class.java)

        StepVerifier.create(echoRequest)
            .verifyError(ApplicationErrorException::class.java)
    }
}

@Controller
class TestController() {
    @MessageMapping("echo")
    fun echo(msg: String): String = msg
}