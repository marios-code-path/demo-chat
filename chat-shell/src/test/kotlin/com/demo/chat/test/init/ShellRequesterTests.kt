package com.demo.chat.test.init

import com.demo.chat.client.rsocket.MetadataRSocketRequester
import com.demo.chat.client.rsocket.RSocketRequesterFactory
import com.demo.chat.client.rsocket.RequestMetadata
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import reactor.test.StepVerifier
import java.util.function.Supplier

class ShellRequesterTests : ShellIntegrationTestBase() {

    @Test
    fun `metadataprovider supplies requestmetadata`(@Autowired sup: Supplier<RequestMetadata>) {
        Assertions
            .assertThat(sup)
            .isNotNull

        Assertions
            .assertThat(sup.get())
            .isInstanceOf(RequestMetadata::class.java)
    }

    @Test
    fun `requester sends RequestMetadata`(@Autowired factory: RSocketRequesterFactory) {
        val requester = factory.getClientForService("user")

        Assertions
            .assertThat(requester)
            .isNotNull
            .isInstanceOf(MetadataRSocketRequester::class.java)

        StepVerifier
            .create(requester.route("test").send())
            .verifyComplete()
    }
}