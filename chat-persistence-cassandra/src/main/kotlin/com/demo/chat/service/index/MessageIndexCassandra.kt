package com.demo.chat.service.index

import com.demo.chat.codec.Codec
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.cassandra.ChatMessageByTopic
import com.demo.chat.domain.cassandra.ChatMessageByTopicKey
import com.demo.chat.domain.cassandra.ChatMessageByUser
import com.demo.chat.domain.cassandra.ChatMessageByUserKey
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageByUserRepository
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.MessageIndexService.Companion.DATA
import com.demo.chat.service.MessageIndexService.Companion.TOPIC
import com.demo.chat.service.MessageIndexService.Companion.USER
import reactor.core.publisher.Flux
import reactor.core.publisher.Flux.empty
import reactor.core.publisher.Mono
import java.time.Instant

class MessageCriteriaCodec<T> : Codec<Message<T, String>, Map<String, String>> {
    override fun decode(record: Message<T, String>): Map<String, String> =
            mapOf(
                    Pair(USER, record.key.from.toString()),
                    Pair(TOPIC, record.key.dest.toString()),
                    Pair(DATA, record.data)
            )
}

@Suppress("ReactorUnusedPublisher")
class MessageIndexCassandra<T>(
        private val criteriaCodec: Codec<Message<T, String>, Map<String, String>>,
        private val byUserRepo: ChatMessageByUserRepository<T>,
        private val byTopicRepo: ChatMessageByTopicRepository<T>) : MessageIndexService<T, String> {
    override fun add(entity: Message<T, String>): Mono<Void> =
            with(criteriaCodec.decode(entity)) {
                val instant = Instant.now()
                Flux.concat(
                        byUserRepo.save(ChatMessageByUser(
                                ChatMessageByUserKey(
                                        entity.key.id,
                                        entity.key.from,
                                        entity.key.dest,
                                        instant
                                ),
                                entity.data,
                                entity.record
                        )),
                        byTopicRepo.save(ChatMessageByTopic(
                                ChatMessageByTopicKey(
                                        entity.key.id,
                                        entity.key.from,
                                        entity.key.dest,
                                        instant
                                ),
                                entity.data,
                                entity.record
                        ))
                )
                        .then()
            }

    override fun rem(key: Key<T>): Mono<Void> = Flux
            .concat(byUserRepo.deleteByKeyId(key.id),
                    byTopicRepo.deleteByKeyId(key.id))
            .then()

    /* TODO: Suppressed empty() because I dont know when empty() .. so maybe this is overboard */
    override fun findBy(query: Map<String, T>): Flux<out MessageKey<T>> {
        val searchFor = query.keys.first()
        return when (searchFor) {
            TOPIC -> findByTopic(query[searchFor] ?: error("Missing Topic"))
            USER -> findByUser(query[searchFor] ?: error("Missing User"))
            else -> empty()
        }.map {
            it.key
        }
    }

    private fun findByTopic(topic: T) = byTopicRepo.findByKeyDest(topic)
    private fun findByUser(uid: T) = byUserRepo.findByKeyFrom(uid)
}