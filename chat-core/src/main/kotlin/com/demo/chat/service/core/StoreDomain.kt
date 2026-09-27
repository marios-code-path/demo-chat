package com.demo.chat.service.core

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import reactor.core.publisher.Mono

/**
 * The domain check of a store write. See `CHAT-avduuqwp`, T5.
 *
 * A typed store holds the entities of one domain. Each `add` refuses a key
 * whose root is not the root of that domain, before any write. The roots load
 * at startup after the stores exist, so each check reads them at write time.
 */
object StoreDomain {
    /** The key root must be the root of [domain]. */
    fun <T> requireKey(key: Key<T>, domain: ChatDomain, rootKeys: RootKeys<T>): Mono<Void> = Mono.defer {
        if (key.root == rootKeys.of(domain).id) Mono.empty()
        else Mono.error(KeyVerificationException("Key ${key.id} is not in ${domain.wireName}."))
    }

    /**
     * A raw id carries no root, so the registry root of [id] must be the root
     * of [domain]. A membership stores its key as a raw id.
     */
    fun <T> requireId(id: T, domain: ChatDomain, keys: IKeyService<T>, rootKeys: RootKeys<T>): Mono<Void> =
        keys.rootOf(id)
            .switchIfEmpty(Mono.error { KeyVerificationException("Key $id is not in the registry.") })
            .flatMap { root ->
                if (root == rootKeys.of(domain).id) Mono.empty<Void>()
                else Mono.error(KeyVerificationException("Key $id is not in ${domain.wireName}."))
            }
}
