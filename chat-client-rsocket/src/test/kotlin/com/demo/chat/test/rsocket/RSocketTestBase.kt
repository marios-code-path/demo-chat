package com.demo.chat.test.rsocket

import com.demo.chat.client.rsocket.MetadataRSocketRequester
import com.demo.chat.client.rsocket.SimpleRequestMetadata
import com.demo.chat.domain.knownkey.Anon
import io.rsocket.metadata.WellKnownMimeType
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.rsocket.server.LocalRSocketServerPort
import org.springframework.messaging.rsocket.RSocketRequester
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
        @LocalRSocketServerPort port: Int,
    ) {
        requester = builder.tcp("localhost", port)

        metadataRequester = MetadataRSocketRequester(requester, requestMetadataProvider(username, password))
    }
}