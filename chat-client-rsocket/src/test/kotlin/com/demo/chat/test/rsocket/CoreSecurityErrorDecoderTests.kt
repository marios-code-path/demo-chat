package com.demo.chat.test.rsocket

import com.demo.chat.client.rsocket.CoreSecurityErrorDecoder
import com.demo.chat.security.rsocket.CoreAuthenticationRefusal
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import com.demo.chat.security.rsocket.RSocketSecurityErrorCodes
import io.rsocket.exceptions.ApplicationErrorException
import io.rsocket.exceptions.CustomRSocketException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoreSecurityErrorDecoderTests {

    @Test
    fun `the authentication code becomes a core authentication refusal`() {
        val decoded = CoreSecurityErrorDecoder.decode(
            CustomRSocketException(RSocketSecurityErrorCodes.AUTHENTICATION, "Invalid Credentials")
        )

        assertThat(decoded).isInstanceOf(CoreAuthenticationRefusal::class.java)
            .hasMessage("Invalid Credentials")
    }

    @Test
    fun `the authorization code becomes a core authorization refusal`() {
        val decoded = CoreSecurityErrorDecoder.decode(
            CustomRSocketException(RSocketSecurityErrorCodes.AUTHORIZATION, "Access Denied")
        )

        assertThat(decoded).isInstanceOf(CoreAuthorizationRefusal::class.java)
            .hasMessage("Access Denied")
    }

    @Test
    fun `another custom code passes unchanged`() {
        val error = CustomRSocketException(0x00000777, "Access Denied")

        assertThat(CoreSecurityErrorDecoder.decode(error)).isSameAs(error)
    }

    /** The decoder reads the code. The same text on a plain application error is not a refusal. */
    @Test
    fun `a plain application error with the refusal text passes unchanged`() {
        val error = ApplicationErrorException("Access Denied")

        assertThat(CoreSecurityErrorDecoder.decode(error)).isSameAs(error)
    }
}
