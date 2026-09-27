package com.demo.chat.test.resolve

import com.demo.chat.controller.core.IndexSearchRequestIndexServiceController
import com.demo.chat.controller.core.KeyValueStoreController
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.controller.core.TopicPubSubServiceController
import com.demo.chat.controller.core.mapping.SecretsStoreMapping
import com.demo.chat.controller.resolve.KeyDomain
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.MemberTopicRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.key.FakeKeyServices
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.verify
import com.demo.chat.test.anyObject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicInteger

/**
 * Each RSocket route verifies the keys and ids of its input before the store,
 * the index, or pub/sub sees it. See `CHAT-avduuqwp`, D6 and D8 to D12.
 *
 * The routes are plain methods, so the tests call them directly. A refused
 * input must leave the downstream mock with zero calls.
 */
class RSocketRouteVerificationTests {
    private val roots = FakeKeyServices.longRoots()
    private val registry = FakeKeyServices.long(roots)

    /** Counts registry reads, so a test can prove that none ran. */
    private val reads = AtomicInteger()
    private val counting = object : IKeyService<Long> by registry {
        override fun rootOf(id: Long): Mono<Long> = Mono.defer { reads.incrementAndGet(); registry.rootOf(id) }
    }
    private val verifier = KeyVerifier(counting, roots)

    private fun root(domain: ChatDomain) = roots.of(domain).id

    @KeyDomain(ChatDomain.USER)
    class UserController(store: PersistenceStore<Long, User<Long>>, verifier: KeyVerifier<Long>) :
        PersistenceServiceController<Long, User<Long>>(store, verifier)

    @KeyDomain(ChatDomain.MESSAGE)
    class MessageController(store: PersistenceStore<Long, Message<Long, String>>, verifier: KeyVerifier<Long>) :
        PersistenceServiceController<Long, Message<Long, String>>(store, verifier)

    @KeyDomain(ChatDomain.USER)
    class UserIndexController(index: IndexService<Long, User<Long>, IndexSearchRequest>, verifier: KeyVerifier<Long>) :
        IndexSearchRequestIndexServiceController<Long, User<Long>>(index, verifier)

    class SecretsController(store: SecretsStore<Long>, private val verifier: KeyVerifier<Long>) :
        SecretsStoreMapping<Long>, SecretsStore<Long> by store {
        override fun verifier(): KeyVerifier<Long> = verifier
    }

    // D6
    @Test
    fun `a persistence add with a forged root never reaches the store`() {
        val store = mockOf<PersistenceStore<Long, User<Long>>>()
        val user = registry.register(4501L, ChatDomain.USER)

        StepVerifier.create(UserController(store, verifier).addRoute(User.create(Key.of(user.id, root(ChatDomain.MESSAGE)), "n", "h", "u")))
            .verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(store)
    }

    @Test
    fun `a persistence add with a registered key reaches the store`() {
        val store = mockOf<PersistenceStore<Long, User<Long>>>()
        given(store.add(anyObject())).willReturn(Mono.empty())
        val user = User.create(registry.register(4502L, ChatDomain.USER), "n", "h", "u")

        StepVerifier.create(UserController(store, verifier).addRoute(user)).verifyComplete()
        verify(store).add(user)
    }

    // D6 and E8. A message stores a sender and a destination beside its key.
    @Test
    fun `a message add with an unknown sender never reaches the store`() {
        val store = mockOf<PersistenceStore<Long, Message<Long, String>>>()
        val minted = registry.register(4515L, ChatDomain.MESSAGE)
        val room = registry.register(4516L, ChatDomain.MESSAGE_TOPIC)

        StepVerifier.create(
            MessageController(store, verifier)
                .addRoute(Message.create(MessageKey.of(minted.id, minted.root, 424254L, room.id), "m", true))
        ).verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(store)
    }

    // D8
    @Test
    fun `a credential write for an unknown owner never reaches the store`() {
        val store = mockOf<SecretsStore<Long>>()

        StepVerifier.create(SecretsController(store, verifier).addRoute(KeyCredential(Key.of(424247L, root(ChatDomain.USER)), "s")))
            .verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(store)
    }

    @Test
    fun `a credential comparison for an owner outside USER never reaches the store`() {
        val store = mockOf<SecretsStore<Long>>()
        val message = registry.register(4503L, ChatDomain.MESSAGE)

        StepVerifier.create(SecretsController(store, verifier).compareRoute(KeyCredential(message, "s")))
            .verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(store)
    }

    // D9
    @Test
    fun `a bulk read with one unknown key never reaches the store`() {
        val store = mockOf<KeyValueStore<Long, Any>>()
        val known = registry.register(4504L, ChatDomain.KEY_VALUE_PAIR)

        StepVerifier.create(
            KeyValueStoreController(store, verifier)
                .typedByIdsRoute(listOf(known, Key.of(424248L, root(ChatDomain.KEY_VALUE_PAIR))), String::class.java)
        ).verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(store)
    }

    @Test
    fun `an empty bulk read reads no key and no entity`() {
        val store = mockOf<KeyValueStore<Long, Any>>()

        StepVerifier.create(KeyValueStoreController(store, verifier).typedByIdsRoute(listOf(), String::class.java))
            .verifyComplete()
        Mockito.verifyNoInteractions(store)
        org.assertj.core.api.Assertions.assertThat(reads.get()).isZero()
    }

