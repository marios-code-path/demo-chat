package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.KeyRefusalAdvice
import com.demo.chat.security.rsocket.CoreAuthenticationRefusal
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

class RestCoreAuthenticationErrorMappingTests {

    private val advice = KeyRefusalAdvice()

    @Test
    fun `a core authentication refusal maps to 401`() {
        val method = KeyRefusalAdvice::class.java.getMethod(
            "coreAuthenticationRefused",
            CoreAuthenticationRefusal::class.java,
        )

        assertThat(method.getAnnotation(ResponseStatus::class.java).value)
            .isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(advice.coreAuthenticationRefused(CoreAuthenticationRefusal(RuntimeException())))
            .isEqualTo("The core refused authentication.")
    }

    @Test
    fun `a core authorization refusal maps to 403`() {
        val method = KeyRefusalAdvice::class.java.getMethod(
            "coreAuthorizationRefused",
            CoreAuthorizationRefusal::class.java,
        )

        assertThat(method.getAnnotation(ResponseStatus::class.java).value)
            .isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(advice.coreAuthorizationRefused(CoreAuthorizationRefusal(RuntimeException())))
            .isEqualTo("The core refused authorization.")
    }
}
