package com.demo.chat.service.conflate

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.TopicPubSubService
import reactor.core.publisher.Mono
import java.util.*

interface PublishConfiguration<T, E, V> {
    fun entityAdd(ent: E): Optional<Message<T, V>>
    fun entityDel(ent: E): Optional<Message<T, V>>

    companion object Factory {
        fun <T, E, V> create(
                adder: (E) -> Optional<Message<T, V>>,
                remy: (E) -> Optional<Message<T, V>>,
        ): PublishConfiguration<T, E, V> = object : PublishConfiguration<T, E, V> {
            override fun entityAdd(ent: E) = adder(ent)
            override fun entityDel(ent: E) = remy(ent)
        }
    }
}

class PubSubbedPersistence<T, E, V>(
    val config: PublishConfiguration<T, E, V>,
    val persistence: PersistenceStore<T, E>,
    val pubsub: TopicPubSubService<T, V>,
) : PersistenceStore<T, E> by persistence {
    override fun add(ent: E): Mono<Void> = persistence.add(ent)
            .flatMap {
                config.entityAdd(ent)
                        .map(pubsub::sendMessage)
                        .orElse(Mono.empty())
            }

    override fun rem(key: Key<T>): Mono<Void> =
            persistence
                    .get(key)
                    .flatMapMany {
                        config.entityDel(it)
                                .map(pubsub::sendMessage)
                                .orElse(Mono.empty())
                    }
                    .then(persistence.rem(key))
}