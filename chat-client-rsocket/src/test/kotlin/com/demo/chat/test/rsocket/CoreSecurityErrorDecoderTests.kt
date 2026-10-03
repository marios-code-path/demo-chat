package com.demo.chat.test.rsocket

import com.demo.chat.client.rsocket.CoreSecurityErrorDecoder
import com.demo.chat.security.rsocket.CoreAuthenticationRefusal
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoreSecurityErrorDecoderTests {

    @Test
    fun `a typed authentication envelope becomes a core authentication refusal`() {
        val decoded = CoreSecurityErrorDecoder.decode(
            ApplicationErrorException("{\"version\":1,\"kind\":\"AUTHENTICATION\"}")
        )

        assertThat(decoded).isInstanceOf(CoreAuthenticationRefusal::class.java)
    }

    @Test
    fun `a typed authorization envelope becomes a core authorization refusal`() {
        val decoded = CoreSecurityErrorDecoder.decode(
            ApplicationErrorException("{\"version\":1,\"kind\":\"AUTHORIZATION\"}")
        )

        assertThat(decoded).isInstanceOf(CoreAuthorizationRefusal::class.java)
    }

    @Test
    fun `an unknown envelope remains an application error`() {
        val error = ApplicationErrorException("{\"version\":2,\"kind\":\"AUTHENTICATION\"}")

        assertThat(CoreSecurityErrorDecoder.decode(error)).isSameAs(error)
    }
}
