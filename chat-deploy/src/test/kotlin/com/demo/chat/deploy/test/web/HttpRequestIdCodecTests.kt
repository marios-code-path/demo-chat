package com.demo.chat.deploy.test.web

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.deploy.web.ServerJsonCodecConfiguration
import com.demo.chat.controller.webflux.MembershipPersistenceRestController
import com.demo.chat.controller.webflux.MessagePersistenceRestController
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.UUIDKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.security.AuthMetaPersistence
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.key.FakeKeyServices
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Two HTTP routes that decode a raw id request as a JSON body. See
 * `CHAT-oeyxthsy`.
 *
 * The domain module now reads the ids of `MessageSendRequest` and
 * `MembershipRequest` by the `Key` id rule. **The HTTP server codec builds its
 * mapper from the same module beans**, so these routes meet that change. The
 * client is bound to the real controllers through `ServerJsonCodecConfiguration`,
 * so each body decodes as a deployment decodes it.
 *
 * The other composite HTTP routes build their requests from path variables
 * and the principal, so no request body carries them.
 */
class HttpRequestIdCodecTests {

    /** A message store that mints through [registry] and records each add. */
    private class RecordingMessages<T>(private val registry: TestGeneratorKeyService<T>) : MessagePersistence<T, String> {
        val added = CopyOnWriteArrayList<Message<T, String>>()

        override fun key(): Mono<out Key<T>> = registry.key(ChatDomain.MESSAGE)
        override fun add(ent: Message<T, String>): Mono<Void> = Mono.fromRunnable { added.add(ent) }
        override fun rem(key: Key<T>): Mono<Void> = Mono.empty()
        override fun get(key: Key<T>): Mono<out Message<T, String>> = Mono.empty()
        override fun all(): Flux<out Message<T, String>> = Flux.empty()
    }

    /** A membership store that mints through [registry] and records each add. */
    private class RecordingMemberships<T>(private val registry: TestGeneratorKeyService<T>) : MembershipPersistence<T> {
        val added = CopyOnWriteArrayList<TopicMembership<T>>()

        override fun key(): Mono<out Key<T>> = registry.key(ChatDomain.TOPIC_MEMBERSHIP)
        override fun add(ent: TopicMembership<T>): Mono<Void> = Mono.fromRunnable { added.add(ent) }
        override fun rem(key: Key<T>): Mono<Void> = Mono.empty()
        override fun get(key: Key<T>): Mono<out TopicMembership<T>> = Mono.empty()
        override fun all(): Flux<out TopicMembership<T>> = Flux.empty()
    }

    private class Stack<T>(generator: IKeyGenerator<T>, roots: RootKeys<T>, typeUtil: TypeUtil<T>) {
        val registry = TestGeneratorKeyService(generator, roots)
        val messages = RecordingMessages(registry)
        val memberships = RecordingMemberships(registry)
        val user: Key<T> = registry.register(generator.nextId()!!, ChatDomain.USER)
        val room: Key<T> = registry.register(generator.nextId()!!, ChatDomain.MESSAGE_TOPIC)

        private val beans = object : PersistenceServiceBeans<T, String> {
            override fun userPersistence(): UserPersistence<T> = TODO("not used")
            override fun topicPersistence(): TopicPersistence<T> = TODO("not used")
            override fun messagePersistence(): MessagePersistence<T, String> = messages
            override fun membershipPersistence(): MembershipPersistence<T> = memberships
            override fun authMetaPersistence(): AuthMetaPersistence<T> = TODO("not used")
            override fun keyValuePersistence(): KeyValueStore<T, Any> = TODO("not used")
        }

        private val verifier = KeyVerifier(registry, roots)

        val client: WebTestClient = WebTestClient
            .bindToController(
                MessagePersistenceRestController(beans, typeUtil, verifier),
                MembershipPersistenceRestController(beans, typeUtil, verifier),
            )
            .httpMessageCodecs { ServerJsonCodecConfiguration(listOf(ChatJackson3Modules().chatJackson3Module())).configureHttpMessageCodecs(it) }
            .build()
    }

    private fun uuidStack() = Stack(UUIDKeyGenerator(1), FakeKeyServices.uuidRoots(), UUIDUtil())

    private fun longStack() = Stack(TestLongKeyGenerator(), FakeKeyServices.longRoots(), LongUtil())

    private fun <T> addsMessage(s: Stack<T>) {
        s.client.put().uri("/persist/message/add").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(MessageSendRequest("over http", s.user.id, s.room.id)).exchange()
            .expectStatus().isCreated

        val stored = s.messages.added.single()
        assertThat(stored.key.from).isEqualTo(s.user.id)
        assertThat(stored.key.dest).isEqualTo(s.room.id)
        assertThat(stored.data).isEqualTo("over http")
    }

    private fun <T> addsMembership(s: Stack<T>) {
        s.client.put().uri("/persist/membership/add").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(MembershipRequest(s.user.id, s.room.id)).exchange()
            .expectStatus().isCreated

        val stored = s.memberships.added.single()
        assertThat(stored.member).isEqualTo(s.user.id)
        assertThat(stored.memberOf).isEqualTo(s.room.id)
    }

    @Test
    fun `a message add with UUID ids stores the message`() = addsMessage(uuidStack())

    @Test
    fun `a message add with long ids stores the message`() = addsMessage(longStack())

    @Test
    fun `a membership add with UUID ids stores the membership`() = addsMembership(uuidStack())

    @Test
    fun `a membership add with long ids stores the membership`() = addsMembership(longStack())
}
