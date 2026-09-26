package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.RootKeyDeletionException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random

class TestStringKeyGen : IKeyGenerator<String> {
    override fun nextId(): String = randomAlphaNumeric(10)
}

class TestUUIDKeyGenerator : IKeyGenerator<UUID> {
    override fun nextId(): UUID = UUID.randomUUID()
}

class TestLongKeyGenerator : IKeyGenerator<Long> {
    private val atom = AtomicLong(abs(Random.nextLong()))

    override fun nextId(): Long = atom.incrementAndGet()
}

/**
 * A key registry for tests. See `CHAT-avduuqwp`.
 *
 * It mints each key under the root of its domain, and it records the root of
 * every id it mints. [rootOf] answers the stored root, the id itself for a root
 * key, and nothing for an unknown id. [rem] refuses a root key.
 *
 * With [rootKeys], the roots are the loaded domain roots. Without it, the
 * service mints one root for each domain from [generator], on first use.
 */
class TestGeneratorKeyService<T>(
    val generator: IKeyGenerator<T>,
    private val rootKeys: RootKeys<T>? = null,
) : IKeyService<T> {
    private val roots = ConcurrentHashMap<ChatDomain, T & Any>()
    private val registry = ConcurrentHashMap<T & Any, T & Any>()

    fun rootIdOf(domain: ChatDomain): T & Any =
        rootKeys?.of(domain)?.id?.let { it!! }
            ?: roots.computeIfAbsent(domain) { generator.nextId()!! }

    override fun key(domain: ChatDomain): Mono<out Key<T>> = Mono.fromCallable {
        val id = generator.nextId()!!
        val root = rootIdOf(domain)
        registry[id] = root
        Key.of<T>(id, root)
    }

    override fun rem(key: Key<T>): Mono<Void> =
        if (isRoot(key.id!!)) Mono.error(RootKeyDeletionException(key.id))
        else Mono.fromRunnable { registry.remove(key.id!!) }

    override fun exists(key: Key<T>): Mono<Boolean> = Mono.just(registry[key.id!!] == key.root || isRoot(key.id!!))

    override fun rootOf(id: T): Mono<T & Any> = Mono.justOrEmpty(
        if (isRoot(id!!)) id else registry[id]
    )

    private fun isRoot(id: T & Any): Boolean =
        (rootKeys?.domains()?.values?.any { it.id == id } ?: false) || roots.containsValue(id)
}

class TestStringKeyService(
    private val keys: TestGeneratorKeyService<String> = TestGeneratorKeyService(TestStringKeyGen()),
) : IKeyService<String> by keys, IKeyGenerator<String> by keys.generator

class TestUUIDKeyService(
    private val keys: TestGeneratorKeyService<UUID> = TestGeneratorKeyService(TestUUIDKeyGenerator()),
) : IKeyService<UUID> by keys, IKeyGenerator<UUID> by keys.generator

class TestLongKeyService(
    private val keys: TestGeneratorKeyService<Long> = TestGeneratorKeyService(TestLongKeyGenerator()),
) : IKeyService<Long> by keys, IKeyGenerator<Long> by keys.generator
