package com.demo.chat.test.controller.webflux

import com.demo.chat.config.WebFluxSecurity
import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentIdentity
import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.demo.chat.config.agent.AgentResourceServerChain
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.test.controller.webflux.config.WebFluxTestSigningKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.WebFilterChainProxy
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

/**
 * What an unauthenticated HTTP request reaches.
 *
 * The application chain requires an agent token. A request without one answers
 * 401 before the identity reader runs.
 *
 * See `docs/IDENTITY-POLICY.md`.
 */
class WebFluxAnonymousIdentityTests {

    private val noOpChain = WebFilterChain { Mono.empty<Void>() }

    @Test
    fun `an unauthenticated request answers 401`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/any/route"))
        WebFilterChainProxy(chain()).filter(exchange, noOpChain).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `an unauthenticated request reaches no identity`() {
        assertThat(identityAtTheEndOfTheChain()).isNull()
    }

    private fun identityAtTheEndOfTheChain(): Key<Long>? {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/any/route"))
        val reached = AtomicReference<Key<Long>?>()

        WebFilterChainProxy(chain()).filter(exchange, readIdentityInto(reached)).block()

        return reached.get()
    }

    private fun chain() = requireNotNull(
        WebFluxSecurity(agentChain()).filterChain(ServerHttpSecurity.http())
    ) { "WebFluxSecurity.filterChain answered no chain" }

    private fun agentChain(): AgentResourceServerChain {
        val properties = AgentSecurityProperties().apply {
            agent = AgentSecurityProperties.Agent().apply {
                clientId = "client-under-test"
                username = "agent-svc"
                requiredScope = "chat.mcp"
            }
            jwt = AgentSecurityProperties.Jwt().apply { jwkPath = WebFluxTestSigningKey.path() }
        }
        val identity = AgentIdentity().apply {
            resolve(
                ChatUserDetails(
                    User.create(TestKeys.key(2L), "agent-svc", "agent-svc", "http://agent-svc"),
                    emptyList(),
                )
            )
        }
        return AgentResourceServerChain(
            properties,
            AgentJwtDecoderFactory.fromJwkFile(WebFluxTestSigningKey.path()),
            AgentAuthenticationConverter(identity, "client-under-test"),
        )
    }

    /** The last filter of the chain, which reads what the chain established. */
    private fun readIdentityInto(reached: AtomicReference<Key<Long>?>) = WebFilterChain {
        ContextIdentity<Long>(rootKeys())
            .identity()
            .doOnNext { key -> reached.set(key) }
            .then(Mono.empty())
    }

    private fun rootKeys(): RootKeys<Long> =
        RootKeysFixture.ofLong(emptyMap(), admin = TestKeys.key(9999L), anon = ANON_KEY)

    private companion object {
        val ANON_KEY: Key<Long> = TestKeys.key(1L)
    }
}
