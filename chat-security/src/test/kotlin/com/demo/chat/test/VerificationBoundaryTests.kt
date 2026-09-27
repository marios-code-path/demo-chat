package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Mono

/**
 * The access service verifies each key before the broker sees it. See
 * `CHAT-avduuqwp`, D4 and D5.
 *
 * The broker records each call. A key that fails verification must not reach
 * it, so each refusal asserts zero calls as well as a denial.
 */
class VerificationBoundaryTests {
    private val roots = FakeKeyServices.longRoots()
    private val keys = FakeKeyServices.long(roots)
    private val verifier = KeyVerifier(keys, roots)

    private val caller = keys.register(4100L, ChatDomain.USER)

    /** A broker that allows every call and records its target. */
    private class RecordingBroker : AccessBroker<Long> {
        val calls = mutableListOf<Key<Long>>()

        override fun hasAccessByPrincipal(principal: Mono<Key<Long>>, target: VerifiedKey<Long>, action: String): Mono<Boolean> =
            principal.map { calls.add(target.key); true }

        override fun hasAccessByKey(principal: Key<Long>, target: VerifiedKey<Long>, action: String): Mono<Boolean> =
            Mono.fromCallable { calls.add(target.key); true }

        override fun hasAccessByKeyId(principal: Long, key: Long, action: String): Mono<Boolean> =
            Mono.error(IllegalStateException("not used"))
    }

    private fun service(broker: RecordingBroker) = SpringSecurityAccessBrokerService(broker, roots, verifier)

    private fun authenticated(): SecurityContextImpl = SecurityContextImpl(
        UsernamePasswordAuthenticationToken(
            ChatUserDetails(User.create(caller, "n", "h", "http://u"), listOf()), "secret", listOf()
        )
    )

    private fun Mono<Boolean>.answer(): Boolean =
        contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticated()))).block()!!

    @Test
    fun `a registered key reaches the broker`() {
        val broker = RecordingBroker()
        val room = keys.register(4101L, ChatDomain.MESSAGE_TOPIC)

        assertThat(service(broker).hasAccessTo(room, "GET").answer()).isTrue()
        assertThat(broker.calls).containsExactly(room)
    }

    @Test
    fun `an unknown key never reaches the broker`() {
        val broker = RecordingBroker()

        val answer = service(broker).hasAccessTo(Key.of(424242L, roots.of(ChatDomain.USER).id), "GET").answer()

        assertThat(answer).isFalse()
        assertThat(broker.calls).isEmpty()
    }

    @Test
    fun `a forged root never reaches the broker`() {
        val broker = RecordingBroker()
        val minted = keys.register(4102L, ChatDomain.USER)

        val answer = service(broker).hasAccessTo(Key.of(minted.id, roots.of(ChatDomain.MESSAGE_TOPIC).id), "GET").answer()

        assertThat(answer).isFalse()
        assertThat(broker.calls).isEmpty()
    }

    @Test
    fun `a submitted entity with an unknown key never reaches the broker`() {
        val broker = RecordingBroker()
        val forged = User.create(Key.of(424243L, roots.of(ChatDomain.USER).id), "n", "h", "http://u")

        assertThat(service(broker).hasAccessToSubmittedEntity(forged, "PUT").answer()).isFalse()
        assertThat(broker.calls).isEmpty()
    }

    @Test
    fun `a submitted entity in the wrong domain never reaches the broker`() {
        val broker = RecordingBroker()
        // The id is registered, but in MESSAGE_TOPIC. A User entity must be in USER.
        val room = keys.register(4103L, ChatDomain.MESSAGE_TOPIC)
        val forged = User.create(room, "n", "h", "http://u")

        assertThat(service(broker).hasAccessToSubmittedEntity(forged, "PUT").answer()).isFalse()
        assertThat(broker.calls).isEmpty()
    }

    @Test
    fun `a registered submitted entity reaches the broker`() {
        val broker = RecordingBroker()
        val user = User.create(keys.register(4104L, ChatDomain.USER), "n", "h", "http://u")

        assertThat(service(broker).hasAccessToSubmittedEntity(user, "PUT").answer()).isTrue()
        assertThat(broker.calls).containsExactly(user.key)
    }

    // A store entity converts by type. A key outside the domain of its type denies.
    @Test
    fun `a store entity whose root is not its type domain never reaches the broker`() {
        val broker = RecordingBroker()
        val user = User.create(Key.of(4105L, roots.of(ChatDomain.MESSAGE).id), "n", "h", "http://u")

        assertThat(service(broker).hasAccessToEntity(user, "GET").answer()).isFalse()
        assertThat(broker.calls).isEmpty()
    }

    @Test
    fun `a store membership converts under the membership root`() {
        val broker = RecordingBroker()
        val membership = TopicMembership.create(4106L, 1L, 2L)

        assertThat(service(broker).hasAccessToEntity(membership, "GET").answer()).isTrue()
        assertThat(broker.calls).containsExactly(Key.of(4106L, roots.of(ChatDomain.TOPIC_MEMBERSHIP).id))
    }

    @Test
    fun `an unknown domain name never reaches the broker`() {
        val broker = RecordingBroker()

        assertThat(service(broker).hasAccessToDomain("java.lang.Runtime", "ALL").answer()).isFalse()
        assertThat(broker.calls).isEmpty()
    }

    @Test
    fun `a domain name reaches the broker as its root`() {
        val broker = RecordingBroker()

        assertThat(service(broker).hasAccessToDomain(ChatDomain.MESSAGE_TOPIC.wireName, "ALL").answer()).isTrue()
        assertThat(broker.calls).containsExactly(roots.of(ChatDomain.MESSAGE_TOPIC))
    }
}
