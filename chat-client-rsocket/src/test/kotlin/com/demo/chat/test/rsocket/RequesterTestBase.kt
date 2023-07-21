package com.demo.chat.test.rsocket

import com.demo.chat.client.rsocket.MetadataRSocketRequester
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.domain.knownkey.Anon
import io.rsocket.metadata.WellKnownMimeType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.rsocket.server.LocalRSocketServerPort
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import java.util.*
import java.util.function.Supplier

@SpringBootTest(classes = [RSocketServerTestConfiguration::class])
open class RequesterTestBase {

    lateinit var requester: RSocketRequester
    lateinit var metadataRequester: MetadataRSocketRequester

    fun requestMetadataProvider(
        username: String = Anon::class.java.simpleName,
        password: String = ""
    ): Supplier<RequestMetadata> = Supplier {
        val metadata = RequestMetadata(
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
        metadataRequester = MetadataRSocketRequester(requester, requestMetadataProvider("user","password"))
    }


}