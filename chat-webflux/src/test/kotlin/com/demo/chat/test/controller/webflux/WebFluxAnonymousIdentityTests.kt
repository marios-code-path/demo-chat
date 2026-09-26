package com.demo.chat.test.controller.webflux

import com.demo.chat.config.WebFluxSecurity
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.ContextIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.WebFilterChainProxy
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

/**
 * What identity an unauthenticated HTTP request reaches.
 *
 * **This runs the production filter chain.** It builds the bean from
 * `WebFluxSecurity`, sends one request that carries no credential, and reads
 * the identity with `ContextIdentity`, which is what every access check uses.
 *
 * The existing REST tests all install a security context, so none of them
 * reads this path. That is why the regression this test pins reached master.
 *
 * See `docs/IDENTITY-POLICY.md`.
 */
class WebFluxAnonymousIdentityTests {

    /**
     * Reactive Spring Security does not enable anonymous authentication by
     * default. Remove `anonymous` from `WebFluxSecurity.filterChain` and this
     * test reads a null identity, which means every guarded service denies an
     * unauthenticated request.
     */
    @Test
    fun `an unauthenticated request reaches the anon root key`() {
        assertThat(identityOfOneRequest()).isEqualTo(ANON_KEY)
    }

    private fun identityOfOneRequest(): Key<Long>? {
        val chain = requireNotNull(WebFluxSecurity().filterChain(ServerHttpSecurity.http())) {
            "WebFluxSecurity.filterChain answered no chain"
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/any/route"))
        val reached = AtomicReference<Key<Long>?>()

        WebFilterChainProxy(chain)
            .filter(exchange, readIdentityInto(reached))
            .block()

        return reached.get()
    }

    /** The last filter of the chain, which reads what the chain established. */
    private fun readIdentityInto(reached: AtomicReference<Key<Long>?>) = WebFilterChain {
        ContextIdentity(rootKeys())
            .identity()
            .doOnNext { key -> reached.set(key) }
            .then(Mono.empty())
    }

    private fun rootKeys(): RootKeys<Long> =
        RootKeysFixture.ofLong(emptyMap(), admin = Key.funKey(9999L), anon = ANON_KEY)

    private companion object {
        val ANON_KEY: Key<Long> = Key.funKey(1L)
    }
}
