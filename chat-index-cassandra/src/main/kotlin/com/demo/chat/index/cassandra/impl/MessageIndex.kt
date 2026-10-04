package com.demo.chat.index.cassandra.impl

import com.demo.chat.domain.SimpleMessageKey

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.index.cassandra.domain.ChatMessageByTopic
import com.demo.chat.index.cassandra.domain.ChatMessageByTopicKey
import com.demo.chat.index.cassandra.domain.ChatMessageByUser
import com.demo.chat.index.cassandra.domain.ChatMessageByUserKey
import com.demo.chat.index.cassandra.domain.ChatMessageIndexById
import com.demo.chat.index.cassandra.repository.ChatMessageByTopicRepository
import com.demo.chat.index.cassandra.repository.ChatMessageByUserRepository
import com.demo.chat.index.cassandra.repository.ChatMessageIndexByIdRepository
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessageIndexService.Companion.TOPIC
import com.demo.chat.service.core.MessageIndexService.Companion.USER
import reactor.core.publisher.Flux
import reactor.core.publisher.Flux.empty
import reactor.core.publisher.Mono
import java.util.function.Function

@Suppress("ReactorUnusedPublisher")
class MessageIndex<T : Any>(
    private val stringToKey: Function<String, T>,
    private val byUserRepo: ChatMessageByUserRepository<T>,
    private val byTopicRepo: ChatMessageByTopicRepository<T>,
    private val byIdRepo: ChatMessageIndexByIdRepository<T>,
    private val rootKeys: RootKeys<T>,
) : MessageIndexService<T, String, Map<String, String>> {
    /**
     * Each row keeps the time of the message. A row that took the time of the
     * write gave a different time on every read path. See `CHAT-xcmpudyb`.
     */
    override fun add(entity: Message<T, String>): Mono<Void> {
        val instant = entity.key.timestamp
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
                ),
                byIdRepo.save(ChatMessageIndexById(entity.key.id, entity.key.from, entity.key.dest, instant))
        )
                .then()
    }

    /**
     * Reads the coordinates of the message, then deletes each row by its full
     * primary key. The coordinate row goes last, so a failed delete can run
     * again. A message that the index does not hold completes with nothing to
     * delete. See `CHAT-xcmpudyb`.
     */
    override fun rem(key: Key<T>): Mono<Void> = byIdRepo
            .findById(key.id)
            .flatMap { row ->
                Flux.concat(
                        byUserRepo.rem(row),
                        byTopicRepo.rem(row),
                        byIdRepo.delete(row)
                ).then()
            }

    /* TODO: Suppressed empty() because I dont know when empty() .. so maybe this is overboard */
    override fun findBy(query: Map<String, String>): Flux<out MessageKey<T>> {
        val searchFor = query.keys.first()
        return when (searchFor) {
            TOPIC -> findByTopic(stringToKey.apply(query[searchFor] ?: error("Missing Topic")))
                .map { SimpleMessageKey(it.key.id, root(), it.key.from, it.key.dest, it.key.timestamp) }
            USER -> findByUser(stringToKey.apply(query[searchFor] ?: error("Missing User")))
                .map { SimpleMessageKey(it.key.id, root(), it.key.from, it.key.dest, it.key.timestamp) }
            else -> empty()
        }
    }

    private fun root(): T = rootKeys.of(ChatDomain.MESSAGE).id

    private fun findByTopic(topic: T) = byTopicRepo.findByKeyDest(topic)
    private fun findByUser(uid: T) = byUserRepo.findByKeyFrom(uid)
    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}