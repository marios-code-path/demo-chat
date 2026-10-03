package com.demo.chat.test.rsocket

import com.demo.chat.config.rsocket.RSocketAuthenticationManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import reactor.core.publisher.Mono

class RSocketIdentityPrecedenceTests {

    @Test
    fun `valid request metadata takes precedence over setup metadata`() {
        val simple = RecordingManager()
        val bearer = RecordingManager()
        val result = TestingAuthenticationToken("agent", "token")
        bearer.result = result
        val manager = RSocketAuthenticationManager(simple, bearer)

        assertThat(manager.authenticate(BearerTokenAuthenticationToken("token")).block())
            .isSameAs(result)
        assertThat(bearer.calls).hasSize(1)
        assertThat(simple.calls).isEmpty()
    }

    @Test
    fun `missing request metadata leaves setup authentication on the simple manager`() {
        val simple = RecordingManager()
        val bearer = RecordingManager()
        val result = TestingAuthenticationToken("service", "secret")
        simple.result = result
        val manager = RSocketAuthenticationManager(simple, bearer)

        assertThat(
            manager.authenticate(UsernamePasswordAuthenticationToken("service", "secret")).block()
        ).isSameAs(result)
        assertThat(simple.calls).hasSize(1)
        assertThat(bearer.calls).isEmpty()
    }

    @Test
    fun `invalid request metadata does not fall back to setup authentication`() {
        val simple = RecordingManager()
        val bearer = RecordingManager().apply {
            failure = org.springframework.security.authentication.BadCredentialsException("expired")
        }
        val manager = RSocketAuthenticationManager(simple, bearer)

        assertThat(
            org.assertj.core.api.Assertions.catchThrowable {
                manager.authenticate(BearerTokenAuthenticationToken("expired")).block()
            }
        ).isInstanceOf(org.springframework.security.authentication.BadCredentialsException::class.java)
        assertThat(simple.calls).isEmpty()
        assertThat(bearer.calls).hasSize(1)
    }

    private class RecordingManager : ReactiveAuthenticationManager {
        val calls = mutableListOf<org.springframework.security.core.Authentication>()
        var result: org.springframework.security.core.Authentication? = null
        var failure: Throwable? = null

        override fun authenticate(authentication: org.springframework.security.core.Authentication): Mono<org.springframework.security.core.Authentication> {
            calls += authentication
            failure?.let { return Mono.error(it) }
            return Mono.just(result!!)
        }
    }
}
