package com.demo.chat.controller.core.mapping

import com.demo.chat.controller.resolve.Verified
import com.demo.chat.controller.resolve.keyDomainOf
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.VerifiedKey
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The RSocket routes of one store. Each key of the input verifies before the
 * store sees it. The routes call the store methods, and the store methods
 * carry no mapping. See `CHAT-avduuqwp`, D1 and D6.
 *
 * The domain of the store is the `@KeyDomain` of the controller class.
 */
interface PersistenceStoreMapping<T, E : Any> : PersistenceStore<T, E> {
    fun verifier(): KeyVerifier<T>

    @MessageMapping("key")
    override fun key(): Mono<out Key<T>>

    /**
     * The entity key verifies in the store domain, and each id the entity
     * stores resolves in its domain, before the store. D6 and E8.
     */
    @MessageMapping("add")
    fun addRoute(ent: E): Mono<Void> =
        verifier().verifyEntity(ent, keyDomainOf(this::class.java))
            .then(verifier().verifyReferences(ent))
            .then(Mono.defer { add(ent) })

    @MessageMapping("rem")
    fun remRoute(@Verified key: VerifiedKey<T>): Mono<Void> = rem(key.key)

    @MessageMapping("get")
    fun getRoute(@Verified key: VerifiedKey<T>): Mono<out E> = get(key.key)

    @MessageMapping("all")
    override fun all(): Flux<out E>
}

/** The same routes for the key-value store. Each key verifies in KEY_VALUE_PAIR. */
interface KeyValueStoreMapping<T> :
    KeyValueStore<T, Any> {
    fun verifier(): KeyVerifier<T>

    @MessageMapping("key")
    override fun key(): Mono<out Key<T>>

    @MessageMapping("add")
    fun addRoute(ent: KeyValuePair<T, Any>): Mono<Void> =
        verifier().verifyEntity(ent, ChatDomain.KEY_VALUE_PAIR).then(Mono.defer { add(ent) })

    @MessageMapping("rem")
    fun remRoute(@Verified(ChatDomain.KEY_VALUE_PAIR) key: VerifiedKey<T>): Mono<Void> = rem(key.key)

    @MessageMapping("get")
    fun getRoute(@Verified(ChatDomain.KEY_VALUE_PAIR) key: VerifiedKey<T>): Mono<out KeyValuePair<T, Any>> = get(key.key)

    @MessageMapping("all")
    override fun all(): Flux<out KeyValuePair<T, Any>>

    @MessageMapping("typedAll")
    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>>

    /**
     * Every input key verifies in KEY_VALUE_PAIR before the bulk read. One
     * refused key refuses the request. An empty list reads nothing. D9.
     */
    @MessageMapping("typedByIds")
    fun <E> typedByIdsRoute(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        if (ids.isEmpty()) Flux.empty()
        else Flux.fromIterable(ids)
            .concatMap { verifier().verify(it, ChatDomain.KEY_VALUE_PAIR) }
            .map { it.key }
            .collectList()
            .flatMapMany { verified -> typedByIds(verified, typedArgument) }

    @MessageMapping("typedGet")
    fun <E> typedGetRoute(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>> =
        verifier().verify(key, ChatDomain.KEY_VALUE_PAIR).flatMap { typedGet(it.key, typeArgument) }
}
