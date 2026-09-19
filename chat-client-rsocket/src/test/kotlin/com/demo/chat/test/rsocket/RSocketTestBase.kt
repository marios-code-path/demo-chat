package com.demo.chat.test.rsocket

import com.demo.chat.client.rsocket.MetadataRSocketRequester
import com.demo.chat.client.rsocket.SimpleRequestMetadata
import com.demo.chat.domain.knownkey.Anon
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
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import java.util.function.Supplier

@SpringBootTest(classes = [RSocketServerTestConfiguration::class])
@SpringJUnitConfig( initializers = [RSocketPortInfoApplicationContextInitializer::class])
open class RSocketTestBase(var username: String = "user", var password: String = "password") {

    lateinit var requester: RSocketRequester
    // TODO make wsRequester in tests
    // lateinit var wsRequester: RSocketRequester
    lateinit var metadataRequester: MetadataRSocketRequester

    fun requestMetadataProvider(
        username: String = Anon::class.java.simpleName,
        password: String = ""
    ): Supplier<SimpleRequestMetadata> = Supplier {
        val metadata = SimpleRequestMetadata(
            UsernamePasswordMetadata(username, password),
            MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)
        )
        metadata
    }

    @BeforeAll
    internal fun `before all`(
        @Autowired builder: RSocketRequester.Builder,
        @Value("\${local.rsocket.server.port}") port: Int,
    ) {
        // **The client decodes the response, so it needs the domain codec.**
        // Spring Boot 4 decodes with Jackson 3, and the chat domain
        // deserializers are Jackson 2, so a Message answers a type definition
        // error. The decoder is inserted at position 0 rather than appended,
        // because the default Jackson 3 decoder matches the type first and an
        // appended one never runs. See CHAT-qwmjrixq.
        val mapper = JsonMapper.builder()
            .addModule(ChatJackson3Modules().chatJackson3Module())
            .build()

        requester = builder
            .rsocketStrategies { sb -> sb.decoders { it.add(0, JacksonJsonDecoder(mapper)) } }
            .tcp("localhost", port)

        metadataRequester = MetadataRSocketRequester(requester, requestMetadataProvider(username, password))
    }
}