    // D10
    @Test
    fun `a message from an unknown sender never reaches pub sub`() {
        val pubsub = mockOf<TopicPubSubService<Long, String>>()
        val minted = registry.register(4505L, ChatDomain.MESSAGE)
        val room = registry.register(4506L, ChatDomain.MESSAGE_TOPIC)

        StepVerifier.create(
            TopicPubSubServiceController(pubsub, verifier, LongUtil())
                .sendMessageRoute(Message.create(MessageKey.of(minted.id, minted.root, 424249L, room.id), "m", true))
        ).verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(pubsub)
    }

    @Test
    fun `a message whose key is not a MESSAGE key never reaches pub sub`() {
        val pubsub = mockOf<TopicPubSubService<Long, String>>()
        val user = registry.register(4507L, ChatDomain.USER)
        val room = registry.register(4508L, ChatDomain.MESSAGE_TOPIC)

        StepVerifier.create(
            TopicPubSubServiceController(pubsub, verifier, LongUtil())
                .sendMessageRoute(Message.create(MessageKey.of(user.id, user.root, user.id, room.id), "m", true))
        ).verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(pubsub)
    }

    @Test
    fun `a registered message reaches pub sub`() {
        val pubsub = mockOf<TopicPubSubService<Long, String>>()
        given(pubsub.sendMessage(anyObject())).willReturn(Mono.empty())
        val minted = registry.register(4509L, ChatDomain.MESSAGE)
        val user = registry.register(4510L, ChatDomain.USER)
        val room = registry.register(4511L, ChatDomain.MESSAGE_TOPIC)
        val message = Message.create(MessageKey.of(minted.id, minted.root, user.id, room.id), "m", true)

        StepVerifier.create(TopicPubSubServiceController(pubsub, verifier, LongUtil()).sendMessageRoute(message)).verifyComplete()
        verify(pubsub).sendMessage(message)
    }

    // D11
    @Test
    fun `a subscription to a user id never reaches pub sub`() {
        val pubsub = mockOf<TopicPubSubService<Long, String>>()
        val user = registry.register(4512L, ChatDomain.USER)

        StepVerifier.create(
            TopicPubSubServiceController(pubsub, verifier, LongUtil()).subscribeOne(MemberTopicRequest(user.id, user.id))
        ).verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(pubsub)
    }

    @Test
    fun `an open of an unknown topic never reaches pub sub`() {
        val pubsub = mockOf<TopicPubSubService<Long, String>>()

        StepVerifier.create(TopicPubSubServiceController(pubsub, verifier, LongUtil()).openRoute(424250L))
            .verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(pubsub)
    }

    @Test
    fun `a raw id decoded as an Integer resolves as a Long`() {
        val pubsub = mockOf<TopicPubSubService<Long, String>>()
        given(pubsub.open(anyObject())).willReturn(Mono.empty())
        registry.register(4513L, ChatDomain.MESSAGE_TOPIC)

        @Suppress("UNCHECKED_CAST")
        val route = TopicPubSubServiceController(pubsub, verifier, LongUtil()) as TopicPubSubServiceController<Any, String>
        StepVerifier.create(route.openRoute(4513)).verifyComplete()
        verify(pubsub).open(4513L)
    }

    // An id that does not convert exactly is an input error, and no registry read runs.
    @Test
    fun `a fractional topic id never reaches the registry or pub sub`() {
        val pubsub = mockOf<TopicPubSubService<Long, String>>()
        registry.register(42L, ChatDomain.MESSAGE_TOPIC)
        val before = reads.get()

        @Suppress("UNCHECKED_CAST")
        val route = TopicPubSubServiceController(pubsub, verifier, LongUtil()) as TopicPubSubServiceController<Any, String>
        StepVerifier.create(route.openRoute(42.9)).verifyError(com.demo.chat.domain.KeyInputException::class.java)
        Mockito.verifyNoInteractions(pubsub)
        org.assertj.core.api.Assertions.assertThat(reads.get()).isEqualTo(before)
    }

    // D12
    @Test
    fun `an index add with an unknown key never reaches the index`() {
        val index = mockOf<IndexService<Long, User<Long>, IndexSearchRequest>>()

        StepVerifier.create(
            UserIndexController(index, verifier).addRoute(User.create(Key.of(424251L, root(ChatDomain.USER)), "n", "h", "u"))
        ).verifyError(KeyVerificationException::class.java)
        Mockito.verifyNoInteractions(index)
    }

    @Test
    fun `a bulk read of registered keys reaches the store`() {
        val store = mockOf<KeyValueStore<Long, Any>>()
        val known = registry.register(4514L, ChatDomain.KEY_VALUE_PAIR)
        given(store.typedByIds(anyObject(), anyObject<Class<String>>())).willReturn(Flux.empty())

        StepVerifier.create(KeyValueStoreController(store, verifier).typedByIdsRoute(listOf(known), String::class.java))
            .verifyComplete()
        verify(store).typedByIds(listOf(known), String::class.java)
    }
}

private inline fun <reified T : Any> mockOf(): T = Mockito.mock(T::class.java)
