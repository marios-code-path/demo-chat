package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.RootKeyDeletionException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootIds
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono

/**
 * Redis-backed [IKeyService]. See `CHAT-avduuqwp`.
 *
 * Key registry: one hash for each key type, `chat:keys:<keyType>`, from id to
 * root. Key ids are generated locally by the injected [IKeyGenerator]. A root
 * key has no entry. [rootOf] answers the id itself for a root key.
 * `RootIds` parses each stored root strictly.
 */
class KeyServiceRedis<T : Any>(
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val keyGen: IKeyGenerator<T>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
    keyType: String,
) : IKeyService<T> {

    private val keyRegistryHash = "chat:keys:$keyType"

    override fun key(domain: ChatDomain): Mono<out Key<T>> =
        Mono.fromCallable { keyGen.nextId() to rootKeys.of(domain).id }
            .flatMap { (newId, root) ->
                stringTemplate
                    .opsForHash<String, String>()
                    .put(keyRegistryHash, typeUtil.toString(newId), typeUtil.toString(root))
                    .map { Key.of(newId, root) }
            }

    override fun rem(key: Key<T>): Mono<Void> =
        if (rootKeys.domainOfRoot(key.id) != null) Mono.error(RootKeyDeletionException(key.id))
        else stringTemplate
            .opsForHash<String, String>()
            .remove(keyRegistryHash, typeUtil.toString(key.id))
            .then()

    override fun exists(key: Key<T>): Mono<Boolean> = rootOf(key.id).hasElement()

    override fun rootOf(id: T): Mono<T> =
        if (rootKeys.domainOfRoot(id) != null) Mono.just(id)
        else stringTemplate
            .opsForHash<String, String>()
            .get(keyRegistryHash, typeUtil.toString(id))
            .map { RootIds.parse(typeUtil, it, "$keyRegistryHash $id") }
}
