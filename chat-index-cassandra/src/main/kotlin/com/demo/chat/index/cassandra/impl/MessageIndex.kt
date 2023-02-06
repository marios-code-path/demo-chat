package com.demo.chat.index.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.index.cassandra.domain.ChatMessageByTopic
import com.demo.chat.index.cassandra.domain.ChatMessageByTopicKey
import com.demo.chat.index.cassandra.domain.ChatMessageByUser
import com.demo.chat.index.cassandra.domain.ChatMessageByUserKey
import com.demo.chat.index.cassandra.repository.ChatMessageByTopicRepository
import com.demo.chat.index.cassandra.repository.ChatMessageByUserRepository
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessageIndexService.Companion.TOPIC
import com.demo.chat.service.core.MessageIndexService.Companion.USER
import reactor.core.publisher.Flux
import reactor.core.publisher.Flux.empty
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.function.Function

@Suppress("ReactorUnusedPublisher")
class MessageIndex<T>(
    private val stringToKey: Function<String, T>,
    private val byUserRepo: ChatMessageByUserRepository<T>,
    private val byTopicRepo: ChatMessageByTopicRepository<T>,
) : MessageIndexService<T, String, Map<String, String>> {
    override fun add(entity: Message<T, String>): Mono<Void> {
        val instant = Instant.now()
        return Flux.concat(
                byUserRepo.save(
                    ChatMessageByUser(
                        ChatMessageByUserKey(
                                entity.key.id,
                                entity.key.from,
                                entity.key.dest,
                                instant
                        ),
                        entity.data,
                        entity.record
                )
                ),
                byTopicRepo.save(
                    ChatMessageByTopic(
                        ChatMessageByTopicKey(
                                entity.key.id,
                                entity.key.from,
                                entity.key.dest,
                                instant
                        ),
                        entity.data,
                        entity.record
                )
                )
        )
                .then()
    }

    override fun rem(key: Key<T>): Mono<Void> = Flux
            .concat(byUserRepo.deleteByKeyId(key.id),
                    byTopicRepo.deleteByKeyId(key.id))
            .then()

    /* TODO: Suppressed empty() because I dont know when empty() .. so maybe this is overboard */
    override fun findBy(query: Map<String, String>): Flux<out MessageKey<T>> {
        val searchFor = query.keys.first()
        return when (searchFor) {
            TOPIC -> findByTopic(stringToKey.apply(query[searchFor] ?: error("Missing Topic")))
            USER -> findByUser(stringToKey.apply(query[searchFor] ?: error("Missing User")))
            else -> empty()
        }.map {
            it.key
        }
    }

    private fun findByTopic(topic: T) = byTopicRepo.findByKeyDest(topic)
    private fun findByUser(uid: T) = byUserRepo.findByKeyFrom(uid)
    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}