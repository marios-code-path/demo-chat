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

    private fun client() = KeyClient<Long>("key.", requester, LongUtil())

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

        @Controller
        @MessageMapping("key")
        class LongKeyController(registry: TestGeneratorKeyService<Long>, typeUtil: TypeUtil<Long>) :
            KeyServiceController<Long>(registry as IKeyService<Long>, typeUtil)
    }
}

/** The UUID twin. A UUID payload decodes as a String. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(UUIDRootOfTransportTests.UUIDKeyConfiguration::class)
class UUIDRootOfTransportTests : RSocketTestBase() {

    @Autowired
    private lateinit var registry: TestGeneratorKeyService<UUID>

    private fun client() = KeyClient<UUID>("key.", requester, UUIDUtil())

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

        @Controller
        @MessageMapping("key")
        class UUIDKeyController(registry: TestGeneratorKeyService<UUID>, typeUtil: TypeUtil<UUID>) :
            KeyServiceController<UUID>(registry as IKeyService<UUID>, typeUtil)
    }
}
