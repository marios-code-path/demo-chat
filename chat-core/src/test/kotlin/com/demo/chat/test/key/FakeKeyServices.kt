package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.TestLongKeyGenerator
import java.util.UUID

/**
 * Registries over a complete set of roots, for key service tests. The domain
 * roots take 5000 and up. The identities are users. See `CHAT-avduuqwp`.
 */
object FakeKeyServices {
    fun longRoots(): RootKeys<Long> = RootKeys<Long>().apply {
        loadDomains(ChatDomain.entries.associateWith { Key.root(5000L + it.ordinal) })
        loadIdentities(Key.of(4998L, 5000L + ChatDomain.USER.ordinal), Key.of(4999L, 5000L + ChatDomain.USER.ordinal))
    }

    /** The `UUID` equivalent. The domain roots take the low bits 5000 and up. */
    fun uuidRoots(): RootKeys<UUID> = RootKeys<UUID>().apply {
        loadDomains(ChatDomain.entries.associateWith { Key.root(UUID(0x7007L, 5000L + it.ordinal)) })
        loadIdentities(Key.of(UUID(0x7007L, 4998L), of(ChatDomain.USER).id), Key.of(UUID(0x7007L, 4999L), of(ChatDomain.USER).id))
    }

    fun long(rootKeys: RootKeys<Long>): TestGeneratorKeyService<Long> =
        TestGeneratorKeyService(TestLongKeyGenerator(), rootKeys)
}

/**
 * A verifier over a registry that holds nothing. Every `resolve` and `verify`
 * fails. A test that never checks raw ids passes it to a broker.
 */
object TestVerifiers {
    fun <T> resolvingNothing(): com.demo.chat.service.core.KeyVerifier<T> =
        com.demo.chat.service.core.KeyVerifier(com.demo.chat.service.dummy.DummyKeyService(), RootKeys())

    /**
     * A verifier over a registry that holds [keys], each id under its own root.
     * An id outside [keys] fails. [rootKeys] supplies the domain roots.
     */
    fun <T> holding(rootKeys: RootKeys<T>, keys: Collection<Key<T>>): com.demo.chat.service.core.KeyVerifier<T> =
        com.demo.chat.service.core.KeyVerifier(MapKeyRegistry(keys.associate { it.id to it.root }), rootKeys)

    /**
     * A verifier that holds every id under the fixed [TestRoots] root. A method
     * security test that does not test verification passes it. The boundary
     * tests use [holding] or a real registry instead.
     */
    fun <T> acceptingTestRoot(rootKeys: RootKeys<T>): com.demo.chat.service.core.KeyVerifier<T> =
        com.demo.chat.service.core.KeyVerifier(TestRootRegistry(), rootKeys)
}

/** A registry that answers the fixed test root for every id and mints nothing. */
class TestRootRegistry<T> : com.demo.chat.service.core.IKeyService<T> {
    override fun key(domain: ChatDomain): reactor.core.publisher.Mono<out Key<T>> =
        reactor.core.publisher.Mono.error(IllegalStateException("This registry mints nothing."))
    override fun rem(key: Key<T>): reactor.core.publisher.Mono<Void> = reactor.core.publisher.Mono.empty()
    override fun exists(key: Key<T>): reactor.core.publisher.Mono<Boolean> =
        reactor.core.publisher.Mono.just(key.root == TestRoots.of(key.id))
    @Suppress("UNCHECKED_CAST")
    override fun rootOf(id: T): reactor.core.publisher.Mono<T & Any> =
        reactor.core.publisher.Mono.fromCallable { TestRoots.of(id) as Any } as reactor.core.publisher.Mono<T & Any>
}

/** A registry that answers `rootOf` from a map and mints nothing. */
class MapKeyRegistry<T>(private val roots: Map<T, T>) : com.demo.chat.service.core.IKeyService<T> {
    override fun key(domain: ChatDomain): reactor.core.publisher.Mono<out Key<T>> =
        reactor.core.publisher.Mono.error(IllegalStateException("This registry mints nothing."))
    override fun rem(key: Key<T>): reactor.core.publisher.Mono<Void> = reactor.core.publisher.Mono.empty()
    override fun exists(key: Key<T>): reactor.core.publisher.Mono<Boolean> =
        reactor.core.publisher.Mono.just(roots[key.id] == key.root)
    @Suppress("UNCHECKED_CAST")
    override fun rootOf(id: T): reactor.core.publisher.Mono<T & Any> =
        reactor.core.publisher.Mono.justOrEmpty(roots[id]) as reactor.core.publisher.Mono<T & Any>
}
