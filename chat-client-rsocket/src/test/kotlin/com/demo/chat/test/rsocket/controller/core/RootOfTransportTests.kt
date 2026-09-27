package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.client.rsocket.clients.core.KeyClient
import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.test.StepVerifier
import java.util.UUID
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

/**
 * The rootOf route over a real RSocket connection, for a small Long id. See
 * `CHAT-avduuqwp`, T4 review correction 1.
 *
 * The registry is a hash map keyed by the key type, so an Integer or a String
 * misses a Long or UUID entry. A payload decodes by its JSON shape, so the
 * route must convert the id before the lookup.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(LongRootOfTransportTests.LongKeyConfiguration::class)
class LongRootOfTransportTests : RSocketTestBase() {

    @Autowired
    private lateinit var registry: TestGeneratorKeyService<Long>

    @Autowired
    private lateinit var counting: CountingKeys<Long>

    private fun client() = KeyClient<Long>("key.", requester, LongUtil())

    /** The JSON text goes out as written, so the number keeps its exact form. */
    private fun raw(json: String) = requester.route("key.rootOf")
        .data(json)
        .retrieveMono(Long::class.java)

    private fun refusedWithoutRegistryRead(json: String, message: String) {
        registry.register(42L, ChatDomain.MESSAGE)
        val before = counting.rootReads.get()

        StepVerifier.create(raw(json))
            .expectErrorSatisfies { assertThat(it.message).contains(message) }
            .verify()
        assertThat(counting.rootReads.get()).isEqualTo(before)
    }

    // The review probe resolved these two to id 42. They are input errors now.
    @Test
    fun `a fractional id is an input error and reads no registry`() {
        // No quotes in the message: the server decoded a JSON number, not text.
        refusedWithoutRegistryRead("42.9", "The key id 42.9 is not an integer")
    }

    @Test
    fun `an id outside the Long range is an input error and reads no registry`() {
        refusedWithoutRegistryRead("18446744073709551658", "The key id 18446744073709551658 is outside the Long range")
    }

    @Test
    fun `an unsupported shape is an input error and reads no registry`() {
        refusedWithoutRegistryRead("true", "cannot be a Boolean")
        refusedWithoutRegistryRead("{\"id\":42}", "cannot be a LinkedHashMap")
        refusedWithoutRegistryRead("[42]", "cannot be a ArrayList")
    }

    @Test
    fun `an exact id reads the registry once`() {
        val key = registry.register(42L, ChatDomain.MESSAGE)
        val before = counting.rootReads.get()

        StepVerifier.create(raw("42")).expectNext(key.root).verifyComplete()
        assertThat(counting.rootReads.get()).isEqualTo(before + 1)
    }

    @Test
    fun `a minted key resolves to its root`() {
        val minted = client().key(ChatDomain.USER).block()!!

        StepVerifier.create(client().rootOf(minted.id)).expectNext(minted.root).verifyComplete()
    }

    @Test
    fun `a small Long id resolves to its root`() {
        val key = registry.register(42L, ChatDomain.MESSAGE)

        StepVerifier.create(client().rootOf(42L)).expectNext(key.root).verifyComplete()
    }

    @Test
    fun `a root id resolves to itself`() {
        val root = registry.rootIdOf(ChatDomain.USER)

        StepVerifier.create(client().rootOf(root)).expectNext(root).verifyComplete()
    }

    @Test
    fun `an unknown id answers empty`() {
        StepVerifier.create(client().rootOf(424270L)).verifyComplete()
    }

    @TestConfiguration
    class LongKeyConfiguration {
        private val roots = FakeKeyServices.longRoots()

        @Bean
        fun registry(): TestGeneratorKeyService<Long> = FakeKeyServices.long(roots)

        @Bean
        fun testKeyVerifier(registry: TestGeneratorKeyService<Long>): KeyVerifier<Long> = KeyVerifier(registry, roots)

        @Bean
        fun testTypeUtil(): TypeUtil<Long> = LongUtil()

        @Bean
        fun counting(registry: TestGeneratorKeyService<Long>): CountingKeys<Long> = CountingKeys(registry)

        @Controller
        @MessageMapping("key")
        class LongKeyController(counting: CountingKeys<Long>, typeUtil: TypeUtil<Long>) :
            KeyServiceController<Long>(counting, typeUtil)
    }
}

/** The UUID twin. A UUID payload decodes as a String. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(UUIDRootOfTransportTests.UUIDKeyConfiguration::class)
class UUIDRootOfTransportTests : RSocketTestBase() {

    @Autowired
    private lateinit var registry: TestGeneratorKeyService<UUID>

    private fun client() = KeyClient<UUID>("key.", requester, UUIDUtil())

    @Autowired
    private lateinit var counting: CountingKeys<UUID>

    private fun refusedWithoutRegistryRead(json: String, message: String) {
        val before = counting.rootReads.get()

        StepVerifier.create(requester.route("key.rootOf").data(json).retrieveMono(UUID::class.java))
            .expectErrorSatisfies { assertThat(it.message).contains(message) }
            .verify()
        assertThat(counting.rootReads.get()).isEqualTo(before)
    }

    @Test
    fun `a number is an input error and reads no registry`() {
        refusedWithoutRegistryRead("42", "cannot be a Integer")
    }

    @Test
    fun `text that is not a canonical UUID is an input error and reads no registry`() {
        refusedWithoutRegistryRead("\"1-1-1-1-1\"", "is not a canonical UUID")
    }

    @Test
    fun `a minted key resolves to its root`() {
        val minted = client().key(ChatDomain.USER).block()!!

        StepVerifier.create(client().rootOf(minted.id)).expectNext(minted.root).verifyComplete()
    }

    @Test
    fun `a root id resolves to itself`() {
        val root = registry.rootIdOf(ChatDomain.MESSAGE_TOPIC)

        StepVerifier.create(client().rootOf(root)).expectNext(root).verifyComplete()
    }

    @Test
    fun `an unknown id answers empty`() {
        StepVerifier.create(client().rootOf(UUID.randomUUID())).verifyComplete()
    }

    @TestConfiguration
    class UUIDKeyConfiguration {
        private val roots = FakeKeyServices.uuidRoots()

        @Bean
        fun registry(): TestGeneratorKeyService<UUID> =
            TestGeneratorKeyService(com.demo.chat.test.TestUUIDKeyGenerator(), roots)

        @Bean
        fun testKeyVerifier(registry: TestGeneratorKeyService<UUID>): KeyVerifier<UUID> = KeyVerifier(registry, roots)

        @Bean
        fun testTypeUtil(): TypeUtil<UUID> = UUIDUtil()

        @Bean
        fun counting(registry: TestGeneratorKeyService<UUID>): CountingKeys<UUID> = CountingKeys(registry)

        @Controller
        @MessageMapping("key")
        class UUIDKeyController(counting: CountingKeys<UUID>, typeUtil: TypeUtil<UUID>) :
            KeyServiceController<UUID>(counting, typeUtil)
    }
}

/** A registry that counts its root reads, so a test can prove that none ran. */
class CountingKeys<T>(private val delegate: IKeyService<T>) : IKeyService<T> by delegate {
    val rootReads = AtomicInteger()

    override fun rootOf(id: T): Mono<T & Any> = Mono.defer {
        rootReads.incrementAndGet()
        delegate.rootOf(id)
    }
}
