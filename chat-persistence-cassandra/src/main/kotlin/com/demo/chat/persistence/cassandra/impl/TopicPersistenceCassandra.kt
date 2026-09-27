package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.service.core.StoreDomain

import com.demo.chat.persistence.cassandra.domain.ChatTopicKey

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.cassandra.domain.ChatTopic
import com.demo.chat.persistence.cassandra.repository.TopicRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.TopicPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Each read maps a row under the root of the MESSAGE_TOPIC domain. See `CHAT-avduuqwp`. */
open class TopicPersistenceCassandra<T : Any>(
    private val keyService: IKeyService<T>,
    private val rootKeys: RootKeys<T>,
    private val roomRepo: TopicRepository<T>
) : TopicPersistence<T> {
    override fun all(): Flux<out MessageTopic<T>> = roomRepo.findAll().map(::topic)

    override fun get(key: Key<T>): Mono<out MessageTopic<T>> = roomRepo.findByKeyId(key.id).map(::topic)

    override fun key(): Mono<out Key<T>> = keyService.key(ChatDomain.MESSAGE_TOPIC)

    /** The key must be in MESSAGE_TOPIC before the write. See `CHAT-avduuqwp`, T5. */
    override fun add(ent: MessageTopic<T>): Mono<Void> =
        StoreDomain.requireKey(ent.key, ChatDomain.MESSAGE_TOPIC, rootKeys).then(Mono.defer { write(ent) })

    private fun write(ent: MessageTopic<T>): Mono<Void> =
        roomRepo
            .add(ChatTopic(ChatTopicKey(ent.key.id), ent.data, true))
            .then()

    override fun rem(key: Key<T>): Mono<Void> =
        roomRepo
            .findByKeyId(key.id)
            .switchIfEmpty(Mono.error(NotFoundException))
            .flatMap { roomRepo.rem(key) }

    private fun topic(row: ChatTopic<T>): MessageTopic<T> =
        MessageTopic.create(Key.of(row.key.id, rootKeys.of(ChatDomain.MESSAGE_TOPIC).id), row.data)
}
