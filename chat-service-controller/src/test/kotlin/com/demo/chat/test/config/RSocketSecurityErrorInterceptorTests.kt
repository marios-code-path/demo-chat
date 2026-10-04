package com.demo.chat.test.config

import com.demo.chat.config.rsocket.RSocketSecurityErrorInterceptor
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.NotFoundException
import com.demo.chat.security.rsocket.RSocketNotFound
import com.demo.chat.security.rsocket.RSocketSecurityErrorCodes
import io.rsocket.exceptions.CustomRSocketException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException

/**
 * The server error codes. See `CHAT-mpjtnpqv` and `CHAT-scoizkpm`.
 *
 * **A miss and a failure must leave with different codes.** A client reads the
 * code and never the text, so one shared code would make a store failure read
 * as a miss.
 */
class RSocketSecurityErrorInterceptorTests {

    private fun codeOf(error: Throwable): Int? =
        (RSocketSecurityErrorInterceptor.toSecurityError(error) as? CustomRSocketException)?.errorCode()

    @Test
    fun `a missing object leaves with the not found code`() {
        val coded = RSocketSecurityErrorInterceptor.toSecurityError(NotFoundException)

        assertThat(codeOf(NotFoundException)).isEqualTo(RSocketNotFound.CODE)
        assertThat(coded).hasMessage("Object not Found")
    }

    @Test
    fun `a key that the registry does not hold leaves with the not found code`() {
        val error = KeyVerificationException("Key 99 is not in the registry.")

        assertThat(codeOf(error)).isEqualTo(RSocketNotFound.CODE)
        assertThat(RSocketSecurityErrorInterceptor.toSecurityError(error)).hasMessage("Key 99 is not in the registry.")
    }

    @Test
    fun `a refusal keeps the authorization code`() {
        assertThat(codeOf(AccessDeniedException("Access Denied"))).isEqualTo(RSocketSecurityErrorCodes.AUTHORIZATION)
    }

    /** **A failure takes no code here.** The transport then reports `0x201`. */
    @Test
    fun `a failure passes unchanged`() {
        val error = IllegalStateException("Query timed out after PT2S")

        assertThat(RSocketSecurityErrorInterceptor.toSecurityError(error)).isSameAs(error)
    }
}
