package com.demo.chat.test.deploy.cassandra

import com.demo.chat.config.deploy.cassandra.CompositeServiceConfiguration
import com.demo.chat.config.service.composite.CompositeServiceBeansConfiguration
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.test.anyObject
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.security.AccessBroker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Mono

/**
 * The identity that the cassandra composite beans reach.
 *
 * `CompositeServiceConfiguration.serviceAccessCompositeServiceAccessBeans` in
 * `chat-deploy-cassandra` supplies `principalKeyPublisher` to every access
 * wrapper. **No test built that bean before 2026-09-23.**
 *
 * This class builds it, drives `topicService().addRoom`, and reads the
 * identity that the wrapper hands to the access broker. It then compares that
 * identity with `ContextIdentity`, which is the canonical resolver.
 *
 * See `docs/IDENTITY-POLICY.md` and `CHAT-qucgqaye`.
 */
class CassandraCompositeIdentityTests {

    @Test
    fun `an anonymous caller reaches the anon root key`() {
        assertThat(identityAt(anonymousContext())).isEqualTo(ANON_KEY)
    }

    @Test
    fun `an authenticated caller reaches its own key`() {
        assertThat(identityAt(authenticatedContext())).isEqualTo(USER_KEY)
    }

    @Test
    fun `an unauthenticated caller reaches no identity`() {
        assertThat(identityAt(unauthenticatedContext())).isNull()
    }

    @Test
    fun `an unsupported principal reaches no identity`() {
        assertThat(identityAt(unsupportedContext())).isNull()
    }

    @Test
    fun `no context reaches no identity`() {
        assertThat(identityAt(null)).isNull()
    }

    /**
     * **The publisher is the canonical resolver, not a copy of its rules.**
     *
     * A copied rule could answer the four states above and still drift later.
     * This compares the two answers for every state in one assertion.
     */
    @Test
    fun `the publisher answers exactly what ContextIdentity answers`() {
        val contexts = listOf(
            null,
            anonymousContext(),
            authenticatedContext(),
            unauthenticatedContext(),
            unsupportedContext()
        )

        val throughTheBeans = contexts.map { identityAt(it) }
        val throughTheResolver = contexts.map { canonicalIdentityAt(it) }

        assertThat(throughTheBeans).isEqualTo(throughTheResolver)
    }

    /** Drives the production beans and answers the identity they reached. */
    private fun identityAt(context: SecurityContext?): Key<Long>? {
        val broker = RecordingAccessBroker()
        val beans = CompositeServiceConfiguration()
            .serviceAccessCompositeServiceAccessBeans(broker, rootKeys(), composedBeans())

        run(beans.topicService().addRoom(ByStringRequest("a-room")).then(), context)

        return broker.reached
    }

    /** Answers what the canonical resolver reaches for the same context. */
    private fun canonicalIdentityAt(context: SecurityContext?): Key<Long>? {
        var reached: Key<Long>? = null
        run(ContextIdentity(rootKeys()).identity().doOnNext { reached = it }.then(), context)
        return reached
    }

    private fun run(call: Mono<Void>, context: SecurityContext?) {
        val withContext = if (context == null) {
            call
        } else {
            call.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
        }
        withContext.block()
    }

    private fun composedBeans(): CompositeServiceBeansConfiguration<Long, String, IndexSearchRequest> {
        val composed: CompositeServiceBeansConfiguration<Long, String, IndexSearchRequest> =
            BDDMockito.mock()
        val topics: TopicServiceImpl<Long, String, IndexSearchRequest> = BDDMockito.mock()

        BDDMockito.given(composed.topicService()).willReturn(topics)
        BDDMockito.given(topics.addRoom(anyObject())).willReturn(Mono.just(ROOM_KEY))

        return composed
    }

    private fun anonymousContext() = SecurityContextImpl(
        AnonymousAuthenticationToken(
            "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        )
    )

    private fun authenticatedContext() =
        SecurityContextImpl(UsernamePasswordAuthenticationToken(chatUserDetails(), "secret", listOf()))

    private fun unauthenticatedContext() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken.unauthenticated(chatUserDetails(), "secret")
    )

    private fun unsupportedContext() =
        SecurityContextImpl(UsernamePasswordAuthenticationToken("a-plain-string", "secret", listOf()))

    private fun chatUserDetails() =
        ChatUserDetails(User.create(USER_KEY, "u", "handle", "http://u"), listOf())

    private fun rootKeys(): RootKeys<Long> = RootKeys<Long>().apply {
        merge(
            mapOf(
                Anon::class.java.simpleName to ANON_KEY,
                MessageTopic::class.java.simpleName to TOPIC_DOMAIN_KEY,
                User::class.java.simpleName to USER_DOMAIN_KEY
            )
        )
    }

    /** Resolves the principal publisher and records the key it carried. */
    private class RecordingAccessBroker : AccessBroker<Long> {
        var reached: Key<Long>? = null

        override fun hasAccessByPrincipal(
            principal: Mono<Key<Long>>, key: Key<Long>, action: String
        ): Mono<Boolean> = principal.map { reached = it; true }

        override fun hasAccessManyByPrincipal(
            principal: Mono<Key<Long>>, targets: List<Key<Long>>, perm: String
        ): Mono<Boolean> = principal.map { reached = it; true }

        override fun hasAccessByKey(
            principal: Key<Long>, key: Key<Long>, action: String
        ): Mono<Boolean> = error("These tests never call hasAccessByKey")

        override fun hasAccessByManyKeys(
            principal: Key<Long>, keys: List<Key<Long>>, perm: String
        ): Mono<Boolean> = error("These tests never call hasAccessByManyKeys")
    }

    private companion object {
        val ANON_KEY: Key<Long> = Key.funKey(1L)
        val USER_KEY: Key<Long> = Key.funKey(2L)
        val ROOM_KEY: Key<Long> = Key.funKey(3L)
        val TOPIC_DOMAIN_KEY: Key<Long> = Key.funKey(4L)
        val USER_DOMAIN_KEY: Key<Long> = Key.funKey(5L)
    }
}
