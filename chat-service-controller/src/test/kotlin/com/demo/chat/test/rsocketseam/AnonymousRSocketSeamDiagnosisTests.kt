package com.demo.chat.test.rsocketseam

import com.demo.chat.config.rsocket.DefaultingAnonymousPayloadInterceptor
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.security.AccessBroker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.rsocket.api.PayloadExchange
import org.springframework.security.rsocket.api.PayloadInterceptorChain
import reactor.core.publisher.Mono

/**
 * The RSocket anonymous seam, read end to end inside one process.
 *
 * `CHAT-ltvfmcvh` names four places that decide the anonymous identity.
 * `DefaultingAnonymousPayloadInterceptor` is the first, and
 * `SpringSecurityAccessBrokerService` is the second. **This class runs one
 * after the other**, which is the order a deployment runs them in.
 *
 * It changes nothing. It states what the pair does today.
 *
 * `AnonymousIdentityDiagnosisTests` in chat-security measures the reader
 * alone. This measures the pair, because the defect is in the seam between
 * them rather than in either one.
 */
class AnonymousRSocketSeamDiagnosisTests {

    /**
     * The interceptor replaces the Spring anonymous token, and the principal
     * of the replacement is a `User`.
     */
    @Test
    fun `the interceptor installs a user principal`() {
        val context = interceptedContext()

        assertThat(context.authentication?.principal).isInstanceOf(User::class.java)
    }

    /**
     * **The finding.** The access broker reads
     * `principal as ChatUserDetails`, and a `User` is not one. So the
     * anonymous caller that the interceptor prepared is refused rather than
     * granted the anonymous identity.
     *
     * A caller who sends no credential at all, and therefore never reaches
     * this interceptor, is granted the anonymous identity instead. The two
     * anonymous paths do not agree.
     */
    @Test
    fun `the access broker denies the identity the interceptor installed`() {
        val broker = RecordingAccessBroker()
        val service = SpringSecurityAccessBrokerService(broker, rootKeys())

        val allowed = service.hasAccessTo(TARGET_KEY, "read")
            .contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(interceptedContext()))
            )
            .block()

        assertThat(allowed).isFalse()
        assertThat(broker.seen).isEmpty()
    }

    /** Runs the interceptor over a Spring anonymous token and answers the result. */
    private fun interceptedContext(): SecurityContext {
        val context = SecurityContextImpl(
            AnonymousAuthenticationToken(
                "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            )
        )

        DefaultingAnonymousPayloadInterceptor(rootKeys())
            .intercept(NoPayloadExchange, PayloadInterceptorChain { Mono.empty() })
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
            .block()

        return context
    }

    private fun rootKeys(): RootKeys<Long> =
        RootKeys<Long>().apply { merge(mapOf("Anon" to ANON_KEY)) }

    /** The interceptor never reads the exchange, so this answers nothing. */
    private object NoPayloadExchange : PayloadExchange {
        override fun getType() = error("The interceptor never reads the exchange type")
        override fun getPayload() = error("The interceptor never reads the payload")
        override fun getDataMimeType() = error("The interceptor never reads the data mime type")
        override fun getMetadataMimeType() =
            error("The interceptor never reads the metadata mime type")
    }

    private class RecordingAccessBroker : AccessBroker<Long> {
        val seen: MutableList<Key<Long>> = mutableListOf()

        override fun hasAccessByPrincipal(
            principal: Mono<Key<Long>>, key: Key<Long>, action: String
        ): Mono<Boolean> = principal.map { seen.add(it); true }

        override fun hasAccessManyByPrincipal(
            principal: Mono<Key<Long>>, targets: List<Key<Long>>, perm: String
        ): Mono<Boolean> = principal.map { seen.add(it); true }

        override fun hasAccessByKey(
            principal: Key<Long>, key: Key<Long>, action: String
        ): Mono<Boolean> = error("This diagnosis never calls hasAccessByKey")

        override fun hasAccessByManyKeys(
            principal: Key<Long>, keys: List<Key<Long>>, perm: String
        ): Mono<Boolean> = error("This diagnosis never calls hasAccessByManyKeys")
    }

    private companion object {
        val ANON_KEY: Key<Long> = Key.funKey(1L)
        val TARGET_KEY: Key<Long> = Key.funKey(3L)
    }
}
