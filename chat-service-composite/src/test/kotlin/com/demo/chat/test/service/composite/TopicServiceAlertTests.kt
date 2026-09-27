package com.demo.chat.test.service.composite

import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import java.util.function.Supplier

/**
 * A join alert and a leave alert are messages. Each takes its own MESSAGE
 * key, and neither reuses the membership id. See `CHAT-avduuqwp`, C49 and C50.
 */
class TopicServiceAlertTests {
    private val keys = FakeKeyServices.long(FAKE_ROOTS)
    private val room = MessageTopic.create(keys.register(70L, ChatDomain.MESSAGE_TOPIC), "general")
    private val userId = keys.register(71L, ChatDomain.USER).id

    private val memberships = mutableListOf<TopicMembership<Long>>()
    private val alerts = FakePubSub()

    private val membershipPersistence = object : MembershipPersistence<Long> {
        private var nextId = 500L
        override fun key(): Mono<out Key<Long>> =
            Mono.fromSupplier { Key.of(nextId++, fakeRoot(ChatDomain.TOPIC_MEMBERSHIP)) }
        override fun add(ent: TopicMembership<Long>): Mono<Void> = Mono.fromRunnable { memberships.add(ent) }
        override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { memberships.removeIf { it.key == key.id } }
        override fun get(key: Key<Long>): Mono<out TopicMembership<Long>> =
            Mono.justOrEmpty(memberships.firstOrNull { it.key == key.id })
        override fun all(): Flux<out TopicMembership<Long>> = Flux.fromIterable(memberships.toList())
    }

    private val membershipIndex = object : MembershipIndexService<Long, Map<String, String>> {
        override fun add(entity: TopicMembership<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Map<String, String>): Flux<out Key<Long>> =
            Flux.defer { Flux.fromIterable(memberships.map { Key.of(it.key, fakeRoot(ChatDomain.TOPIC_MEMBERSHIP)) }) }
        override fun findUnique(query: Map<String, String>): Mono<out Key<Long>> = findBy(query).singleOrEmpty()
        override fun size(query: Map<String, String>): Mono<Long> = Mono.just(memberships.size.toLong())
    }

    private val topicPersistence = object : TopicPersistence<Long> {
        override fun key(): Mono<out Key<Long>> = Mono.error(IllegalStateException("not used"))
        override fun add(ent: MessageTopic<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun get(key: Key<Long>): Mono<out MessageTopic<Long>> =
            Mono.justOrEmpty(room.takeIf { it.key == key })
        override fun all(): Flux<out MessageTopic<Long>> = Flux.just(room)
    }

    private val service = TopicServiceImpl(
        topicPersistence = topicPersistence,
        topicIndex = FakeTopicIndex(),
        pubsub = alerts,
        userPersistence = object : DummyPersistenceStore<Long, User<Long>>(), UserPersistence<Long> {},
        membershipPersistence = membershipPersistence,
        membershipIndex = membershipIndex,
        emptyDataCodec = Supplier { "" },
        topicNameToQuery = Function { req -> mapOf("name" to req.name) },
        memberOfIdToQuery = Function { _ -> mapOf<String, String>() },
        memberWithTopicToQuery = Function { _ -> mapOf<String, String>() },
        messagePersistence = FakeMessagePersistence(),
        verifier = KeyVerifier(keys, FAKE_ROOTS),
        rootKeys = FAKE_ROOTS,
    )

    // addRoom opens the room in production. This test builds the room directly.
    @BeforeEach
    fun openRoom() {
        alerts.open(room.key.id).block()
    }

    @Test
    fun `a join alert has its own message key`() {
        service.joinRoom(MembershipRequest(userId, room.key.id)).block()

        val alert = alerts.sent.single()
        assertThat(alert.key.root).isEqualTo(fakeRoot(ChatDomain.MESSAGE))
        assertThat(memberships.map { it.key }).doesNotContain(alert.key.id)
        assertThat(alert.key.from).isEqualTo(userId)
        assertThat(alert.key.dest).isEqualTo(room.key.id)
    }

    @Test
    fun `a leave alert has its own message key`() {
        service.joinRoom(MembershipRequest(userId, room.key.id)).block()
        val membershipIds = memberships.map { it.key }

        service.leaveRoom(MembershipRequest(userId, room.key.id)).block()

        val alert = alerts.sent.last()
        assertThat(alerts.sent).hasSize(2)
        assertThat(alert.key.root).isEqualTo(fakeRoot(ChatDomain.MESSAGE))
        assertThat(membershipIds).doesNotContain(alert.key.id)
        assertThat(alert.key.id).isNotEqualTo(alerts.sent.first().key.id)
    }

    // D7. A membership never stores a user that the registry does not hold.
    @Test
    fun `a join by an unknown user stores nothing and sends nothing`() {
        StepVerifier.create(service.joinRoom(MembershipRequest(424262L, room.key.id)))
            .verifyError(com.demo.chat.domain.KeyVerificationException::class.java)

        assertThat(memberships).isEmpty()
        assertThat(alerts.sent).isEmpty()
    }

    @Test
    fun `a leave by an unknown user removes nothing and sends nothing`() {
        service.joinRoom(MembershipRequest(userId, room.key.id)).block()

        StepVerifier.create(service.leaveRoom(MembershipRequest(424263L, room.key.id)))
            .verifyError(com.demo.chat.domain.KeyVerificationException::class.java)

        assertThat(memberships).hasSize(1)
        assertThat(alerts.sent).hasSize(1)
    }
}
