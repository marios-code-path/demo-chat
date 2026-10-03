package com.demo.chat.test.rsocket

import com.demo.chat.config.agent.AgentAuthenticationToken
import io.rsocket.exceptions.ApplicationErrorException
import io.rsocket.metadata.WellKnownMimeType
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import reactor.test.StepVerifier
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        TestController::class,
    ]
)
class RestBearerRelayContextTests : RSocketTestBase() {

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

    @Test
    fun `request bearer metadata uses the reactive agent token`() {
        val user = User.create(Key.root(1L), "Agent", "agent", "http://agent")
        val principal = ChatUserDetails(user, listOf("ROLE_AGENT"))
        val jwt = Jwt.withTokenValue("relay-token")
            .header("alg", "none")
            .claim("client_id", "client-under-test")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build()
        val token = AgentAuthenticationToken(principal, jwt, emptyList())

        StepVerifier.create(
            metadataRequester.route("auth-type")
                .retrieveMono(String::class.java)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token))
        ).expectNext("TestingAuthenticationToken").verifyComplete()
    }

    @Test
    fun `request without an agent token sends no bearer metadata`() {
        StepVerifier.create(
            metadataRequester.route("auth-type")
                .retrieveMono(String::class.java)
        ).expectNext("UsernamePasswordAuthenticationToken").verifyComplete()
    }
}

@Controller
class TestController() {
    @MessageMapping("echo")
    fun echo(msg: String): String = msg

    @MessageMapping("auth-type")
    fun authenticationType(): reactor.core.publisher.Mono<String> =
        org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
            .map { it.authentication?.javaClass?.simpleName ?: "none" }
}
