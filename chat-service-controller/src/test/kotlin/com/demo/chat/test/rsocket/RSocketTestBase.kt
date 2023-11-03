package com.demo.chat.test.rsocket

import io.rsocket.metadata.WellKnownMimeType
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.rsocket.server.LocalRSocketServerPort
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils

@SpringBootTest(classes = [RSocketServerTestConfiguration::class])
@SpringJUnitConfig( initializers = [RSocketPortInfoApplicationContextInitializer::class])
open class RSocketTestBase(var username: String = "user", var password: String = "password") {

    val SIMPLE_AUTH = MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)

    lateinit var requester: RSocketRequester

    @BeforeAll
    internal fun `config`(
        @Autowired builder: RSocketRequester.Builder,
        @LocalRSocketServerPort port: Int,
    ) {
        requester = builder
            .rsocketStrategies { sb ->
                sb.encoder(SimpleAuthenticationEncoder())
            }
            .tcp("localhost", port)
    }
}