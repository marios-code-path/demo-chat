package com.demo.chat.service.conflate

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.PubSubService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

interface PublishConfiguration {
    fun publishAdd(): Boolean
    fun publishRem(): Boolean
    companion object Factory {
        fun create(add: Boolean, rem: Boolean): PublishConfiguration  = object : PublishConfiguration {
            override fun publishAdd(): Boolean = add

            override fun publishRem(): Boolean  = rem
        }
    }
}

class PubSubbedPersistence<T, E>(
        val persistence: PersistenceStore<T, E>,
        val pubsub: PubSubService<T, E>,
        val config: PublishConfiguration,
        val entityToMessage: Function<E, Message<T, E>>,
) : PersistenceStore<T, E> {
    override fun key(): Mono<out Key<T>> = persistence.key()

    override fun add(ent: E): Mono<Void> = when (config.publishAdd()) {
        true -> persistence
                .add(ent)
                .flatMap { pubsub.sendMessage(entityToMessage.apply(ent)) }
        else -> Mono.empty()
    }

    override fun rem(key: Key<T>): Mono<Void> = when (config.publishRem()) {
        true -> persistence
                .get(key)
                .map(entityToMessage)
                .flatMap(pubsub::sendMessage)
                .flatMap {
                    persistence.rem(key)
                }

        else -> Mono.empty()
    }

    override fun get(key: Key<T>): Mono<out E> = persistence.get(key)

    override fun all(): Flux<out E> = persistence.all()
}