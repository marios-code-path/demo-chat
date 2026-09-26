package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootIds
import com.demo.chat.service.core.RootKeyStore
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono

/**
 * The root key store of the Redis backend. It holds one hash per key type,
 * `chat:rootkeys:<keyType>`, from domain wire name to root id.
 *
 * `HSETNX` writes a root only when the domain has none. So two nodes that start
 * at once use the one root that won. A `long` deployment and a `uuid`
 * deployment on one Redis read separate hashes. `RootIds` parses every stored
 * id strictly, on a read and on the read after a conditional write. See
 * `CHAT-avduuqwp`.
 */
class RootKeyStoreRedis<T : Any>(
    private val template: ReactiveStringRedisTemplate,
    private val typeUtil: TypeUtil<T>,
    keyType: String,
) : RootKeyStore<T> {

    private val hash = "chat:rootkeys:$keyType"

    override fun read(): Mono<Map<ChatDomain, T>> = template.opsForHash<String, String>()
        .entries(hash)
        .collectMap(
            { entry -> ChatDomain.parse(entry.key) ?: throw ChatException("The hash $hash names an unknown domain: ${entry.key}") },
            { entry -> RootIds.parse(typeUtil, entry.value, "$hash ${entry.key}") },
        )

    override fun createIfAbsent(domain: ChatDomain, id: T): Mono<T> = template.opsForHash<String, String>()
        .putIfAbsent(hash, domain.wireName, typeUtil.toString(id))
        .then(template.opsForHash<String, String>().get(hash, domain.wireName))
        .map { RootIds.parse(typeUtil, it, "$hash ${domain.wireName}") }
}
