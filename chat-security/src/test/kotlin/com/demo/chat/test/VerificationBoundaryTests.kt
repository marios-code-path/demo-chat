package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
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

    // A store result converts in the domain of its store. See T4 review correction 3.
    @Test
    fun `a store entity whose root is not the store domain never reaches the broker`() {
        val broker = RecordingBroker()
        val user = User.create(Key.of(4105L, roots.of(ChatDomain.MESSAGE).id), "n", "h", "http://u")

        assertThat(service(broker).hasAccessToEntity(user, "GET", ChatDomain.USER).answer()).isFalse()
        assertThat(broker.calls).isEmpty()
    }

    // The key carries the store root, but the store should not hold this type.
    @Test
    fun `a misplaced entity type never reaches the broker`() {
        val broker = RecordingBroker()
        val topic = MessageTopic.create(Key.of(4107L, roots.of(ChatDomain.USER).id), "room")

        assertThat(service(broker).hasAccessToEntity(topic, "GET", ChatDomain.USER).answer()).isFalse()
        assertThat(broker.calls).isEmpty()
    }

    // A store result is trusted and reads no registry, so an unregistered id under the store root passes.
    @Test
    fun `an unregistered store entity under the store root reaches the broker`() {
        val broker = RecordingBroker()
        val user = User.create(Key.of(424255L, roots.of(ChatDomain.USER).id), "n", "h", "http://u")

        assertThat(service(broker).hasAccessToEntity(user, "GET", ChatDomain.USER).answer()).isTrue()
        assertThat(broker.calls).containsExactly(user.key)
    }

    @Test
    fun `a store membership converts under the membership root`() {
        val broker = RecordingBroker()
        val membership = TopicMembership.create(4106L, 1L, 2L)

        assertThat(service(broker).hasAccessToEntity(membership, "GET", ChatDomain.TOPIC_MEMBERSHIP).answer()).isTrue()
        assertThat(broker.calls).containsExactly(Key.of(4106L, roots.of(ChatDomain.TOPIC_MEMBERSHIP).id))
    }

    @Test
    fun `a membership from a user store never reaches the broker`() {
        val broker = RecordingBroker()

        assertThat(service(broker).hasAccessToEntity(TopicMembership.create(4108L, 1L, 2L), "GET", ChatDomain.USER).answer())
            .isFalse()
        assertThat(broker.calls).isEmpty()
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
