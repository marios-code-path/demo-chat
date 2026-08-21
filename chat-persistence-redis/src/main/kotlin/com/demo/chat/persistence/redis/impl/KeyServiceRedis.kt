package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono

/**
 * Redis-backed [IKeyService].
 *
 * Key registry: a single Hash (`chat:keys`) mapping `id -> kind.simpleName`.
 * Key ids are generated locally by the injected [IKeyGenerator] (UUID or Long);
 * Redis only stores the kind association.
 */
class KeyServiceRedis<T>(
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val keyGen: IKeyGenerator<T>,
    private val keyRegistryHash: String = "chat:keys",
) : IKeyService<T> {

    override fun <S> key(kind: Class<S>): Mono<out Key<T>> {
        val newId = keyGen.nextId()
        return stringTemplate
            .opsForHash<String, String>()
            .put(keyRegistryHash, newId.toString(), kind.simpleName)
            .map { Key.funKey(newId) }
    }

    override fun rem(key: Key<T>): Mono<Void> =
        stringTemplate
            .opsForHash<String, String>()
            .remove(keyRegistryHash, key.id.toString())
            .then()

    override fun exists(key: Key<T>): Mono<Boolean> =
        stringTemplate
            .opsForHash<String, String>()
            .hasKey(keyRegistryHash, key.id.toString())

    override fun kind(key: Key<T>): Mono<String> =
        stringTemplate
            .opsForHash<String, String>()
            .get(keyRegistryHash, key.id.toString())
            .defaultIfEmpty("none")
}