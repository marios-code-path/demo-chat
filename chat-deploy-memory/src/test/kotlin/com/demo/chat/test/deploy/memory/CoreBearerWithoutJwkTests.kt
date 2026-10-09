package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.security.rsocket.RSocketSecurityErrorCodes
import io.rsocket.exceptions.CustomRSocketException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.rsocket.metadata.BearerTokenMetadata
import org.springframework.security.rsocket.metadata.BearerTokenAuthenticationEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import reactor.test.StepVerifier
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-core-bearer-without-jwk",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.command.bus=memory", "app.service.composite.auth=true",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic",
        "app.controller.pubsub", "app.controller.secrets",
        "app.service.security.userdetails",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreBearerWithoutJwkTests {

    @Autowired lateinit var strategies: RSocketStrategies

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    @Test
    fun `a bearer request is refused before anonymous authentication`() {
        val requester = RSocketRequester.builder()
            .rsocketStrategies(
                strategies.mutate().encoders { encoders ->
                    encoders.add(0, BearerTokenAuthenticationEncoder())
                }.build()
            )
            .connectTcp("localhost", port)
            .block(Duration.ofSeconds(10))!!

        StepVerifier.create(
            requester.route("key.rootOf")
                .metadata(
                    BearerTokenMetadata("a.b.c"),
                    MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.v0")
                )
                .data(1L)
                .retrieveMono(Long::class.java)
        ).expectErrorSatisfies { error ->
                assertThat(error).isInstanceOf(CustomRSocketException::class.java)
                assertThat((error as CustomRSocketException).errorCode())
                    .isEqualTo(RSocketSecurityErrorCodes.AUTHENTICATION)
        }.verify(Duration.ofSeconds(10))
    }
}
