package com.demo.chat.test.rsocket

import io.rsocket.metadata.WellKnownMimeType
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import com.demo.chat.config.ChatJackson3Modules
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.messaging.rsocket.RSocketRequester
import tools.jackson.databind.json.JsonMapper
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
        @Value("\${local.rsocket.server.port}") port: Int,
    ) {
        val mapper = JsonMapper.builder()
            .addModule(ChatJackson3Modules().chatJackson3Module())
            .build()

        requester = builder
            .rsocketStrategies { sb ->
                sb.encoder(SimpleAuthenticationEncoder())
                // Replace rather than append. The default Jackson 3 decoder
                // already matches this type, so a decoder added at the end
                // never runs.
                sb.decoders { it.add(0, JacksonJsonDecoder(mapper)) }
            }
            .tcp("localhost", port)
    }
}