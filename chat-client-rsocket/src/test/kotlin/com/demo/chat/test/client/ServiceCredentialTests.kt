package com.demo.chat.test.client

import com.demo.chat.client.rsocket.ServiceCredential
import com.demo.chat.client.rsocket.SimpleRequestMetadata
import io.rsocket.metadata.WellKnownMimeType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata

/**
 * The service credential that a client sends to the core routes. See
 * `CHAT-rdlghoqe`. `CoreRouteAccessTests` in `chat-deploy-memory` proves that
 * the server accepts this metadata on a request.
 */
class ServiceCredentialTests {

    @Test
    fun `the provider answers the credential as authentication metadata`() {
        val metadata = ServiceCredential.metadataProvider("Service", "secret").get()

        assertThat(metadata).isInstanceOf(SimpleRequestMetadata::class.java)
        val simple = metadata as SimpleRequestMetadata
        assertThat(simple.mimeType.toString()).isEqualTo(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)
        val credential = simple.value as UsernamePasswordMetadata
        assertThat(credential.username).isEqualTo("Service")
        assertThat(credential.password).isEqualTo("secret")
    }

    /** **A blank password fails at startup**, and not at the first core request. */
    @Test
    fun `a blank password is refused`() {
        assertThatThrownBy { ServiceCredential.metadataProvider("Service", " ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("app.client.rsocket.credential.password is blank")
    }

    @Test
    fun `a blank username is refused`() {
        assertThatThrownBy { ServiceCredential.metadataProvider("", "secret") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("app.client.rsocket.credential.username is blank")
    }
}
